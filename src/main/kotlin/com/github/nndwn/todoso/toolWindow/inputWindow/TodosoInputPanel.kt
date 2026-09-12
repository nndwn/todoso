package com.github.nndwn.todoso.toolWindow.inputWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.TodosoIcons
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TodoValidator
import com.github.nndwn.todoso.toolWindow.inputWindow.components.RoundedInputPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

class TodosoInputPanel(
  private val project: Project,
  private val onNewTask: (String) -> Unit,
  private val onUpdateTask: (String) -> Unit,
  private val onConfirmCancel: (String) -> Unit,
  private val onCreateNote: (String) -> Unit,
  private val onCancelEdit: () -> Unit,
  private val fontInput: Font,
  private val getPopularTags: () -> List<String>,
  private val getAllTasks: () -> List<TodoTask>,
  private val onSuggestionRequest: (List<SuggestionItem>?) -> Unit,
  private val onNavigationRequest: (String) -> Unit,
) : JBPanel<TodosoInputPanel>(BorderLayout()) {

  companion object {
    private const val PROPERTY_NAME = "Todoso.Input.Background"
    private const val NEW_TASK_BUTTON = "todo.button.new.task"
    private const val UPDATE_BUTTON = "todo.button.update"
    private const val EDIT_BUTTON = "todo.button.cancel.edit"
    private const val BACKGROUND_COLOR_EDIT = "Todo.Input.EditBackground"
    private const val BACKGROUND_COLOR_CANCEL = "Todo.Input.CancelBackground"
    private const val BACKGROUND_COLOR_NORMAL = "Todo.Input.Background"

    private val LIST_EXTENSION_INSERT =   listOf("jpg", "jpeg", "png", "gif", "svg", "webp")
  }

  private var isOverlayVisible = false

  var currentMode: InputMode = InputMode.Normal
    private set

  val inputTextArea =
    JBTextArea().apply {
      font = fontInput.deriveFont(12f)
      emptyText.text = TodosoBundle.message("todo.input.placeholder")
      lineWrap = true
      wrapStyleWord = true
      rows = 3
      isOpaque = false
      border = JBUI.Borders.empty(8, 12)
      background = JBColor.namedColor(PROPERTY_NAME, JBColor(0xF2F2F2, 0x1E1F22))
    }

  val actionButton =
    JButton(TodosoBundle.message(NEW_TASK_BUTTON)).apply { addActionListener { handleMainAction() } }

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
      addActionListener { handleAttachFile() }
    }

  private val inputWrapper =
    RoundedInputPanel(inputTextArea).apply {
      preferredSize = Dimension(preferredSize.width, JBUI.scale(130))
    }

  init {
    isFocusable = true
    setupUI()
    setupListeners()
    updateActionButtons()
  }

  private fun setupUI() {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
    background = JBUI.CurrentTheme.ToolWindow.background()

    val marginWrapper =
      JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(16, 15, 4, 15)
        isOpaque = false
        add(inputWrapper, BorderLayout.CENTER)
      }

    val buttonsPanel =
      JBPanel<JBPanel<*>>(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(0, 15, 5, 15)

        val leftButtons =
          JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            isOpaque = false
            add(actionButton)
            add(cancelButton)
          }

        val rightWrapper = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            add(attachButton)
        }

        add(leftButtons, BorderLayout.WEST)
        add(rightWrapper, BorderLayout.EAST)
      }

    add(marginWrapper, BorderLayout.CENTER)
    add(buttonsPanel, BorderLayout.SOUTH)
  }

  private fun setupListeners() {
    inputTextArea.addFocusListener(
      object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) = this@TodosoInputPanel.repaint()

        override fun focusLost(e: FocusEvent?) = this@TodosoInputPanel.repaint()
      }
    )

    inputTextArea.document.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updateActionButtons()

          SwingUtilities.invokeLater {
            val prefix = getActivePrefix(inputTextArea.text, inputTextArea.caretPosition)
            if (prefix != null) {
              showSuggestionsPopup('#')
            } else {
              hideOverlay()
            }
          }
        }
      }
    )

    inputTextArea.addKeyListener(
      object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent) {
          // keyTyped sekarang hanya menangani inisiasi awal jika diperlukan
          // Sebagian besar logika sudah ditangani oleh DocumentListener di atas
        }

        override fun keyPressed(e: KeyEvent) {
          if (handlePopupNavigation(e)) return

          if (e.keyCode == KeyEvent.VK_ESCAPE) {
            if (isOverlayVisible) {
              hideOverlay()
              e.consume()
              return
            }
            if (currentMode !is InputMode.Normal) {
              onCancelEdit()
            }
          }
          if (e.keyCode == KeyEvent.VK_ESCAPE) return

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
        onNavigationRequest("DOWN")
        e.consume()
        return true
      }
      KeyEvent.VK_UP -> {
        onNavigationRequest("UP")
        e.consume()
        return true
      }
      KeyEvent.VK_ENTER -> {
        onNavigationRequest("ENTER")
        e.consume()
        return true
      }
    }
    return false
  }

  private fun handleMainAction() {
    val text = inputTextArea.text
    when (currentMode) {
      is InputMode.Edit -> onUpdateTask(text)
      is InputMode.Cancel -> onConfirmCancel(text)
      is InputMode.Note -> onCreateNote(ensureNotePrefix(text))
      is InputMode.Normal -> onNewTask(text)
    }
    inputTextArea.requestFocusInWindow()
  }

  fun setMode(mode: InputMode, initialText: String = "") {
    currentMode = mode
    when (mode) {
      is InputMode.Normal -> {
        inputTextArea.text = ""
        inputTextArea.background =
          JBColor.namedColor(BACKGROUND_COLOR_NORMAL, JBColor(0xF2F2F2, 0x1E1F22))
        actionButton.text = TodosoBundle.message(NEW_TASK_BUTTON)
        cancelButton.isVisible = false
      }
      is InputMode.Edit -> {
        inputTextArea.text = initialText
        inputTextArea.background =
          JBColor.namedColor(BACKGROUND_COLOR_EDIT, JBColor(0xE6F2FF, 0x2D3548))
        actionButton.text = TodosoBundle.message(UPDATE_BUTTON)
        cancelButton.isVisible = true
      }
      is InputMode.Cancel -> {
        inputTextArea.text = initialText
        inputTextArea.background =
          JBColor.namedColor(BACKGROUND_COLOR_CANCEL, JBColor(0xFFE6E6, 0x482D2D))
        actionButton.text = TodosoBundle.message(UPDATE_BUTTON)
        cancelButton.isVisible = true
      }
      is InputMode.Note -> {
        inputTextArea.text = if (initialText.isBlank()) "// " else ensureNotePrefix(initialText)
        inputTextArea.background =
          JBColor.namedColor(BACKGROUND_COLOR_EDIT, JBColor(0xE6F2FF, 0x2D3548))
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
        is InputMode.Edit -> isInputValid(currentText) && currentText != mode.originalText.trim()
        else -> isInputValid(currentText)
      }
  }

  internal fun isInputValid(text: String): Boolean {
    return TodoValidator.isContentValid(text)
  }

  fun clearInputText() = setMode(InputMode.Normal)

  fun requestUnfocus() {
    this.requestFocusInWindow()
  }

  private fun handleAttachFile() {
    val descriptor =
      FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
        .withTitle(TodosoBundle.message("todo.insert.file"))
        .withDescription(TodosoBundle.message("todo.insert.file.desc"))

    val selectedFile = FileChooser.chooseFile(descriptor, project, null) ?: return
    insertMarkdownAttachment(selectedFile)
  }

  internal fun insertMarkdownAttachment(file: VirtualFile) {
    val projectDir = project.guessProjectDir()
    val relativePath =
      if (projectDir != null) {
        VfsUtilCore.getRelativePath(file, projectDir) ?: file.path
      } else {
        file.path
      }

    val isImage =
      LIST_EXTENSION_INSERT.any {
        file.name.lowercase().endsWith(".$it")
      }

    val markdownSnippet =
      if (isImage) {
        "![${file.name}]($relativePath)"
      } else {
        "[${file.name}]($relativePath)"
      }

    SwingUtilities.invokeLater {
      val currentText = inputTextArea.text
      if (currentText.contains("//")) {
        inputTextArea.text = "$currentText $markdownSnippet"
      } else {
        inputTextArea.text = "$currentText // $markdownSnippet"
      }
      inputTextArea.requestFocusInWindow()
    }
  }

  private fun showSuggestionsPopup(triggerChar: Char) {
    val prefix = getActivePrefix(inputTextArea.text, inputTextArea.caretPosition) ?: ""

    val items = if (triggerChar == '#') {
      getSuggestions(prefix, getPopularTags(), getAllTasks())
    } else emptyList()

    if (items.isEmpty()) {
      hideOverlay()
      return
    }

    isOverlayVisible = true
    onSuggestionRequest(items)
  }

  private fun hideOverlay() {
    isOverlayVisible = false
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

  internal fun getSuggestions(
    prefix: String,
    popularTags: List<String>,
    allTasks: List<TodoTask>,
  ): List<SuggestionItem> {
    val suggestions = mutableListOf<SuggestionItem>()

    // 1. Tag Suggestions
    val tagItems = if (prefix.isEmpty()) {
      if (popularTags.isEmpty()) {
        TodosoConstants.DEFAULT_QUICK_TAGS.map {
          SuggestionItem(it, TodosoBundle.message("todo.suggestion.quick.tags"), IconLoader.getIcon("/general/add.png", javaClass))
        }
      } else {
        popularTags.map {
          SuggestionItem(it, TodosoBundle.message("todo.suggestion.popular.tags"), IconLoader.getIcon("/actions/checked.png", javaClass))
        }
      }
    } else {
      allTasks.flatMap { it.tags }
        .distinct()
        .filter { it.startsWith(prefix, ignoreCase = true) }
        .map { tag ->
          val isPopular = popularTags.contains(tag)
          SuggestionItem(
            tag,
            if (isPopular) TodosoBundle.message("todo.suggestion.popular.tags") else TodosoBundle.message("todo.suggestion.all.tags"),
            IconLoader.getIcon(if (isPopular) "/actions/checked.png" else "/nodes/tag.png", javaClass)
          )
        }
    }
    suggestions.addAll(tagItems)

    if (prefix.isNotEmpty()) {
      val relatedTasks =
        allTasks
          .filter { task -> task.tags.any { it.equals(prefix, ignoreCase = true) } }
          .sortedByDescending { it.metadata.createdDate ?: "" }

      suggestions.addAll(
        relatedTasks.map { task ->
          SuggestionItem(
            text = task.description.take(40) + (if (task.description.length > 40) "..." else ""),
            category = TodosoBundle.message("todo.suggestion.related.tags"),
            icon = IconLoader.getIcon("/nodes/variable.png", javaClass),
            isTask = true,
            taskId = task.id,
          )
        }
      )
    }
    return suggestions
  }

  internal fun getActivePrefix(text: String, caretPos: Int): String? {
    if (caretPos <= 0) return null

    val lastHash = text.substring(0, caretPos).lastIndexOf('#')
    if (lastHash == -1) return null

    val sub = text.substring(lastHash + 1, caretPos)
    return if (sub.contains(" ")) null else sub
  }
}
