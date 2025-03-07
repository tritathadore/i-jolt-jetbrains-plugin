package com.jolt.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.jcef.executeJavaScript
import com.intellij.util.messages.MessageBusConnection
import com.jolt.plugin.core.JoltPluginDisposable
import com.jolt.plugin.services.DaemonService
import com.jolt.plugin.services.DaemonStatus
import com.jolt.plugin.services.DaemonStatusListener
import com.jolt.plugin.services.LogService
import com.jolt.plugin.util.Logger
import com.jolt.plugin.util.PortUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.cef.browser.CefMessageRouter
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JTextArea
import javax.swing.JScrollPane

class JoltToolFactoryWindow : ToolWindowFactory, Disposable, EditorColorsListener, DaemonStatusListener {
  private var messageRouter: CefMessageRouter? = null
  private var browser: JBCefBrowser? = null
  private var project: Project? = null
  private var messageBusConnection: MessageBusConnection? = null
  private var logTextArea: JTextArea? = null
  private var logScrollPane: JScrollPane? = null
  private var panel: JBLoadingPanel? = null
  private var isLogVisible = false
  private var lastKnownStatus: DaemonStatus? = null
  private var pollingJob: Job? = null
  private val coroutineScope = CoroutineScope(Dispatchers.Main)


  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    this.project = project
    Disposer.register(JoltPluginDisposable.getInstance(project), this)

    browser = JBCefBrowser.createBuilder().build()
    messageRouter = CefMessageRouter.create()
    messageRouter?.addHandler(UiMessageRouterHandler(), true)
    browser?.jbCefClient?.cefClient?.addMessageRouter(messageRouter!!)

    val devToolsAction = object : AnAction("Web Dev Tools", "Open web developer tools for the UI", AllIcons.Javaee.WebModuleGroup) {
      override fun actionPerformed(e: AnActionEvent) {
        browser?.openDevtools()
      }
    }

    val toggleLogAction = object : AnAction("Toggle Log Panel", "Toggle the logs for the daemon process", AllIcons.Actions.ToggleVisibility) {
      override fun actionPerformed(e: AnActionEvent) {
        toggleLogVisibility()
      }
    }

    toolWindow.setTitleActions(listOf(devToolsAction, toggleLogAction))

    logTextArea = JTextArea().apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      font = Font(Font.MONOSPACED, Font.PLAIN, 10)
    }

    logScrollPane = JBScrollPane(logTextArea).apply {
      preferredSize = Dimension(Int.MAX_VALUE, 200)
      minimumSize = Dimension(0, 200)
      maximumSize = Dimension(Int.MAX_VALUE, 200)
      isVisible = isLogVisible
    }

    panel = JBLoadingPanel(BorderLayout(), this).apply {
      browser?.component?.let { add(it, BorderLayout.CENTER) }
      logScrollPane?.let { add(it, BorderLayout.SOUTH) }
    }

    logTextArea?.let { LogService.getInstance().setTextArea(it) }
    messageBusConnection = project.messageBus.connect()
    messageBusConnection?.subscribe(EditorColorsManager.TOPIC, this)
    messageBusConnection?.subscribe(DaemonStatusListener.TOPIC, this)

    val content = toolWindow.contentManager.factory.createContent(panel, null, false)
    toolWindow.contentManager.addContent(content)
    panel?.setLoadingText("Loading Jolt AI...")
    panel?.startLoading()

    startStatusPolling()
  }

  private fun startStatusPolling() {
    pollingJob = coroutineScope.launch {
      while (isActive) {
        val thisProject = project ?: continue
        val daemonService = DaemonService.getInstance(thisProject)
        val currentStatus = daemonService.status
        val daemonPort = daemonService.daemonPort
        var status = currentStatus

        // The daemon may have fired the started signal before the listener
        // was registered so check if the port is active as well
        if (PortUtil.isPortActive(daemonPort)) {
          status = DaemonStatus.started
        }

        Logger.log { "Daemon status updated: $status" }

        if (currentStatus != lastKnownStatus) {
          handleStatusChange(status)
        }

        // If we've moved out of the pending state, we can stop polling
        if (status != DaemonStatus.pending) {
          pollingJob?.cancel()
        }
        delay(1000)
      }
    }
  }

  private fun handleStatusChange(newStatus: DaemonStatus) {
    lastKnownStatus = newStatus
    if (newStatus != DaemonStatus.pending) {
      panel?.stopLoading()
      loadContent()
    }
  }

  private fun toggleLogVisibility() {
    isLogVisible = !isLogVisible
    logScrollPane?.isVisible = isLogVisible
    logScrollPane?.parent?.revalidate()
    logScrollPane?.parent?.repaint()
  }

  private fun loadContent() {
    try {
      val scriptContent = javaClass.getResource("/web/index.js")?.readText() ?: ""
      val styleContent = javaClass.getResource("/web/css/index.css")?.readText() ?: ""
      val daemonPort = project?.let { DaemonService.getInstance(it).daemonPort } ?: 0
      val themeClass = if (JoltThemeUtil.isDarkTheme()) "jetbrains-dark" else "jetbrains-light"
      val daemonStatus = project?.let { DaemonService.getInstance(it).status } ?: DaemonStatus.failedToStart

      val htmlContent = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Jolt AI</title>
          <style type="text/css" id="jolt-stylesheet">
            ${JoltThemeUtil.getThemeCss()}
          </style>
          <style type="text/css">
            $styleContent
          </style>
        </head>
        <body class="$themeClass">
          <div id="root" class="font-sans"></div>
          <script type="application/json" id="jolt-state">
            {
              "route":"/chat",
              "hasWslConfigProblem":false,
              "daemonPort":$daemonPort,
              "daemonStatus":"$daemonStatus"
            }
          </script>
          <script type="module">
            $scriptContent
          </script>
        </body>
        </html>
      """.trimIndent()

      browser?.loadHTML(htmlContent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun onStatusChanged(newStatus: DaemonStatus) {
    // Process status changes from the message bus
    handleStatusChange(newStatus)
  }

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    val isDarkTheme = JoltThemeUtil.isDarkTheme()
    CoroutineScope(Dispatchers.Main).launch {
      try {
        // When the theme is changed in a JetBrains IDE, toggle the stylesheet
        // and update the theme class so dark:/light: tailwind selectors take effect
        browser?.executeJavaScript(
          """
          document.querySelector('#jolt-stylesheet').textContent = `${JoltThemeUtil.getThemeCss()}`;
          window.updateThemeClass(${isDarkTheme});
        """.trimIndent()
        )
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override fun dispose() {
    pollingJob?.cancel()
    pollingJob = null
    messageBusConnection?.disconnect()
    messageBusConnection = null
    messageRouter?.let { router ->
      browser?.jbCefClient?.cefClient?.removeMessageRouter(router)
    }
    panel = null
    browser?.dispose()
    messageRouter = null
    browser = null
    project = null
    logTextArea = null
    logScrollPane = null
  }
}
