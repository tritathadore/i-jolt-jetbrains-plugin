package com.jolt.plugin.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.jolt.plugin.services.DaemonService
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.project.ProjectManager
import io.sentry.Sentry

/**
 * Additional lifecycle listeners to dispose a project and terminate the daemon
 * In most cases they are likely overlap but each catch side cases such as IDE or
 * plugin updates, project restarts due to JVM reindex updates, etc.
 */

val disposeProject: (project: Project?) -> Unit = { project ->
  try {
    project?.getService(DaemonService::class.java)?.dispose()
  } catch (e: Exception) {
    Sentry.captureException(e)
  }
}

class JoltProjectManagerListener : ProjectManagerListener {
  override fun projectClosing(project: Project) {
    disposeProject(project)
  }
}

class JoltApplicationLifecycleListener : AppLifecycleListener {
  override fun appWillBeClosed(isRestart: Boolean) {
    ProjectManager.getInstance().openProjects.forEach(disposeProject)
  }
}
