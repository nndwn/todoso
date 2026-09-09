package com.github.nndwn.todoso.toolWindow.inputWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodosoSuggestionService
import com.github.nndwn.todoso.toolWindow.inputWindow.components.RoundedInputPanel
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JList
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
    private val getAllTasks: () -> List<TodoTask>
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

    private val suggestionService = project.service<TodosoSuggestionService>()
    private var activePopup: JBPopup? = null

    var currentMode: InputMode = InputMode.Normal
        private set

    val inputTextArea = JBTextArea().apply {
        font = fontInput.deriveFont(12f)
        emptyText.text = TodosoBundle.message("todo.input.placeholder")
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        isOpaque = false
        border = JBUI.Borders.empty(8, 12)
        background = JBColor.namedColor(PROPERTY_NAME, JBColor(0xF2F2F2, 0x1E1F22))
    }

    val newTaskButton = JButton(TodosoBundle.message(NEW_TASK_BUTTON)).apply {
        addActionListener { handleMainAction() }
    }

    val cancelButton = JButton(TodosoBundle.message(EDIT_BUTTON)).apply {
        isVisible = false
        addActionListener {
            onCancelEdit()
            inputTextArea.requestFocusInWindow()
        }
    }

    private val inputWrapper = RoundedInputPanel(inputTextArea)

    init {
        setupUI()
        setupListeners()
        updateActionButtons()
    }

    private fun setupUI() {
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
        background = JBUI.CurrentTheme.ToolWindow.background()

        val marginWrapper = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 8, 4, 8)
            isOpaque = false
            add(inputWrapper, BorderLayout.CENTER)
        }

        val buttonsPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 5)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 3, 5, 3)
            add(newTaskButton)
            add(cancelButton)
        }

        add(marginWrapper, BorderLayout.CENTER)
        add(buttonsPanel, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        inputTextArea.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) = this@TodosoInputPanel.repaint()
            override fun focusLost(e: FocusEvent?) = this@TodosoInputPanel.repaint()
        })

        inputTextArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updateActionButtons()
        })

        inputTextArea.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                val char = e.keyChar
                if (char == '#' && shouldTriggerPopup()) {
                    SwingUtilities.invokeLater { showSuggestionsPopup(char) }
                } else {
                    SwingUtilities.invokeLater {
                        if (suggestionService.getActivePrefix(inputTextArea.text, inputTextArea.caretPosition) != null) {
                            showSuggestionsPopup('#')
                        } else {
                            activePopup?.cancel()
                        }
                    }
                }
            }

            override fun keyPressed(e: KeyEvent) {
                if (handlePopupNavigation(e)) return

                if (e.keyCode == KeyEvent.VK_ESCAPE && currentMode !is InputMode.Normal) {
                    onCancelEdit()
                }
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    if (newTaskButton.isEnabled) newTaskButton.doClick()
                }
            }
        })
    }

    private fun handlePopupNavigation(e: KeyEvent): Boolean {
        val popup = activePopup ?: return false
        if (!popup.isVisible) return false

        @Suppress("UNCHECKED_CAST")
        val list = UIUtil.findComponentOfType(popup.content, JList::class.java) as? JList<SuggestionItem> ?: return false

        when (e.keyCode) {
            KeyEvent.VK_DOWN -> {
                list.selectedIndex = (list.selectedIndex + 1).coerceAtMost(list.model.size - 1)
                list.ensureIndexIsVisible(list.selectedIndex)
                return true
            }
            KeyEvent.VK_UP -> {
                list.selectedIndex = (list.selectedIndex - 1).coerceAtLeast(0)
                list.ensureIndexIsVisible(list.selectedIndex)
                return true
            }
            KeyEvent.VK_ENTER -> {
                list.selectedValue?.let {
                    insertItemAtCaret(if (it.isTask) "🆔 ${it.taskId}" else it.text, it.isTask)
                    popup.cancel()
                }
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
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_NORMAL, JBColor(0xF2F2F2, 0x1E1F22))
                newTaskButton.text = TodosoBundle.message(NEW_TASK_BUTTON)
                cancelButton.isVisible = false
            }
            is InputMode.Edit -> {
                inputTextArea.text = initialText
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_EDIT, JBColor(0xE6F2FF, 0x2D3548))
                newTaskButton.text = TodosoBundle.message(UPDATE_BUTTON)
                cancelButton.isVisible = true
            }
            is InputMode.Cancel -> {
                inputTextArea.text = initialText
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_CANCEL, JBColor(0xFFE6E6, 0x482D2D))
                newTaskButton.text = TodosoBundle.message(UPDATE_BUTTON)
                cancelButton.isVisible = true
            }
            is InputMode.Note -> {
                inputTextArea.text = if (initialText.isBlank()) "// " else ensureNotePrefix(initialText)
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_EDIT, JBColor(0xE6F2FF, 0x2D3548))
                newTaskButton.text = TodosoBundle.message(UPDATE_BUTTON)
                cancelButton.isVisible = true
            }
        }
        if (mode !is InputMode.Normal) inputTextArea.requestFocusInWindow()
        updateActionButtons()
        repaint()
    }

    fun setEditMode(enabled: Boolean, text: String = "") = setMode(if (enabled) InputMode.Edit(text) else InputMode.Normal, text)
    fun setNoteMode(enabled: Boolean, text: String = "") = setMode(if (enabled) InputMode.Note else InputMode.Normal, text)
    fun setCancelMode(enabled: Boolean, text: String = "") = setMode(if (enabled) InputMode.Cancel else InputMode.Normal, text)

    private fun ensureNotePrefix(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.startsWith("//")) trimmed else "// $trimmed"
    }

    private fun updateActionButtons() {
        val currentText = inputTextArea.text.trim()
        newTaskButton.isEnabled = when (val mode = currentMode) {
            is InputMode.Note -> currentText.removePrefix("//").trim().isNotEmpty()
            is InputMode.Edit -> isInputValid(currentText) && currentText != mode.originalText.trim()
            else -> isInputValid(currentText)
        }
    }

    internal fun isInputValid(text: String): Boolean {
        if (text.isEmpty()) return false
        return !Regex("""^- \[[ x/-]]\s*$""").matches(text)
    }

    fun clearInputText() = setMode(InputMode.Normal)

    private fun showSuggestionsPopup(triggerChar: Char) {
        activePopup?.cancel()
        val prefix = suggestionService.getActivePrefix(inputTextArea.text, inputTextArea.caretPosition) ?: ""

        val items = runReadAction {
            if (triggerChar == '#') {
                suggestionService.getSuggestions(prefix, getPopularTags(), getAllTasks())
            } else emptyList()
        }

        if (items.isEmpty()) return

        val renderer = object : GroupedItemsListRenderer<SuggestionItem>(object : ListItemDescriptorAdapter<SuggestionItem>() {
            override fun getTextFor(value: SuggestionItem) = if (value.isTask) value.text else "#${value.text}"
            override fun getIconFor(value: SuggestionItem) = value.icon
            override fun getCaptionAboveOf(value: SuggestionItem): String? {
                val index = items.indexOf(value)
                return if (index == 0 || items[index - 1].category != value.category) value.category else null
            }
            override fun hasSeparatorAboveOf(value: SuggestionItem) = getCaptionAboveOf(value) != null
        }) {}

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setRenderer(renderer)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(false)
            .setItemChosenCallback { insertItemAtCaret(if (it.isTask) "🆔 ${it.taskId}" else it.text, it.isTask) }
            .addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) { activePopup = null }
            })
            .createPopup()

        activePopup = popup
        popup.size = Dimension(inputWrapper.width, popup.content.preferredSize.height.coerceAtMost(300))
        val loc = inputWrapper.locationOnScreen
        popup.showInScreenCoordinates(inputWrapper, Point(loc.x, loc.y - popup.size.height))
    }

    internal fun insertItemAtCaret(content: String, isTask: Boolean = false) {
        val doc = inputTextArea.document
        val caretPos = inputTextArea.caretPosition
        val text = inputTextArea.text

        val lastHash = text.substring(0, caretPos).lastIndexOf('#')
        if (lastHash != -1) {
            val startReplace = if (isTask) lastHash else lastHash + 1
            doc.remove(startReplace, caretPos - startReplace)
            doc.insertString(startReplace, "$content ", null)
        } else {
            doc.insertString(caretPos, "$content ", null)
        }
        inputTextArea.requestFocusInWindow()
    }

    internal fun shouldTriggerPopup(): Boolean {
        val caretPos = inputTextArea.caretPosition
        val text = inputTextArea.text
        if (text.isEmpty()) return true
        return (if (caretPos > 0) text[caretPos - 1] else ' ').isWhitespace()
    }
}
