package com.jolt.plugin.util

import io.sentry.Sentry
import java.net.BindException
import java.net.ServerSocket

const val DEFAULT_PORT = 56580
const val ALLOWED_CONCURRENT_INSTANCES = 15

object PortUtil {
  /**
   * Finds an available port by quickly checking and releasing.
   * Returns null if no ports are available.
   */
  fun findAvailablePort(
    startPort: Int = DEFAULT_PORT,
    endPort: Int = DEFAULT_PORT + ALLOWED_CONCURRENT_INSTANCES
  ): Int {
    for (port in startPort..endPort) {
      try {
        ServerSocket(port).use { return port }
      } catch (e: BindException) {
        continue
      } catch (e: Exception) {
        Sentry.captureException(e)
        continue
      }
    }
    return DEFAULT_PORT
  }

  /**
   * Returns true or false if the port is active and has a process running
   */
  fun isPortActive(port: Int): Boolean {
    try {
      ServerSocket(port).use { return false }
    } catch (e: BindException) {
      return true
    } catch (e: Exception) {
      return false
    }
  }
}
