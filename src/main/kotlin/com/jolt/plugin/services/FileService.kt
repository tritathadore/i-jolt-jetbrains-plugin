package com.jolt.plugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.jolt.plugin.core.ChangedFile
import com.jolt.plugin.core.FileOpType
import io.sentry.Sentry
import org.cef.callback.CefQueryCallback
import java.io.File


object FileService {
  private fun upsertFile(project: Project, file: File, content: String?) {
    val parentDir = file.parentFile
    // Create any parent directories that are not there
    if (!parentDir.exists()) {
      parentDir.mkdirs()
    }
    val localFileSystem = LocalFileSystem.getInstance()
    val virtualParentDir = localFileSystem.refreshAndFindFileByIoFile(parentDir) ?: run {
      val exception = IllegalStateException("Can't find dir ${parentDir.absolutePath} when applying code")
      Sentry.captureException(exception)
      throw exception
    }


    // Attempt to see if the file already exists as they may be reapplying a "create" op
    // If it does not exist, create the file
    if (localFileSystem.refreshAndFindFileByIoFile(file) == null) {
      localFileSystem.createChildFile(this, virtualParentDir, file.name)
    }

    // Refresh the parent directory and new file into the virtual file system
    localFileSystem.refreshIoFiles(listOf(parentDir, file))
    val virtualFile = localFileSystem.refreshAndFindFileByIoFile(file) ?: run {
      val exception = IllegalStateException("Can't find file ${file.absolutePath} when applying code")
      Sentry.captureException(exception)
      throw exception
    }

    // Create an undoable action for updating the content
    WriteCommandAction.runWriteCommandAction(project, "changes to ${file.name}", null, {
      val document = FileDocumentManager.getInstance().getDocument(virtualFile)
      document?.setText(content ?: "")
    }, null)

    FileEditorManager.getInstance(project).openFile(virtualFile, true)
  }

  private fun deleteFile(project: Project, file: File) {
    val localFileSystem = LocalFileSystem.getInstance()
    val virtualFile = localFileSystem.refreshAndFindFileByIoFile(file) ?:
      return

    WriteCommandAction.runWriteCommandAction(project, "deleting ${file.name}", null, {
      virtualFile.delete(this)
    }, null)
  }

  private fun moveFile(project: Project, file: File, content: String?) {
    // Moves are just split into deletes and upserts so they can be re-applied
    // null content means this move operation is the old filepath so delete it
    if (content != null) {
      this.upsertFile(project, file, content)
    } else {
      this.deleteFile(project, file)
    }
  }

  fun openFile(project: Project, path: String, callback: CefQueryCallback?) {
    val absolutePath = "${project.basePath}/${path}"
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(absolutePath))
      ?: run {
        callback?.failure(400, "File not found: $absolutePath")
        return
      }

    ApplicationManager.getApplication().invokeLater {
      FileEditorManager.getInstance(project).openFile(virtualFile, true)
      VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
      callback?.success("""{"success": true}""")
    }
  }

  fun applyFileUpdate(project: Project, changedFile: ChangedFile) {
    val absolutePath = "${project.basePath}/${changedFile.filepath}"
    val file = File(absolutePath)

    ApplicationManager.getApplication().invokeLater {
      try {
        when(changedFile.operation) {
          FileOpType.Create, FileOpType.Update -> {
            this.upsertFile(
              file = file,
              project = project,
              content = changedFile.content
            )
          }
          FileOpType.Delete -> this.deleteFile(
            file = file,
            project = project,
          )
          FileOpType.Move -> this.moveFile(
            file = file,
            project = project,
            content = changedFile.content
          )
        }
      } catch (e: Exception) {
        Sentry.captureException(e)
      }
    }
  }
}
