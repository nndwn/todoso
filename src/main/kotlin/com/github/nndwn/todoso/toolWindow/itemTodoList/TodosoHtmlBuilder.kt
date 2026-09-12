package com.github.nndwn.todoso.toolWindow.itemTodoList

import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

object TodosoHtmlBuilder {
    fun build(
        task: TodoTask,
        isSelected: Boolean,
        isVisualEnabled: Boolean,
        defaultColor: Color
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
        val finalBaseColor = when {
            isSelected -> baseColorHex
            isDone -> baseColorHex
            isDoing && isVisualEnabled -> colorToHex(JBColor.GREEN)
            isVisualEnabled -> priorityColorHex
            else -> baseColorHex
        }

        // Pemrosesan Teks Deskripsi
        var description = task.description
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")
        
        // Highlight Tags
        TagParser.TAG_REGEX.findAll(description).forEach { match ->
            val tag = match.value
            description = description.replace(tag, "<span style='color: $tagColorHex; font-style: italic; font-weight: bold;'>$tag</span>")
        }

        return """
            <html>
            <body style='color: $finalBaseColor; font-family: ${UIUtil.getLabelFont().family}; font-size: ${UIUtil.getLabelFont().size}pt; $textDecoration $fontWeight'>
                <div style='margin: 0; padding: 0;'>
                    $description
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun colorToHex(color: Color): String = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
}
