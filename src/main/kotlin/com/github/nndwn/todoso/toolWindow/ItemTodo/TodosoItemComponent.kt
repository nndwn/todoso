package com.github.nndwn.todoso.toolWindow.ItemTodo

import com.github.nndwn.todoso.TodosoIcons
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import javax.swing.text.html.HTMLEditorKit

class TodosoItemComponent(
    val task: TodoTask,
    private val isVisualEnabled: Boolean,
    private val onSelect: (TodoTask) -> Unit,
    private val onEdit: (TodoTask) -> Unit
) : JPanel(BorderLayout()), Scrollable {

    private var isSelected = false

    private val iconLabel = JBLabel().apply {
        verticalAlignment = SwingConstants.TOP
        border = JBUI.Borders.empty(10, 10, 0, 0)
        icon = when (task.status) {
            TaskStatus.DOING -> TodosoIcons.TaskDoing
            TaskStatus.DONE -> TodosoIcons.TaskDone
            TaskStatus.CANCELLED -> TodosoIcons.TaskCancelled
            else -> TodosoIcons.TaskTodo
        }
    }

    private val textPane = object : JTextPane() {
        override fun getScrollableTracksViewportWidth(): Boolean = true
    }.apply {
        contentType = "text/html"
        editorKit = HTMLEditorKit()
        isEditable = false
        isOpaque = false
        isFocusable = false
        
        // Matikan caret dan highlighter agar tidak lompat saat di-klik
        highlighter = null
        (caret as? DefaultCaret)?.apply {
            updatePolicy = DefaultCaret.NEVER_UPDATE
        }
        
        border = JBUI.Borders.empty(8, 10)
        
        // Agar link atau teks tidak menghalangi klik ke panel
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = dispatchToParent(e)
            override fun mouseReleased(e: MouseEvent) = dispatchToParent(e)
            override fun mouseClicked(e: MouseEvent) = dispatchToParent(e)
            override fun mouseEntered(e: MouseEvent) = dispatchToParent(e)
            override fun mouseExited(e: MouseEvent) = dispatchToParent(e)
        })
    }

    init {
        isOpaque = true
        background = UIUtil.getListBackground()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        
        add(iconLabel, BorderLayout.WEST)
        add(textPane, BorderLayout.CENTER)
        
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
        
        updateContent()
        setupEvents()
    }

    private fun dispatchToParent(e: MouseEvent) {
        val parentEvent = MouseEvent(
            this, e.id, e.`when`, e.modifiersEx,
            e.x + textPane.x, e.y + textPane.y,
            e.clickCount, e.isPopupTrigger, e.button
        )
        processMouseEvent(parentEvent)
    }

    private fun setupEvents() {
        val hoverListener = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                if (!isSelected) {
                    background = JBColor.namedColor("List.hoverBackground", Color(0xEDF6FF))
                    repaint()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                if (!isSelected) {
                    // Cek apakah mouse benar-benar keluar dari area panel ini (termasuk anak-anaknya)
                    val point = e.point
                    SwingUtilities.convertPointToScreen(point, e.component)
                    val bounds = Rectangle(locationOnScreen, size)
                    if (!bounds.contains(point)) {
                        background = UIUtil.getListBackground()
                        repaint()
                    }
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.clickCount == 2) {
                    onEdit(task)
                } else {
                    onSelect(task)
                }
            }
        }
        
        addMouseListener(hoverListener)
        // Tambahkan ke iconLabel juga agar tidak ada celah
        iconLabel.addMouseListener(hoverListener)
    }

    fun setSelected(selected: Boolean) {
        this.isSelected = selected
        background = if (selected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
        updateContent()
    }

    private fun updateContent() {
        val foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
        textPane.text = buildHtml(foreground)
    }

    private fun buildHtml(defaultColor: Color): String {
        val isDone = task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED
        val baseColorHex = colorToHex(if (isDone && !isSelected) UIUtil.getLabelDisabledForeground() else defaultColor)
        val tagColorHex = colorToHex(JBColor.CYAN)
        val priorityColorHex = colorToHex(task.priority.color)

        val textDecoration = if (isDone) "text-decoration: line-through;" else ""
        val fontWeight = if (task.status == TaskStatus.DOING) "font-weight: bold;" else ""
        val finalBaseColor = if (isVisualEnabled && !isDone && !isSelected) priorityColorHex else baseColorHex

        var description = task.description.replace("\n", "<br/>")
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

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 20
    override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 100
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false

    private fun colorToHex(color: Color): String = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
}
