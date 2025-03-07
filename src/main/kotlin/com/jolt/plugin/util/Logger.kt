package com.jolt.plugin.util

import com.jolt.plugin.services.LogService

object Logger {
  fun log(message: () -> String) {
    val msg = message()
    LogService.getInstance().appendLog(msg)
  }
}
