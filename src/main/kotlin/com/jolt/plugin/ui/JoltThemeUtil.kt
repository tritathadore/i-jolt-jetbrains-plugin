package com.jolt.plugin.ui

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil

object JoltThemeUtil {
  private fun colorToHex(color: java.awt.Color?): String {
    if (color == null) return "#000000"
    return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
  }

  fun isDarkTheme(): Boolean {
    val editorColorsManager = EditorColorsManager.getInstance()
    return editorColorsManager.isDarkEditor
  }

  fun getThemeCss(): String {
    val editorColorsScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
    val editorFont = editorColorsScheme.editorFontName
    val editorFontSize = editorColorsScheme.editorFontSize

    val keywordColor = editorColorsScheme.getAttributes(DefaultLanguageHighlighterColors.KEYWORD)?.foregroundColor
    val stringColor = editorColorsScheme.getAttributes(DefaultLanguageHighlighterColors.STRING)?.foregroundColor
    val constantColor = editorColorsScheme.getAttributes(DefaultLanguageHighlighterColors.CONSTANT)?.foregroundColor
    val commentColor = editorColorsScheme.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT)?.foregroundColor
    val functionCallColor = editorColorsScheme.getAttributes(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)?.foregroundColor
    val identifierColor = editorColorsScheme.getAttributes(DefaultLanguageHighlighterColors.IDENTIFIER)?.foregroundColor
    val variableColor = editorColorsScheme.getAttributes(DefaultLanguageHighlighterColors.LOCAL_VARIABLE)?.foregroundColor
    val globalVarColor = editorColorsScheme.getAttributes(DefaultLanguageHighlighterColors.GLOBAL_VARIABLE)?.foregroundColor
    val linkColor = editorColorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)?.foregroundColor
    val textColor = JBColor.foreground()
    val css = """
      :root {
        --jetbrains-editor-font-family: "$editorFont";
        --jetbrains-editor-font-size: ${editorFontSize}px;
        --jetbrains-editor-background: ${colorToHex(editorColorsScheme.defaultBackground)};
        --jetbrains-editor-foreground: ${colorToHex(editorColorsScheme.defaultForeground)};
        --jetbrains-widget-background: ${colorToHex(UIUtil.getPanelBackground())};
        --jetbrains-border-color: ${colorToHex(JBColor.border())};
        --jetbrains-link-foreground: ${colorToHex(linkColor)};
        --jolt-code-fg: ${colorToHex(editorColorsScheme.defaultForeground)};
        --jolt-code-comment-fg: ${colorToHex(commentColor)};
        --jolt-code-constant-fg: ${colorToHex(constantColor)};
        --jolt-code-storage-fg: ${colorToHex(globalVarColor)};
        --jolt-code-type-fg: ${colorToHex(identifierColor)};
        --jolt-code-string-fg: ${colorToHex(stringColor)};
        --jolt-code-variable-fg: ${colorToHex(variableColor)};
        --jolt-code-class-fg: ${colorToHex(functionCallColor)};
        --jolt-code-keyword-fg: ${colorToHex(keywordColor)};
      }

      body {
        font-size: ${editorFontSize}px;
        color: ${colorToHex(textColor)};
      }

    """.trimIndent()

    return css
  }
}
