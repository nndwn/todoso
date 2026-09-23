package com.github.nndwn.todoso.toolWindow.inputWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoIcons
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.toolWindow.inputWindow.components.RoundedInputPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JLayeredPane
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

class TodosoInputPanel(
  private val onNewTask: (String) -> Unit,
  private val onUpdateTask: (String) -> Unit,
  private val onConfirmCancel: (String) -> Unit,
  private val onCreateNote: (String) -> Unit,
  private val onCancelEdit: () -> Unit,
  private val fontInput: Font,
  private val onSuggestionProvider: (prefix: String) -> List<SuggestionItem>,
  private val onSuggestionRequest: (List<SuggestionItem>?) -> Unit,
  private val onNavigationRequest: (SuggestionNav) -> Unit,
  private val onTabPressed: () -> Unit,
  private val onAttachFileRequest: () -> Unit,
  private val onTextValidator: (String) -> Boolean,
) : JBPanel<TodosoInputPanel>(BorderLayout()) {

  companion object {
    private const val PROPERTY_NAME = "Todoso.Input.Background"
    private const val NEW_TASK_BUTTON = "todo.button.new.task"
    private const val UPDATE_BUTTON = "todo.button.update"
    private const val EDIT_BUTTON = "todo.button.cancel.edit"
    private const val BACKGROUND_COLOR_EDIT = "Todo.Input.EditBackground"
    private const val BACKGROUND_COLOR_CANCEL = "Todo.Input.CancelBackground"
    private const val BACKGROUND_COLOR_NORMAL = "Todo.Input.Background"
  }

  private var isOverlayVisible = false
  private var isNavigationActive = false

  var currentMode: InputMode = InputMode.Normal
    private set

  val inputTextArea =
    JBTextArea().apply {
      font = fontInput.deriveFont(12f)
      emptyText.text = TodosoBundle.message("todo.input.placeholder")
      TextComponentEmptyText.setupPlaceholderVisibility(this)
      lineWrap = true
      wrapStyleWord = true
      rows = 3
      isOpaque = false
      border = JBUI.Borders.empty(8, 12)
      background = JBColor.namedColor(PROPERTY_NAME, JBColor(0xF2F2F2, 0x1E1F22))
    }

  val actionButton = JButton(TodosoBundle.message(NEW_TASK_BUTTON)).apply { addActionListener { handleMainAction() } }

  val cancelButton =
    JButton(TodosoBundle.message(EDIT_BUTTON)).apply {
      isVisible = false
      addActionListener {
        onCancelEdit()
        inputTextArea.requestFocusInWindow()
      }
    }

  val attachButton =
    JButton(TodosoIcons.AddFile).apply {
      toolTipText = TodosoBundle.message("todo.insert.file")
      isContentAreaFilled = false
      isBorderPainted = false
      isFocusPainted = false
      isFocusable = false
      margin = JBUI.emptyInsets()
      border = null
      preferredSize = Dimension(JBUI.scale(30), JBUI.scale(30))
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      addActionListener { onAttachFileRequest() }
    }

  private val inputWrapper =
    RoundedInputPanel(inputTextArea).apply {
      preferredSize = Dimension(preferredSize.width, JBUI.scale(130))
    }

  private val statusCountPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
    isOpaque = false
    isVisible = false
  }

  private val layeredPane = object : JLayeredPane() {
    override fun getPreferredSize(): Dimension = mainContent.preferredSize
    override fun doLayout() {
      super.doLayout()
      mainContent.bounds = Rectangle(0, 0, width, height)
      updateStatusPosition()
    }
  }
  private val mainContent = JBPanel<JBPanel<*>>(BorderLayout())

  init {
    isFocusable = true
    inputTextArea.setFocusTraversalKeysEnabled(false)
    setupUI()
    setupListeners()
    setupContextMenu()
    updateActionButtons()
  }

  private fun setupContextMenu() {
    inputTextArea.addMouseListener(
      object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (e.isPopupTrigger) showMenu(e)
        }

        override fun mouseReleased(e: MouseEvent) {
          if (e.isPopupTrigger) showMenu(e)
        }

        private fun showMenu(e: MouseEvent) {
          val menu = JPopupMenu()
          val mask = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx

          val pasteAction = JMenuItem(TodosoBundle.message("todo.menu.paste"), AllIcons.Actions.MenuPaste)
          pasteAction.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_V, mask)
          pasteAction.addActionListener { inputTextArea.paste() }
          menu.add(pasteAction)

          menu.show(e.component, e.x, e.y)
        }
      }
    )
  }

  private fun setupUI() {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
    background = JBUI.CurrentTheme.ToolWindow.background()

    mainContent.isOpaque = false
    val marginWrapper =
      JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(21, 12, 4, 12)
        isOpaque = false
        add(inputWrapper, BorderLayout.CENTER)
      }

    val buttonsPanel =
      JBPanel<JBPanel<*>>(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(0, 5, 5, 5)

        val leftButtons =
          JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            isOpaque = false
            add(actionButton)
            add(cancelButton)
          }

        val rightWrapper =
          JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            add(attachButton)
          }

        add(leftButtons, BorderLayout.WEST)
        add(rightWrapper, BorderLayout.EAST)
      }

    mainContent.add(marginWrapper, BorderLayout.CENTER)
    mainContent.add(buttonsPanel, BorderLayout.SOUTH)

    layeredPane.add(mainContent, JLayeredPane.DEFAULT_LAYER)
    layeredPane.add(statusCountPanel, JLayeredPane.PALETTE_LAYER)

    add(layeredPane, BorderLayout.CENTER)

    layeredPane.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        mainContent.bounds = layeredPane.bounds
        updateStatusPosition()
      }
    })
  }

  private fun setupListeners() {
    inputTextArea.addFocusListener(
      object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          this@TodosoInputPanel.repaint()
          // Picu suggestion jika kosong (atau hanya berisi spasi) saat mendapatkan fokus hanya di InputMode.Normal
          if (inputTextArea.text.trim().isEmpty() && currentMode is InputMode.Normal) {
            showSuggestionsPopup('!')
          }
        }

        override fun focusLost(e: FocusEvent?) {
          this@TodosoInputPanel.repaint()
          // Sembunyikan suggestion saat kehilangan fokus
          hideOverlay()
        }
      }
    )

    inputTextArea.document.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updateActionButtons()

          SwingUtilities.invokeLater {
            val text = inputTextArea.text
            val prefix = getActivePrefix(text, inputTextArea.caretPosition)
            
            when {
              prefix != null -> showSuggestionsPopup('#')
              text.trim().isEmpty() && currentMode is InputMode.Normal -> showSuggestionsPopup('!') // Priority hanya di Normal
              else -> hideOverlay()
            }
          }
        }
      }
    )

    // Aksi Escape di tingkat komponen menggunakan ActionMap (Cara Standar Swing)
    val escapeStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
    inputTextArea.getInputMap(WHEN_FOCUSED).put(escapeStroke, "hide-overlay-action")
    inputTextArea.actionMap.put("hide-overlay-action", object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (isOverlayVisible) {
          hideOverlay()
        } else if (currentMode !is InputMode.Normal) {
          onCancelEdit()
        }
      }
    })

    inputTextArea.addKeyListener(
      object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent) {
          // keyTyped sekarang hanya menangani inisiasi awal jika diperlukan
          // Sebagian besar logika sudah ditangani oleh DocumentListener di atas
        }

        override fun keyPressed(e: KeyEvent) {
          if (handlePopupNavigation(e)) return

          if (e.keyCode == KeyEvent.VK_TAB) {
            onTabPressed()
            e.consume()
            return
          }

          if (e.keyCode == KeyEvent.VK_ENTER) {
            if (e.isShiftDown) {
              inputTextArea.replaceSelection("\n")
              e.consume()
            } else {
              e.consume()
              if (actionButton.isEnabled) actionButton.doClick()
            }
          }
        }
      }
    )
  }

  private fun handlePopupNavigation(e: KeyEvent): Boolean {
    if (!isOverlayVisible) return false

    when (e.keyCode) {
      KeyEvent.VK_DOWN -> {
        isNavigationActive = true
        onNavigationRequest(SuggestionNav.DOWN)
        e.consume()
        return true
      }
      KeyEvent.VK_UP -> {
        isNavigationActive = true
        onNavigationRequest(SuggestionNav.UP)
        e.consume()
        return true
      }
      KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> {
        // Jika overlay muncul, Enter atau Tab selalu konfirmasi saran
        onNavigationRequest(SuggestionNav.ENTER)
        e.consume()
        return true
      }
    }
    return false
  }

  private fun handleMainAction() {
    val text = inputTextArea.text
    val mode = currentMode
    when (mode) {
      is InputMode.Edit -> onUpdateTask(text)
      is InputMode.Cancel -> onConfirmCancel(text)
      is InputMode.Note -> onCreateNote(ensureNotePrefix(text))
      is InputMode.Normal -> onNewTask(text)
    }

    if (mode is InputMode.Normal) {
      ApplicationManager.getApplication().invokeLater {
        inputTextArea.requestFocusInWindow()
      }
    }
  }

  fun setMode(mode: InputMode, initialText: String = "") {
    currentMode = mode
    hideOverlay()
    when (mode) {
      is InputMode.Normal -> {
        inputTextArea.text = ""
        inputTextArea.emptyText.text = TodosoBundle.message("todo.input.placeholder")
        inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_NORMAL, JBColor(0xF2F2F2, 0x1E1F22))
        actionButton.text = TodosoBundle.message(NEW_TASK_BUTTON)
        cancelButton.isVisible = false
      }
      is InputMode.Edit -> {
        inputTextArea.text = initialText
        inputTextArea.emptyText.text = TodosoBundle.message("todo.input.placeholder")
        inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_EDIT, JBColor(0xE6F2FF, 0x2D3548))
        actionButton.text = TodosoBundle.message(UPDATE_BUTTON)
        cancelButton.isVisible = true
      }
      is InputMode.Cancel -> {
        inputTextArea.text = initialText
        inputTextArea.emptyText.text = TodosoBundle.message("todo.action.cancel.noted.required")
        inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_CANCEL, JBColor(0xFFE6E6, 0x482D2D))
        actionButton.text = TodosoBundle.message("todo.button.cancel.task")
        cancelButton.isVisible = true
      }
      is InputMode.Note -> {
        inputTextArea.text = if (initialText.isBlank()) "// " else ensureNotePrefix(initialText)
        inputTextArea.emptyText.text = TodosoBundle.message("todo.input.placeholder")
        inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_EDIT, JBColor(0xE6F2FF, 0x2D3548))
        actionButton.text = TodosoBundle.message(UPDATE_BUTTON)
        cancelButton.isVisible = true
      }
    }
    if (mode !is InputMode.Normal) inputTextArea.requestFocusInWindow()
    updateActionButtons()
    repaint()
  }

  fun setEditMode(enabled: Boolean, text: String = "") =
    setMode(if (enabled) InputMode.Edit(text) else InputMode.Normal, text)

  fun setNoteMode(enabled: Boolean, text: String = "") =
    setMode(if (enabled) InputMode.Note else InputMode.Normal, text)

  fun setCancelMode(enabled: Boolean, text: String = "") =
    setMode(if (enabled) InputMode.Cancel else InputMode.Normal, text)

  private fun ensureNotePrefix(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.startsWith("//")) trimmed else "// $trimmed"
  }

  private fun updateActionButtons() {
    val currentText = inputTextArea.text.trim()
    actionButton.isEnabled =
      when (val mode = currentMode) {
        is InputMode.Note -> currentText.removePrefix("//").trim().isNotEmpty()
        is InputMode.Edit -> onTextValidator(currentText) && currentText != mode.originalText.trim()
        else -> onTextValidator(currentText)
      }
  }

  fun clearInputText() = setMode(InputMode.Normal)

  fun requestFocusToInput() {
    inputTextArea.requestFocusInWindow()
  }

  fun unfocus() {
    hideOverlay()
    this.requestFocusInWindow()
  }

  fun updateStatusCounts(counts: Map<TaskStatus, Int>) {
    statusCountPanel.removeAll()
    TaskStatus.entries.forEach { status ->
      val count = counts[status] ?: 0
      if (count > 0) {
        val fontSize = 11f
        val scaledIcon = IconUtil.scale(status.icon, null, JBUIScale.scale(fontSize) / status.icon.iconHeight)
        
        val labelColor = JBColor.namedColor("Label.infoForeground", JBColor(0x808080, 0x8C8C8C))
        val colorizedIcon = IconUtil.colorize(scaledIcon, labelColor)

        val label = JBLabel("$count", colorizedIcon, SwingConstants.LEFT).apply {
          font = fontInput.deriveFont(Font.BOLD, JBUIScale.scale(fontSize))
          foreground = labelColor
          iconTextGap = JBUI.scale(4)
        }
        statusCountPanel.add(label)
      }
    }
    statusCountPanel.isVisible = statusCountPanel.componentCount > 0
    statusCountPanel.revalidate()
    statusCountPanel.repaint()
    updateStatusPosition()
  }

  private fun updateStatusPosition() {
    if (!statusCountPanel.isVisible) return
    val prefSize = statusCountPanel.preferredSize
    val x = width - prefSize.width - JBUI.scale(3)
    val y = JBUI.scale(3)
    statusCountPanel.bounds = Rectangle(x, y, prefSize.width, prefSize.height)
  }

  internal fun showSuggestionsPopup(triggerChar: Char) {
    val prefix = getActivePrefix(inputTextArea.text, inputTextArea.caretPosition) ?: ""

    // Panggil provider tanpa filter triggerChar agar Priority ('!') bisa lewat
    val items = onSuggestionProvider(prefix)

    if (items.isEmpty()) {
      hideOverlay()
      return
    }

    isOverlayVisible = true
    isNavigationActive = false // Reset status navigasi setiap kali pop-up baru muncul
    onSuggestionRequest(items)
  }

  internal fun hideOverlay() {
    isOverlayVisible = false
    isNavigationActive = false
    onSuggestionRequest(null)
  }

  internal fun insertItemAtCaret(content: String, isTask: Boolean = false) {
    val doc = inputTextArea.document
    val caretPos = inputTextArea.caretPosition
    val text = inputTextArea.text

    val lastHash = text.substring(0, caretPos).lastIndexOf('#')
    if (lastHash != -1) {
      val startReplace = if (isTask) lastHash else lastHash + 1
      doc.remove(startReplace, caretPos - startReplace)
      doc.insertString(startReplace, content, null)
    } else {
      doc.insertString(caretPos, content, null)
    }
    inputTextArea.requestFocusInWindow()
  }

  internal fun shouldTriggerPopup(): Boolean {
    val caretPos = inputTextArea.caretPosition
    val text = inputTextArea.text
    if (text.isEmpty()) return true
    return (if (caretPos > 0) text[caretPos - 1] else ' ').isWhitespace()
  }

  internal fun getActivePrefix(text: String, caretPos: Int): String? {
    if (caretPos <= 0) return null

    val lastHash = text.substring(0, caretPos).lastIndexOf('#')
    if (lastHash == -1) return null

    val sub = text.substring(lastHash + 1, caretPos)
    // Jangan anggap prefix jika mengandung spasi, tab, atau baris baru (whitespace)
    return if (sub.any { it.isWhitespace() }) null else sub
  }
}
