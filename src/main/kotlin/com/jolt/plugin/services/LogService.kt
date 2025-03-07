package com.jolt.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import javax.swing.JTextArea

@Service(Service.Level.APP)
class LogService {
  private var textArea: JTextArea? = null
  private val messageBuffer = mutableListOf<String>()

  fun setTextArea(area: JTextArea) {
    textArea = area
    messageBuffer.forEach { message ->
      area.append("$message\n")
    }
    messageBuffer.clear()
  }

  fun appendLog(message: String) {
    if (textArea == null) {
      messageBuffer.add(message)
    } else {
      textArea?.apply {
        append("$message\n")
        caretPosition = document.length
      }
    }
  }

  companion object {
    fun getInstance(): LogService = ApplicationManager.getApplication().service()
  }
}
