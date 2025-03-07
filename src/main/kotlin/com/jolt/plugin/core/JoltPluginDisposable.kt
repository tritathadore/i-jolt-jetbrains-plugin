package com.jolt.plugin.core
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

@Service(Service.Level.APP, Service.Level.PROJECT)
class JoltPluginDisposable : Disposable {
  companion object {
    @get:NotNull
    val instance: Disposable
      get() = ApplicationManager.getApplication().getService(JoltPluginDisposable::class.java)

    @NotNull
    fun getInstance(@NotNull project: Project): Disposable {
      return project.getService(JoltPluginDisposable::class.java)
    }
  }

  override fun dispose() {}
}
