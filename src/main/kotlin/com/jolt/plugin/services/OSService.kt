package com.jolt.plugin.services

object OSService {
  fun isWindows() = System.getProperty("os.name").lowercase().contains("windows")

  fun unixNodePaths() = listOf(
    "/usr/bin/node",
    "/usr/local/bin/node",
    "/snap/bin/node",
    "/opt/node/bin/node",
    "/opt/homebrew/bin/node",
    "${System.getProperty("user.home")}/.nvm/versions/node",
    "${System.getProperty("user.home")}/.volta/bin/node"
  )

  fun windowsNodePaths() = listOf(
    "C:\\Program Files\\nodejs\\node.exe",
    "C:\\Program Files (x86)\\nodejs\\node.exe",
    "${System.getenv("LOCALAPPDATA")}\\Programs\\node\\node.exe",
    "${System.getenv("LOCALAPPDATA")}\\nvm",
    "${System.getProperty("user.home")}\\AppData\\Roaming\\nvm",
    "${System.getProperty("user.home")}\\.fnm\\versions",
  )
}
