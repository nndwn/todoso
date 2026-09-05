package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.MyBundle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.event.DocumentEvent

class TodoFooterPanel(
  val onNewTask: (String) -> Unit,
  val onUpdateTask: (String) -> Unit,
  val onConfirmCancel: (String) -> Unit,
  val onCancelEdit: () -> Unit,
) : JBPanel<TodoFooterPanel>(BorderLayout()) {

  var isEditMode: Boolean = false
    private set

  var isCancelMode: Boolean = false
    private set

  private var originalText: String = ""

  val inputTextArea =
    JBTextArea().apply {
      emptyText.text = MyBundle.message("todo.window.input.placeholder")
      lineWrap = true
      wrapStyleWord = true
      rows = 3
      isOpaque = false
      border = JBUI.Borders.empty(8, 12)
      background = JBColor.namedColor("Todo.Input.Background", JBColor(0xF2F2F2, 0x1E1F22))
    }

  val newTaskButton =
    JButton(MyBundle.message("todo.button.new.task")).apply {
      addActionListener {
        when {
          isEditMode -> onUpdateTask(inputTextArea.text)
          isCancelMode -> onConfirmCancel(inputTextArea.text)
          else -> onNewTask(inputTextArea.text)
        }
        inputTextArea.requestFocusInWindow()
      }
    }

  val cancelButton =
    JButton(MyBundle.message("todo.button.cancel.edit")).apply {
      isVisible = false
      addActionListener {
        onCancelEdit()
        inputTextArea.requestFocusInWindow()
      }
    }

  fun setEditMode(enabled: Boolean, text: String = "") {
    isEditMode = enabled
    isCancelMode = false
    originalText = text
    if (enabled) {
      inputTextArea.text = text
      inputTextArea.background = JBColor.namedColor("Todo.Input.EditBackground", JBColor(0xE6F2FF, 0x2D3548))
      newTaskButton.text = MyBundle.message("todo.button.update")
      cancelButton.isVisible = true
      inputTextArea.requestFocusInWindow()
    } else {
      inputTextArea.text = ""
      inputTextArea.background = JBColor.namedColor("Todo.Input.Background", JBColor(0xF2F2F2, 0x1E1F22))
      newTaskButton.text = MyBundle.message("todo.button.new.task")
      cancelButton.isVisible = false
    }
    updateActionButtons()
    repaint()
  }

  fun setCancelMode(enabled: Boolean) {
    isCancelMode = enabled
    isEditMode = false
    if (enabled) {
      inputTextArea.background = JBColor.namedColor("Todo.Input.CancelBackground", JBColor(0xFFE6E6, 0x482D2D))
      newTaskButton.text = MyBundle.message("todo.button.update")
      cancelButton.isVisible = true
      inputTextArea.requestFocusInWindow()
    } else {
      inputTextArea.text = ""
      inputTextArea.background = JBColor.namedColor("Todo.Input.Background", JBColor(0xF2F2F2, 0x1E1F22))
      newTaskButton.text = MyBundle.message("todo.button.new.task")
      cancelButton.isVisible = false
    }
    updateActionButtons()
    repaint()
  }

  fun clearInputText() {
    inputTextArea.text = ""
    updateActionButtons()
  }

  private fun updateActionButtons() {
    val currentText = inputTextArea.text.trim()
    val hasMeaningfulContent = isInputValid(currentText)

    if (isEditMode) {
      newTaskButton.isEnabled = hasMeaningfulContent && currentText != originalText.trim()
    } else {
      newTaskButton.isEnabled = hasMeaningfulContent
    }
  }

  private fun isInputValid(text: String): Boolean {
    if (text.isEmpty()) return false
    val prefixOnlyRegex = Regex("""^- \[[ x/-]]\s*$""")
    return !prefixOnlyRegex.matches(text)
  }

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
    background = JBUI.CurrentTheme.ToolWindow.background()

    inputTextArea.addFocusListener(
      object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) = this@TodoFooterPanel.repaint()

        override fun focusLost(e: FocusEvent?) = this@TodoFooterPanel.repaint()
      }
    )

    inputTextArea.document.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updateActionButtons()
        }
      }
    )

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
        add(cancelButton)
      }

    inputTextArea.addKeyListener(
      object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ESCAPE && (isEditMode || isCancelMode)) {
            onCancelEdit()
          }
        }
      }
    )

    add(marginWrapper, BorderLayout.CENTER)
    add(buttonsPanel, BorderLayout.SOUTH)

    updateActionButtons()
  }
}
