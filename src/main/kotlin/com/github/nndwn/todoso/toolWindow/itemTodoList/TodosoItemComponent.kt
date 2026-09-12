package com.github.nndwn.todoso.toolWindow.itemTodoList

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoIcons
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.DateParser
import com.github.nndwn.todoso.domain.parser.TaskIdParser
import com.github.nndwn.todoso.services.TodosoService
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
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
    private val service: TodosoService,
    var task: TodoTask,
    private var isVisualEnabled: Boolean,
    private val onSelect: (TodoTask) -> Unit,
    private val onEdit: (TodoTask) -> Unit
) : JPanel(BorderLayout()), Scrollable {

    private var isSelected = false

    private val iconLabel = JBLabel().apply {
        verticalAlignment = SwingConstants.TOP
        border = JBUI.Borders.empty(10, 10, 0, 0)
        updateIcon(this, task, isVisualEnabled)
    }

    private val textPane = object : JTextPane() {
        override fun getScrollableTracksViewportWidth(): Boolean = true
    }.apply {
        contentType = "text/html"
        editorKit = HTMLEditorKit()
        isEditable = false
        isOpaque = false
        isFocusable = false

        highlighter = null
        (caret as? DefaultCaret)?.apply {
            updatePolicy = DefaultCaret.NEVER_UPDATE
        }
        
        border = JBUI.Borders.empty(8, 10)

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
        
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 0, 0, 0, 0)
        
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
                if (!isSelected && isShowing) {
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
        iconLabel.addMouseListener(hoverListener)
    }

    fun setSelected(selected: Boolean) {
        this.isSelected = selected
        background = if (selected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
        updateContent()
    }


    fun updateData(newTask: TodoTask, newVisualEnabled: Boolean) {
        val visualChanged = isVisualEnabled != newVisualEnabled
        val dataChanged = this.task.rawText != newTask.rawText || this.task.status != newTask.status || this.task.priority != newTask.priority

        if (dataChanged || visualChanged) {
            this.task = newTask
            this.isVisualEnabled = newVisualEnabled
            updateIcon(iconLabel, task, isVisualEnabled)
            updateContent()
        }
    }

    private fun updateIcon(label: JBLabel, task: TodoTask, visualEnabled: Boolean) {
        val baseIcon = when (task.status) {
            TaskStatus.DOING -> TodosoIcons.TaskDoing
            TaskStatus.DONE -> TodosoIcons.TaskDone
            TaskStatus.CANCELLED -> TodosoIcons.TaskCancelled
            else -> TodosoIcons.TaskTodo
        }

        label.icon = if (visualEnabled) {
            IconUtil.colorize(baseIcon, task.priority.color)
        } else {
            baseIcon
        }
    }

    private fun updateContent() {
        val foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
        textPane.text = TodosoHtmlBuilder.build(task, isSelected, isVisualEnabled, foreground)

        this.toolTipText = null
        textPane.toolTipText = null

        tooltip()
    }

    private fun tooltip() {
        HelpTooltip.dispose(this)
        val ht = HelpTooltip()

        val title = if (task.isPersistentId) {
            TodosoBundle.message("todo.tooltip.task.id", task.id)
        } else {
            TodosoBundle.message("todo.tooltip.task.details")
        }
        ht.setTitle(title)

        val meta = task.metadata
        val dateLines = listOfNotNull(
            meta.startDate?.let { "Start: $it" },
            meta.dueDate?.let { "Due: $it" },
            meta.endDate?.let { "Done: $it" },
            meta.cancelDate?.let { "Cancelled: $it" },
            meta.createdDate?.let { "Created: $it" },
            meta.editedDate?.let { "Edited: $it" }
        )

        val chunks = mutableListOf<HtmlChunk>()
        
        // 1. Tanggal
        if (dateLines.isNotEmpty()) {
            chunks.add(HtmlChunk.text(dateLines.joinToString("\n")))
        }

        // 2. Durasi
        if (task.status == TaskStatus.DONE) {
            DateParser.calculateDuration(meta)?.let { duration ->
                if (chunks.isNotEmpty()) chunks.add(HtmlChunk.br())
                chunks.add(HtmlChunk.text(TodosoBundle.message("todo.tooltip.duration", duration)))
            }
        }

        // 3. Catatan
        if (meta.notes.isNotBlank()) {
            chunks.add(HtmlChunk.br())
            chunks.add(HtmlChunk.br())
            chunks.add(HtmlChunk.tag("b").addText(TodosoBundle.message("todo.tooltip.note")))
            chunks.add(HtmlChunk.br())
            chunks.add(HtmlChunk.text(processMarkdownLinksForTooltip(meta.notes)))
        }

        // 4. Rujukan
        val referencedIds = TaskIdParser.TASK_ID_REGEX.findAll(task.description)
            .map { it.groupValues[1] }
            .distinct()
            .filter { it != task.id }

        referencedIds.forEach { refId ->
            service.findTaskById(refId)?.let { refTask ->
                chunks.add(HtmlChunk.hr())
                chunks.add(HtmlChunk.tag("b").addText("Reference $refId:"))
                chunks.add(HtmlChunk.br())
                chunks.add(HtmlChunk.text(refTask.description))
                
                val refDates = listOfNotNull(
                    refTask.metadata.startDate?.let { "Start: $it" },
                    refTask.metadata.dueDate?.let { "Due: $it" },
                    refTask.metadata.endDate?.let { "Done: $it" }
                )
                if (refDates.isNotEmpty()) {
                    chunks.add(HtmlChunk.br())
                    chunks.add(HtmlChunk.text(refDates.joinToString("\n")))
                }
            }
        }

        if (chunks.isNotEmpty()) {
            ht.description = HtmlChunk.div().children(*chunks.toTypedArray()).toString()
        }

        ht.installOn(this)
    }

    private fun processMarkdownLinksForTooltip(notes: String): String {
        var result = notes
        val imageRegex = Regex("""!\[.*?]\((.*?)\)""")
        result = imageRegex.replace(result) { "🖼️ Image: ${it.groupValues[1]}" }

        val linkRegex = Regex("""\[(.*?)]\((.*?)\)""")
        result = linkRegex.replace(result) { "📎 File: ${it.groupValues[2]}" }
        
        return result
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 20
    override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 100
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
}
