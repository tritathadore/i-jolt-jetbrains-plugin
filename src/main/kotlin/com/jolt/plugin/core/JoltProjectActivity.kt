package com.jolt.plugin.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jolt.plugin.services.DaemonService
import com.jolt.plugin.services.LocalContextService
import com.jolt.plugin.services.ProjectService
import io.sentry.Sentry
import io.github.cdimascio.dotenv.dotenv
import io.sentry.SentryEvent
import io.sentry.SentryOptions

class JoltProjectActivity: ProjectActivity {
  override suspend fun execute(project: Project) {
    val projectPath = project.basePath ?: return

    setUpSentry()
    project.getService(ProjectService::class.java)
    LocalContextService.getInstance(project)
    DaemonService.getInstance(project).startDaemon(projectPath)
  }

  private fun setUpSentry() {
    val dotenv = dotenv()
    val sentryDsn: String? = dotenv["JB_SENTRY_DSN"]
    val daemonEnv: String? = dotenv["DAEMON_ENV"]

    if (sentryDsn != null && daemonEnv != null) {
      Sentry.init { options ->
        options.dsn = sentryDsn
        options.environment = daemonEnv
        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
          filterSentryEvent(event)
        }
      }
    }
  }

  private fun filterSentryEvent(event: SentryEvent): SentryEvent? {
    val throwable = event.throwable ?: return event
    val stackTrace = throwable.stackTrace
    if (stackTrace != null && stackTrace.isNotEmpty()) {
      val isJolt = stackTrace.any {
        it.className.startsWith("com.jolt.plugin")
      }
      return if (isJolt) event else null
    }
    return event
  }
}
