package com.github.nndwn.todoso.toolWindow.itemTodoList

import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.domain.parser.TaskIdParser
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

object TodosoHtmlBuilder {
  private val URL_REGEX = Regex("""\b(?:https?://|www\.)[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}[^\s<]*\b|\b[a-zA-Z0-9.-]+\.(?:com|org|net|io|me|id|gov|edu|github\.io)(?:/[^\s<]*)?\b""")

  fun build(
    task: TodoTask,
    isSelected: Boolean,
    isVisualEnabled: Boolean,
    defaultColor: Color,
  ): String {
    val isDone = task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED

    // Kalkulasi Warna
    val baseColorHex = colorToHex(if (isDone && !isSelected) UIUtil.getLabelDisabledForeground() else defaultColor)
    val tagColorHex = colorToHex(JBColor.CYAN)
    val priorityColorHex = colorToHex(task.priority.color)

    // Gaya CSS
    val isDoing = task.status == TaskStatus.DOING
    val textDecoration = if (isDone) "text-decoration: line-through;" else ""
    val fontWeight = if (isDoing) "font-weight: bold;" else ""

    // Warna dasar menyesuaikan status dan visual mode
    val finalBaseColor =
      when {
        isSelected -> baseColorHex
        isDone -> baseColorHex
        isDoing && isVisualEnabled -> colorToHex(JBColor.GREEN)
        isVisualEnabled -> priorityColorHex
        else -> baseColorHex
      }

    // Pemrosesan Teks Deskripsi
    var description =
      task.description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>")

    // Highlight Tags
    description = TagParser.TAG_REGEX.replace(description) { match ->
      val tag = match.value
      val tagName = match.groupValues[1]
      "<a href='tag:$tagName' style='color: $tagColorHex; font-style: italic; font-weight: bold; text-decoration: none;'>$tag</a>"
    }

    // Highlight IDs
    description = TaskIdParser.TASK_ID_REGEX.replace(description) { match ->
      val idText = match.value
      val idVal = match.groupValues[1]
      "<a href='id:$idVal' style='color: $tagColorHex; font-weight: bold; text-decoration: none;'>$idText</a>"
    }

    // Highlight External Links
    description = URL_REGEX.replace(description) { match ->
      val url = match.value
      val href = if (!url.contains("://") && !url.startsWith("www.")) "https://$url" else if (url.startsWith("www.")) "https://$url" else url
      "<a href='$href' style='color: #589DF6; text-decoration: underline;'>$url</a>"
    }

    return """
            <html>
            <body style='color: $finalBaseColor; font-family: ${UIUtil.getLabelFont().family}; font-size: ${UIUtil.getLabelFont().size}pt; $textDecoration $fontWeight'>
                <div style='margin: 0; padding: 0;'>
                    $description
                </div>
            </body>
            </html>
        """
      .trimIndent()
  }

  private fun colorToHex(color: Color): String = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
}
