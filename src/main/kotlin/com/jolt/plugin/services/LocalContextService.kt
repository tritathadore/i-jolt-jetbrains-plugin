package com.jolt.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jolt.plugin.core.LocalContextData
import java.util.LinkedHashSet

@Service(Service.Level.PROJECT)
class LocalContextService(private val project: Project) {
  private val maxHistory = 10
  private val tabHistory = LinkedHashSet<String>()

  init {
    project.messageBus.connect().subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          addToHistory(file.path)
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          tabHistory.remove(file.path)
        }

        override fun selectionChanged(event: FileEditorManagerEvent) {
          event.newFile?.let { addToHistory(it.path) }
        }
      }
    )

    FileEditorManager.getInstance(project).openFiles.forEach { file ->
      addToHistory(file.path)
    }
  }

  private fun addToHistory(path: String) {
    tabHistory.remove(path)
    tabHistory.add(path)

    while (tabHistory.size > maxHistory) {
      tabHistory.iterator().next().let { tabHistory.remove(it) }
    }
  }

  fun getLocalContextData(): LocalContextData {
    val selectedEditor = FileEditorManager.getInstance(this.project).selectedEditor
    val activeTab = selectedEditor?.file?.path
    val filteredRecentTabs = tabHistory.toList().reversed().filter { it != activeTab }
    return LocalContextData(
      activeTab = activeTab,
      openTabs = filteredRecentTabs,
    )
  }

  companion object {
    fun getInstance(project: Project): LocalContextService =
      project.getService(LocalContextService::class.java)
  }
}
