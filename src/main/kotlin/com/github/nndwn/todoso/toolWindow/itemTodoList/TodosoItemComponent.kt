package com.github.nndwn.todoso.toolWindow.itemTodoList

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.DateParser
import com.github.nndwn.todoso.domain.parser.TaskIdParser
import com.github.nndwn.todoso.services.TodosoService
import com.intellij.ide.BrowserUtil
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultCaret
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

class TodosoItemComponent(
  private val service: TodosoService,
  var task: TodoTask,
  private var isVisualEnabled: Boolean,
  private val onSelect: (TodoTask) -> Unit,
  private val onDoubleClick: (TodoTask) -> Unit,
  private val onContextMenu: (TodoTask, MouseEvent) -> Unit,
  private val onDelete: (TodoTask) -> Unit,
  private val onNavigate: (Int) -> Unit,
  private val onTabPressed: () -> Unit,
  private val onSearch: (String) -> Unit,
) : JPanel(BorderLayout()), Scrollable {

  private var isSelected = false
  private var lastHtml: String? = null

  private val iconLabel =
    JBLabel().apply {
      verticalAlignment = SwingConstants.TOP
      border = JBUI.Borders.empty(10, 10, 0, 0)
      updateIcon(this, task, isVisualEnabled)
    }

  private val textPane =
    object : JTextPane() {
      override fun getScrollableTracksViewportWidth(): Boolean = true

      override fun processMouseEvent(e: MouseEvent) {
        val isLink = isLinkAt(e.point)
        super.processMouseEvent(e)

        if (isLink && (e.id == MouseEvent.MOUSE_PRESSED || e.id == MouseEvent.MOUSE_RELEASED || e.id == MouseEvent.MOUSE_CLICKED)) {
          if (e.id == MouseEvent.MOUSE_PRESSED) {
            requestFocusInWindow()
          }
        } else {
          dispatchToParent(e)
        }
      }

      override fun processMouseMotionEvent(e: MouseEvent) {
        super.processMouseMotionEvent(e)
        dispatchToParent(e)
      }

      private fun isLinkAt(p: Point): Boolean {
        val pos = viewToModel2D(p)
        if (pos >= 0) {
          val doc = document as? HTMLDocument
          val elem = doc?.getCharacterElement(pos)
          val a = elem?.attributes?.getAttribute(HTML.Tag.A)
          return a != null
        }
        return false
      }
    }
      .apply {
        contentType = "text/html"
        editorKit = HTMLEditorKit()
        isEditable = false
        isOpaque = false
        isFocusable = false

        addHyperlinkListener { e ->
          if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            val desc = e.description
            when {
              desc.startsWith("tag:") -> onSearch("#" + desc.removePrefix("tag:"))
              desc.startsWith("id:") -> onSearch(desc.removePrefix("id:"))
              else -> BrowserUtil.browse(desc)
            }
          }
        }

        highlighter = null
        (caret as? DefaultCaret)?.apply {
          updatePolicy = DefaultCaret.NEVER_UPDATE
        }

        border = JBUI.Borders.empty(8, 10)
      }

  init {
    isOpaque = true
    isFocusable = true
    setFocusTraversalKeysEnabled(false)
    background = UIUtil.getListBackground()
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    add(iconLabel, BorderLayout.WEST)
    add(textPane, BorderLayout.CENTER)

    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 0, 0, 0, 0)

    updateContent()
    setupEvents()
  }

  private fun dispatchToParent(e: MouseEvent) {
    val parentEvent = SwingUtilities.convertMouseEvent(textPane, e, this)
    if (e.id == MouseEvent.MOUSE_MOVED || e.id == MouseEvent.MOUSE_DRAGGED) {
      processMouseMotionEvent(parentEvent)
    } else {
      processMouseEvent(parentEvent)
    }
  }

  private fun setupEvents() {
    val hoverListener =
      object : MouseAdapter() {
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
          requestFocusInWindow()
          if (e.isPopupTrigger) {
            onContextMenu(task, e)
          } else {
            if (e.clickCount == 2) {
              onDoubleClick(task)
            } else {
              onSelect(task)
            }
          }
        }

        override fun mouseReleased(e: MouseEvent) {
          if (e.isPopupTrigger) {
            onContextMenu(task, e)
          }
        }
      }

    addMouseListener(hoverListener)
    iconLabel.addMouseListener(hoverListener)

    addKeyListener(
      object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          when (e.keyCode) {
            KeyEvent.VK_DELETE -> onDelete(task)
            KeyEvent.VK_UP -> {
              onNavigate(KeyEvent.VK_UP)
              e.consume()
            }
            KeyEvent.VK_DOWN -> {
              onNavigate(KeyEvent.VK_DOWN)
              e.consume()
            }
            KeyEvent.VK_TAB -> {
              onTabPressed()
              e.consume()
            }
            KeyEvent.VK_ENTER -> {
              onSelect(task)
              e.consume()
            }
          }
        }
      }
    )
  }

  fun setSelected(selected: Boolean) {
    this.isSelected = selected
    background = if (selected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
    updateContent()
  }

  fun updateData(newTask: TodoTask, newVisualEnabled: Boolean) {
    val visualChanged = isVisualEnabled != newVisualEnabled
    val statusChanged = this.task.status != newTask.status
    val priorityChanged = this.task.priority != newTask.priority
    val textChanged = this.task.description != newTask.description || this.task.metadata.notes != newTask.metadata.notes

    if (statusChanged || priorityChanged || textChanged || visualChanged) {
      this.task = newTask
      this.isVisualEnabled = newVisualEnabled
      updateIcon(iconLabel, task, isVisualEnabled)
      updateContent()
    } else {

      this.task = newTask
    }
  }

  private fun updateIcon(label: JBLabel, task: TodoTask, visualEnabled: Boolean) {
    val baseIcon = task.status.icon

    label.icon =
      if (visualEnabled) {
        IconUtil.colorize(baseIcon, task.priority.color)
      } else {
        baseIcon
      }
  }

  private fun updateContent() {
    val foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
    val newHtml = TodosoHtmlBuilder.build(task, isSelected, isVisualEnabled, foreground)

    if (lastHtml != newHtml) {
      textPane.text = newHtml
      lastHtml = newHtml
    }

    this.toolTipText = null
    textPane.toolTipText = null

    tooltip()
  }

  private fun tooltip() {
    HelpTooltip.dispose(this)
    val ht = HelpTooltip()

    val title =
      if (task.isPersistentId) {
        TodosoBundle.message("todo.tooltip.task.id", task.id)
      } else {
        TodosoBundle.message("todo.tooltip.task.details")
      }
    ht.setTitle { title }

    val chunks = mutableListOf<HtmlChunk>()

    // 1. Deskripsi lengkap untuk status DONE atau CANCELLED
    if (task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED) {
      chunks.add(HtmlChunk.tag("b").addText(task.description))
      chunks.add(HtmlChunk.br())
    }

    // 2. Detail Tugas Utama
    appendTaskMetadata(chunks, task)

    // 2. Rujukan
    val referencedIds =
      TaskIdParser.TASK_ID_REGEX.findAll(task.description).map { it.groupValues[1] }.distinct().filter { it != task.id }

    referencedIds.forEach { refId ->
      service.findTaskById(refId)?.let { refTask ->
        chunks.add(HtmlChunk.hr())
        chunks.add(HtmlChunk.tag("b").addText(TodosoBundle.message("todo.tooltip.reference", refId)))
        chunks.add(HtmlChunk.br())
        chunks.add(HtmlChunk.text(refTask.description))
        appendTaskMetadata(chunks, refTask)
      }
    }

    if (chunks.isNotEmpty()) {
      ht.description = HtmlChunk.div().children(*chunks.toTypedArray()).toString()
    }

    ht.installOn(this)
  }

  private fun appendTaskMetadata(chunks: MutableList<HtmlChunk>, task: TodoTask) {
    val meta = task.metadata
    val dateLines =
      listOfNotNull(
        meta.startDate?.let { TodosoBundle.message("todo.tooltip.date.start", it) },
        meta.dueDate?.let { TodosoBundle.message("todo.tooltip.date.due", it) },
        meta.endDate?.let { TodosoBundle.message("todo.tooltip.date.done", it) },
        meta.cancelDate?.let { TodosoBundle.message("todo.tooltip.date.cancelled", it) },
        meta.createdDate?.let { TodosoBundle.message("todo.tooltip.date.created", it) },
        meta.editedDate?.let { TodosoBundle.message("todo.tooltip.date.edited", it) },
      )

    if (dateLines.isNotEmpty()) {
      if (chunks.isNotEmpty()) chunks.add(HtmlChunk.br())
      chunks.add(HtmlChunk.text(dateLines.joinToString("\n")))
    }

    if (task.status == TaskStatus.DONE) {
      DateParser.calculateDuration(meta)?.let { duration ->
        if (chunks.isNotEmpty()) chunks.add(HtmlChunk.br())
        chunks.add(HtmlChunk.text(TodosoBundle.message("todo.tooltip.duration", duration)))
      }
    }

    if (meta.notes.isNotBlank()) {
      chunks.add(HtmlChunk.br())
      chunks.add(HtmlChunk.br())
      chunks.add(HtmlChunk.tag("b").addText(TodosoBundle.message("todo.tooltip.note")))
      chunks.add(HtmlChunk.br())
      chunks.add(HtmlChunk.text(processMarkdownLinksForTooltip(meta.notes)))
    }
  }

  private fun processMarkdownLinksForTooltip(notes: String): String {
    var result = notes
    val imageRegex = Regex("""!\[.*?]\((.*?)\)""")
    result = imageRegex.replace(result) { TodosoBundle.message("todo.tooltip.image", it.groupValues[1]) }

    val linkRegex = Regex("""\[(.*?)]\((.*?)\)""")
    result = linkRegex.replace(result) { TodosoBundle.message("todo.tooltip.file", it.groupValues[2]) }

    return result
  }

  override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

  override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 20

  override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 100

  override fun getScrollableTracksViewportWidth(): Boolean = true

  override fun getScrollableTracksViewportHeight(): Boolean = false
}
