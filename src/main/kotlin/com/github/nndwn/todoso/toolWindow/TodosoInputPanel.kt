package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.domain.model.TodoTask
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

/**
 * State visual & mode input untuk TodosoInputPanel
 */
sealed class InputMode {
    object Normal : InputMode()
    data class Edit(val originalText: String) : InputMode()
    object Cancel : InputMode()
    object Note : InputMode()
}

/**
 * Representasi item dalam popup saran (Tags atau Files)
 */
data class SuggestionItem(
    val text: String,
    val category: String,
    val icon: Icon? = null,
    val subText: String? = null,
    val isTask: Boolean = false,
    val taskId: String? = null
)

/**
 * Metadata file proyek untuk saran
 */
// data class FileInfo(val name: String, val path: String) // Dihapus sementara

class TodosoInputPanel(
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
        private const val BACKGROUND_COLOR_INPUT_EDIT = "Todo.Input.EditBackground"
        private const val BACKGROUND_COLOR_INPUT_CANCEL = "Todo.Input.CancelBackground"
        private const val BACKGROUND_COLOR_INPUT = "Todo.Input.Background"
    }

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

    private val newTaskButton = JButton(TodosoBundle.message(NEW_TASK_BUTTON)).apply {
        addActionListener { handleMainAction() }
    }

    private val cancelButton = JButton(TodosoBundle.message(EDIT_BUTTON)).apply {
        isVisible = false
        addActionListener {
            onCancelEdit()
            inputTextArea.requestFocusInWindow()
        }
    }

    private lateinit var inputWrapper: JComponent
    private var activePopup: JBPopup? = null

    init {
        setupUI()
        setupListeners()
        updateActionButtons()
    }

    private fun setupUI() {
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
        background = JBUI.CurrentTheme.ToolWindow.background()

        inputWrapper = createInputWrapper()
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

    private fun createInputWrapper() = object : JBPanel<JBPanel<*>>(BorderLayout()) {
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
    }.apply {
        isOpaque = false
        border = JBUI.Borders.empty(2)
        add(JBScrollPane(inputTextArea).apply {
            border = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = false
        }, BorderLayout.CENTER)
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
                        if (getActivePrefix() != null) showSuggestionsPopup('#')
                        else activePopup?.cancel()
                    }
                }
            }

            override fun keyPressed(e: KeyEvent) {
                val popup = activePopup
                if (popup != null && popup.isVisible) {
                    @Suppress("UNCHECKED_CAST")
                    (UIUtil.findComponentOfType(popup.content, JList::class.java) as? JList<SuggestionItem>)?.let { list ->
                        when (e.keyCode) {
                            KeyEvent.VK_DOWN -> {
                                val next = (list.selectedIndex + 1).coerceAtMost(list.model.size - 1)
                                list.selectedIndex = next
                                list.ensureIndexIsVisible(next)
                                e.consume()
                                return
                            }
                            KeyEvent.VK_UP -> {
                                val prev = (list.selectedIndex - 1).coerceAtLeast(0)
                                list.selectedIndex = prev
                                list.ensureIndexIsVisible(prev)
                                e.consume()
                                return
                            }
                            KeyEvent.VK_ENTER -> {
                                val selected = list.selectedValue
                                if (selected != null) {
                                    insertItemAtCaret(if (selected.isTask) "🆔 ${selected.taskId}" else selected.text, selected.isTask)
                                    popup.cancel()
                                    e.consume()
                                    return
                                }
                            }
                        }
                    }
                }

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
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_INPUT, JBColor(0xF2F2F2, 0x1E1F22))
                newTaskButton.text = TodosoBundle.message(NEW_TASK_BUTTON)
                cancelButton.isVisible = false
            }
            is InputMode.Edit -> {
                inputTextArea.text = initialText
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_INPUT_EDIT, JBColor(0xE6F2FF, 0x2D3548))
                newTaskButton.text = TodosoBundle.message(UPDATE_BUTTON)
                cancelButton.isVisible = true
            }
            is InputMode.Cancel -> {
                inputTextArea.text = initialText
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_INPUT_CANCEL, JBColor(0xFFE6E6, 0x482D2D))
                newTaskButton.text = TodosoBundle.message(UPDATE_BUTTON)
                cancelButton.isVisible = true
            }
            is InputMode.Note -> {
                inputTextArea.text = if (initialText.isBlank()) "// " else ensureNotePrefix(initialText)
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_INPUT_EDIT, JBColor(0xE6F2FF, 0x2D3548))
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

    private fun isInputValid(text: String): Boolean {
        if (text.isEmpty()) return false
        return !Regex("""^- \[[ x/-]]\s*$""").matches(text)
    }

    fun clearInputText() = setMode(InputMode.Normal)

    private fun showSuggestionsPopup(triggerChar: Char) {
        activePopup?.cancel()
        val prefix = getActivePrefix() ?: ""

        val items = runReadAction {
            if (triggerChar == '#') {
                val suggestions = mutableListOf<SuggestionItem>()
                val popularTags = getPopularTags()
                
                // 1. Tag Suggestions
                suggestions.addAll(
                    popularTags
                        .filter { it.startsWith(prefix, ignoreCase = true) }
                        .map { SuggestionItem(it, "Popular Tags", IconLoader.getIcon("/actions/checked.png", javaClass)) }
                )

                // 2. Task Suggestions jika tag lengkap
                if (prefix.isNotEmpty()) {
                    val relatedTasks = getAllTasks().filter { task ->
                        task.tags.any { it.equals(prefix, ignoreCase = true) }
                    }.sortedByDescending { it.metadata.createdDate ?: "" }

                    if (relatedTasks.isNotEmpty()) {
                        suggestions.addAll(
                            relatedTasks.map { task ->
                                SuggestionItem(
                                    text = task.description.take(40) + (if (task.description.length > 40) "..." else ""),
                                    category = "Related Tasks",
                                    icon = IconLoader.getIcon("/nodes/variable.png", javaClass),
                                    isTask = true,
                                    taskId = task.id
                                )
                            }
                        )
                    }
                }
                suggestions
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
        val fieldWidth = inputWrapper.width
        val preferredHeight = popup.content.preferredSize.height.coerceAtMost(300)
        popup.size = Dimension(fieldWidth, preferredHeight)

        val loc = inputWrapper.locationOnScreen
        popup.showInScreenCoordinates(inputWrapper, Point(loc.x, loc.y - popup.size.height))
    }

    private fun insertItemAtCaret(content: String, isTask: Boolean = false) {
        val doc = inputTextArea.document
        val caretPos = inputTextArea.caretPosition
        val text = inputTextArea.text

        val lastHash = text.substring(0, caretPos).lastIndexOf('#')
        if (lastHash != -1) {
            // Jika yang dipilih adalah TASK, kita hapus '#' nya juga agar diganti murni oleh ID
            val startReplace = if (isTask) lastHash else lastHash + 1
            val lengthReplace = caretPos - startReplace
            
            doc.remove(startReplace, lengthReplace)
            doc.insertString(startReplace, "$content ", null)
        } else {
            doc.insertString(caretPos, "$content ", null)
        }
        inputTextArea.requestFocusInWindow()
    }

    private fun getActivePrefix(): String? {
        val text = inputTextArea.text
        val caretPos = inputTextArea.caretPosition
        if (caretPos <= 0) return null

        val lastHash = text.substring(0, caretPos).lastIndexOf('#')
        if (lastHash == -1) return null

        val sub = text.substring(lastHash + 1, caretPos)
        return if (sub.contains(" ")) null else sub
    }

    private fun shouldTriggerPopup(): Boolean {
        val caretPos = inputTextArea.caretPosition
        val text = inputTextArea.text
        if (text.isEmpty()) return true
        return (if (caretPos > 0) text[caretPos - 1] else ' ').isWhitespace()
    }
}
