package com.jolt.plugin.core

enum class UiEventType(val value: String) {
  OpenFile("openFile"),
  ApplyChangedFilesNative("applyChangedFilesNative"),
  GetLocalContextNative("getLocalContextNative"),
  OpenUrlNative("openUrlNative"),
}

enum class FileOpType(val value: String) {
  Create("create"),
  Delete("delete"),
  Update("update"),
  Move("move");
}

inline fun <reified T : Enum<T>> enumFromValue(value: String, valueExtractor: (T) -> String): T? {
  return enumValues<T>().find { valueExtractor(it) == value }
}

data class ChangedFile(
  val filepath: String,
  val operation: FileOpType,
  val content: String?
)

data class LocalContextData(
  val activeTab: String?,
  val openTabs: List<String>,
)
