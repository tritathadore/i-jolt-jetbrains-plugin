package com.jolt.plugin.ui

import com.jolt.plugin.core.*
import com.jolt.plugin.services.FileService
import com.jolt.plugin.services.LocalContextService
import com.jolt.plugin.services.ProjectService
import com.jolt.plugin.util.Logger
import io.sentry.Sentry
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandler
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.net.URI

class UiMessageRouterHandler: CefMessageRouterHandler {
  override fun getNativeRef(identifer: String?) = 0L
  override fun setNativeRef(identifer: String?, nativeRef: Long) {}
  override fun onQueryCanceled(browser: CefBrowser?, frame: CefFrame?, queryId: Long) {}

  override fun onQuery(
    browser: CefBrowser?,
    frame: CefFrame?,
    queryId: Long,
    json: String?,
    persistent: Boolean,
    callback: CefQueryCallback?
  ): Boolean {
    try {
      val jsonObject = JSONObject(json)
      val eventType = enumFromValue<UiEventType>(jsonObject.getString("type")) { it.value }
      val eventData = jsonObject.getJSONObject("data")

      if (eventData == null) {
        Sentry.captureMessage("Failed to parse json $json")
        callback?.failure(400, "Could not parse request to Ui Event")
        return false
      }

      when (eventType) {
        UiEventType.OpenFile -> {
          val path = eventData.getString("path")
          val project = ProjectService.getProject() ?: run {
            callback?.failure(400, "The open project does not match the git repo")
            return false
          }
          FileService.openFile(
            project = project,
            callback = callback,
            path = path
          )
        }
        UiEventType.ApplyChangedFilesNative -> {
          val gitRepoUrl = eventData.getString("gitRepoUrl")
          val project = ProjectService.getProject() ?: run {
            val errorMessage = "The open project does not match the git repo $gitRepoUrl"
            Sentry.captureMessage(errorMessage)
            callback?.failure(400, errorMessage)
            return false
          }
          val changedFilesArray = eventData.getJSONArray("changedFiles")

          for (i in 0 until changedFilesArray.length()) {
            val fileObj = changedFilesArray.getJSONObject(i)
            val operation = enumFromValue<FileOpType>(fileObj.getString("operation")) { it.value } ?: continue
            val changedFile = ChangedFile(
              filepath = fileObj.getString("filepath"),
              content = if (fileObj.has("content")) fileObj.getString("content") else null,
              operation = operation
            )
            FileService.applyFileUpdate(
              project = project,
              changedFile = changedFile
            )
          }
          val responseJson = JSONObject()
          responseJson.put("success", true)
          callback?.success(responseJson.toString())
        }
        UiEventType.GetLocalContextNative -> {
          val project = ProjectService.getProject() ?: run {
            callback?.failure(400, "The open project does not match the git repo")
            return false
          }
          val localContextService = LocalContextService.getInstance(project)
          val localContext = localContextService.getLocalContextData()
          val responseJson = JSONObject()
          responseJson.put("activeTab", localContext.activeTab)
          val openTabsArray = JSONArray()
          localContext.openTabs.forEach { openTabsArray.put(it) }
          responseJson.put("openTabs", openTabsArray)
          callback?.success(responseJson.toString())
        }
        UiEventType.OpenUrlNative -> {
          val url = eventData.getString("url")
          try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
              Desktop.getDesktop().browse(URI(url))
              val responseJson = JSONObject()
              responseJson.put("success", true)
              callback?.success(responseJson.toString())
            } else {
              callback?.failure(500, "No browser found to open URL")
            }
          } catch (e: Exception) {
            Logger.log { "Failed to open URL: ${e.message}" }
            callback?.failure(500, "Failed to open URL: ${e.message}")
          }
        }
        else -> {
          val errorMessage = "Unhandled event type: $eventType"
          Sentry.captureMessage(errorMessage)
          callback?.failure(400, errorMessage)
          return true
        }
      }

    } catch (e: Exception) {
      Sentry.captureException(e)
      callback?.failure(500, "Internal error: ${e.message}")
    }
    return true
  }
}
