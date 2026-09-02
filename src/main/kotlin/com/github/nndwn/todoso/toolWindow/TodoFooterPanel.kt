package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.MyBundle
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JButton

class TodoFooterPanel(
  val onNewTask: (String) -> Unit,
  val onAddTags: (String) -> Unit,
  val onCancelTask: (String) -> Unit,
  val onUpdateTask: (String) -> Unit,
  val onCancelEdit: () -> Unit,
) : JBPanel<JBPanel<*>>(BorderLayout()) {

  var isEditMode: Boolean = false
    private set

  val inputTextArea =
    JBTextArea().apply {
      emptyText.text = MyBundle.message("todo.window.input.placeholder")
      lineWrap = true
      wrapStyleWord = true
      rows = 3
      isOpaque = false
      border = JBUI.Borders.empty(8, 12)
      background = JBColor.namedColor("Todo.Input.Background", JBColor(0xF2F2F2, 0x1E1F22))

      addFocusListener(
        object : FocusAdapter() {
          override fun focusGained(e: FocusEvent?) = this@TodoFooterPanel.repaint()

          override fun focusLost(e: FocusEvent?) = this@TodoFooterPanel.repaint()
        }
      )
    }

  private val newTaskButton =
    JButton(MyBundle.message("todo.button.new.task")).apply {
      addActionListener {
        if (isEditMode) onUpdateTask(inputTextArea.text) else onNewTask(inputTextArea.text)
        inputTextArea.requestFocusInWindow()
      }
    }
  private val addTagsButton =
    JButton(MyBundle.message("todo.button.add.tags")).apply {
      addActionListener {
        onAddTags(inputTextArea.text)
        inputTextArea.requestFocusInWindow()
      }
    }
  val canceledTaskButton =
    JButton(MyBundle.message("todo.button.cancel.task")).apply {
      addActionListener {
        if (isEditMode) onCancelEdit() else onCancelTask(inputTextArea.text)
        inputTextArea.requestFocusInWindow()
      }
    }

  fun setEditMode(enabled: Boolean, text: String = "") {
    isEditMode = enabled
    if (enabled) {
      inputTextArea.text = text
      inputTextArea.background = JBColor.namedColor("Todo.Input.EditBackground", JBColor(0xE6F2FF, 0x2D3548))
      newTaskButton.text = MyBundle.message("todo.button.update")
      canceledTaskButton.text = MyBundle.message("todo.button.cancel.edit")
      addTagsButton.isVisible = false
      inputTextArea.requestFocusInWindow()
    } else {
      inputTextArea.text = ""
      inputTextArea.background = JBColor.namedColor("Todo.Input.Background", JBColor(0xF2F2F2, 0x1E1F22))
      newTaskButton.text = MyBundle.message("todo.button.new.task")
      canceledTaskButton.text = MyBundle.message("todo.button.cancel.task")
      addTagsButton.isVisible = true
    }
    repaint()
  }

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
    background = JBUI.CurrentTheme.ToolWindow.background()

    val inputWrapper =
      object : JBPanel<JBPanel<*>>(BorderLayout()) {
          override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

            val arc = 12
            val thickness = if (inputTextArea.hasFocus()) 2.0f else 1.0f
            val offset = thickness / 2f

            val rectX = offset.toInt()
            val rectY = offset.toInt()
            val rectW = width - thickness.toInt() - 1
            val rectH = height - thickness.toInt() - 1

            g2.color = inputTextArea.background
            g2.fillRoundRect(rectX, rectY, rectW, rectH, arc, arc)

            g2.color = if (inputTextArea.hasFocus()) JBUI.CurrentTheme.Focus.focusColor() else JBColor.border()
            g2.stroke = BasicStroke(thickness)
            g2.drawRoundRect(rectX, rectY, rectW, rectH, arc, arc)
            g2.dispose()
          }
        }
        .apply {
          isOpaque = false
          border = JBUI.Borders.empty(2)
        }

    val inputScrollPane =
      JBScrollPane(inputTextArea).apply {
        border = JBUI.Borders.empty()
        isOpaque = false
        viewport.isOpaque = false
      }

    inputWrapper.add(inputScrollPane, BorderLayout.CENTER)

    val marginWrapper =
      JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(8, 8, 4, 8)
        isOpaque = false
        add(inputWrapper, BorderLayout.CENTER)
      }

    val buttonsPanel =
      JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 5)).apply {
        isOpaque = false
        border = JBUI.Borders.empty(0, 3, 5, 3)
        add(newTaskButton)
        add(addTagsButton)
        add(canceledTaskButton)
      }

    add(marginWrapper, BorderLayout.CENTER)
    add(buttonsPanel, BorderLayout.SOUTH)
  }
}
