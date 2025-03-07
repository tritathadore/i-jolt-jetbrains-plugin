package com.jolt.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ProjectService(project: Project) {
  init {
    instance = this
    currentProject = project
  }

  companion object {
    private var instance: ProjectService? = null
    private var currentProject: Project? = null

    fun getProject(): Project? = currentProject
  }
}
