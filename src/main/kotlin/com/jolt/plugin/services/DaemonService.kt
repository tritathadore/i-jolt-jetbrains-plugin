package com.jolt.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jolt.plugin.util.Logger
import com.jolt.plugin.core.JoltPluginDisposable
import com.jolt.plugin.util.PortUtil
import io.sentry.Sentry
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists

enum class DaemonStatus {
  pending,
  started,
  nodeNotFound,
  failedToStart,
}

@Service(Service.Level.PROJECT)
class DaemonService(private val project: Project) : Disposable {
  private var daemonProcess: Process? = null
  private var daemonFilepath: Path? = null
  private var processJob: Job? = null
  val daemonPort: Int = PortUtil.findAvailablePort()
  var status: DaemonStatus = DaemonStatus.pending
    set(value) {
      field = value
      project.messageBus
        .syncPublisher(DaemonStatusListener.TOPIC)
        .onStatusChanged(value)
    }
  init {
    Disposer.register(JoltPluginDisposable.getInstance(project), this)
  }

  private suspend fun readProcessOutput(process: Process) {
    withContext(Dispatchers.IO) {
      process.inputStream.bufferedReader().use { reader ->
        while (isActive && process.isAlive) {
          val line = reader.readLine() ?: break
          Logger.log { line }

          if (line.contains("jolt_daemon_started:$daemonPort")) {
            status = DaemonStatus.started
          }

          if (line.contains("jolt_daemon_shutting_down:$daemonPort")) {
            dispose()
            break
          }
        }
      }
    }
  }

  suspend fun startDaemon(projectPath: String) {
    try {
      val engineResource = javaClass.getResourceAsStream("/daemon.mjs")
        ?: error("Resource '/daemon.mjs' not found")

      daemonFilepath = withContext(Dispatchers.IO) {
        Files.createTempFile("jolt-daemon", ".mjs").apply {
          engineResource.use { stream ->
            Files.copy(stream, this, StandardCopyOption.REPLACE_EXISTING)
          }
        }
      }

      val nodePath = NodeService(project).findNodeExecutable()
      Logger.log { "Using Node.js at: $nodePath" }

      val processBuilder = ProcessBuilder(
        nodePath, daemonFilepath.toString(), "--port", this.daemonPort.toString()
      )
      processBuilder.directory(java.io.File(projectPath))
      processBuilder.redirectErrorStream(true)

      daemonProcess = withContext(Dispatchers.IO) {
        processBuilder.start().also { process ->
          processJob = CoroutineScope(Dispatchers.IO).launch {
            readProcessOutput(process)
          }
        }
      }
    } catch (e: NodeNotFoundException) {
      Sentry.captureException(e)
      status = DaemonStatus.nodeNotFound
      Logger.log { "Node not found" }
      dispose()
    } catch (e: Exception) {
      Sentry.captureException(e)
      status = DaemonStatus.failedToStart
      Logger.log { "Failed to start daemon: ${e.message}" }
      dispose()
    }
  }

  companion object {
    fun getInstance(project: Project): DaemonService = project.service()
  }

  override fun dispose() {
    try {
      processJob?.cancel()
      processJob = null
      daemonProcess?.destroyForcibly()
      daemonProcess = null
      daemonFilepath?.deleteIfExists()
      daemonFilepath = null
    } catch (e: Exception) {
      Sentry.captureException(e)
    }
  }
}
