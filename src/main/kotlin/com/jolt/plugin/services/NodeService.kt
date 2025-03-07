package com.jolt.plugin.services

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.project.Project
import com.jolt.plugin.util.Logger
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.jvm.Throws

class NodeNotFoundException: Exception("Node executable not found")

class NodeService(private val project: Project) {
  // Helper function to check if Node version is 18 or higher
  private fun isNodeVersionValid(nodePath: String): Pair<Boolean, String> {
    try {
      val process = ProcessBuilder(nodePath, "--version").start()
      val reader = BufferedReader(InputStreamReader(process.inputStream))
      val versionOutput = reader.readLine() // Format: v18.x.x
      process.waitFor()

      val versionStr = versionOutput.trim().removePrefix("v")
      val majorVersion = versionStr.split(".").firstOrNull()?.toIntOrNull() ?: 0
      val isValid = majorVersion >= 18

      Logger.log { "Checking node version at $nodePath: $versionOutput (valid=${isValid})" }
      return Pair(isValid, versionStr)
    } catch (e: Exception) {
      return Pair(false, "0.0.0")
    }
  }

  // Collect all potential Node executable paths
  private fun collectPotentialNodePaths(): List<String> {
    val potentialPaths = mutableListOf<String>()

    PathMacroManager.getInstance(project).expandPath("\$JOLT_NODE_PATH\$")?.let { envVarPath ->
      Logger.log { "Found JOLT_NODE_PATH: $envVarPath" }
      if (File(envVarPath).canExecute()) {
        potentialPaths.add(envVarPath)
        return potentialPaths
      }
    }

    // Get common paths based on OS
    val commonPaths = if (OSService.isWindows()) {
      OSService.windowsNodePaths()
    } else {
      OSService.unixNodePaths()
    }

    // Process each path
    for (path in commonPaths) {
      Logger.log { "Checking path: $path" }
      if (path.contains(".nvm/versions/node")) {
        // Special handling for NVM directory structure
        val directory = File(path)
        if (directory.exists()) {
          try {
            // Get all version directories (v18.18.2, v20.11.0, etc.)
            val versionDirs = directory.listFiles { file ->
              file.isDirectory && file.name.startsWith("v")
            }?.toList() ?: emptyList()

            // Add all executable node paths from NVM
            versionDirs.forEach { dir ->
              val nodeFile = File(dir, "bin/node")
              if (nodeFile.exists() && nodeFile.canExecute()) {
                potentialPaths.add(nodeFile.absolutePath)
              }
            }
          } catch (e: Exception) {
            Logger.log { "Error searching NVM directory ${directory.absolutePath}: ${e.message}" }
          }
        }
      } else if (path.endsWith("/node") || path.endsWith("node.exe")) {
        val file = File(path)
        if (file.canExecute()) {
          Logger.log { "Found executable node: ${file.absolutePath}" }
          potentialPaths.add(file.absolutePath)
        }
      } else {
        // Handle other directory searches
        val directory = File(path)
        if (directory.exists()) {
          try {
            directory.walk()
              .filter { it.name == "node" || it.name == "node.exe" }
              .filter { it.canExecute() }
              .forEach { potentialPaths.add(it.absolutePath) }
          } catch (e: Exception) {
            Logger.log { "Error searching directory ${directory.absolutePath}: ${e.message}" }
          }
        }
      }
    }

    return potentialPaths
  }

  // Filter paths by version and select the best one
  private fun selectBestNodePath(paths: List<String>): String? {
    // First check if JOLT_NODE_PATH is valid
    System.getenv("JOLT_NODE_PATH")?.let { envVarPath ->
      if (paths.contains(envVarPath) && isNodeVersionValid(envVarPath).first) {
        Logger.log { "Using JOLT_NODE_PATH as it has a valid Node version: $envVarPath" }
        return envVarPath
      } else if (paths.contains(envVarPath)) {
        Logger.log { "JOLT_NODE_PATH exists but doesn't have a valid Node version (>=18): $envVarPath" }
      }
    }

    // Filter paths by version and get node info
    val validNodePaths = paths.mapNotNull { path ->
      try {
        val versionCheck = isNodeVersionValid(path)
        if (versionCheck.first) {
          Pair(path, versionCheck.second.removePrefix("v"))
        } else {
          null
        }
      } catch (e: Exception) {
        null
      }
    }

    // Sort by version (highest first) and return the best path
    return validNodePaths.maxByOrNull { (_, version) ->
      version
    } ?.first
  }

  @Throws(NodeNotFoundException::class)
  fun findNodeExecutable(): String {
    // Collect all potential paths
    val potentialPaths = collectPotentialNodePaths()
    Logger.log { "Found ${potentialPaths.size} potential Node paths" }

    // Select the best path
    return selectBestNodePath(potentialPaths) ?: throw NodeNotFoundException()
  }
}
