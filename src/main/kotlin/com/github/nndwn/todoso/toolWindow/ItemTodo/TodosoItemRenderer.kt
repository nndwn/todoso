package com.github.nndwn.todoso.toolWindow.ItemTodo

import com.github.nndwn.todoso.TodosoIcons
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

class TodosoItemRenderer(
    private val settings: TodosoSettingsService
) : JBLabel(), ListCellRenderer<TodoTask> {

    init {
        isOpaque = true
        // Padding: Atas, Kiri, Bawah, Kanan (hapus border garis pemisah)
        border = JBUI.Borders.empty(8, 12)
        // Pastikan icon tetap di atas jika teks memanjang ke bawah
        verticalAlignment = TOP
        verticalTextPosition = TOP
        iconTextGap = JBUI.scale(12)
    }

    override fun getListCellRendererComponent(
        list: JList<out TodoTask>,
        value: TodoTask?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        value ?: return this

        // 1. Sync Warna Background
        background = if (isSelected) list.selectionBackground else list.background
        val defaultForeground = if (isSelected) list.selectionForeground else list.foreground

        // 2. Tentukan Icon
        icon = when (value.status) {
            TaskStatus.DOING -> TodosoIcons.TaskDoing
            TaskStatus.DONE -> TodosoIcons.TaskDone
            TaskStatus.CANCELLED -> TodosoIcons.TaskCancelled
            else -> TodosoIcons.TaskTodo
        }

        // 3. Kalkulasi Lebar Konten
        // Kita ambil lebar list dan kurangi dengan lebar icon + padding
        val iconWidth = icon?.iconWidth ?: 0
        val insets = border.getBorderInsets(this)
        val availableWidth = list.width - insets.left - insets.right - iconWidth - iconTextGap - JBUI.scale(25)

        // 4. Set Teks HTML (Wrapping terjadi karena <div> di dalam HTML)
        text = buildHtmlContent(value, isSelected, defaultForeground, availableWidth.coerceAtLeast(100))

        return this
    }

    private fun buildHtmlContent(task: TodoTask, isSelected: Boolean, defaultColor: Color, width: Int): String {
        val isDone = task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED
        val visualEnabled = settings.state.visualEnabled

        val baseColorHex = colorToHex(if (isSelected) defaultColor else if (isDone) UIUtil.getLabelDisabledForeground() else UIUtil.getLabelForeground())
        val tagColorHex = colorToHex(JBColor.CYAN)
        val priorityColorHex = colorToHex(task.priority.color)

        val textDecoration = if (isDone) "text-decoration: line-through;" else ""
        val fontWeight = if (task.status == TaskStatus.DOING) "font-weight: bold;" else ""
        val finalBaseColor = if (visualEnabled && !isDone && !isSelected) priorityColorHex else baseColorHex

        var description = task.description.replace("\n", "<br/>")
        
        // Highlight Tags
        TagParser.TAG_REGEX.findAll(description).forEach { match ->
            val tag = match.value
            description = description.replace(tag, "<span style='color: $tagColorHex; font-style: italic; font-weight: bold;'>$tag</span>")
        }

        return """
            <html>
            <body style='color: $finalBaseColor; font-family: ${font.family}; font-size: ${font.size}pt; $textDecoration $fontWeight'>
                <div style='width: ${width}px;'>
                    $description
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun colorToHex(color: Color): String = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
}
