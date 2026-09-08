package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
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
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JButton
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
    val subText: String? = null
)

class TodosoInputPanel(
    private val project: Project,
    val onNewTask: (String) -> Unit,
    val onUpdateTask: (String) -> Unit,
    val onConfirmCancel: (String) -> Unit,
    val onCreateNote: (String) -> Unit,
    val onCancelEdit: () -> Unit,
    val fontInput: Font,
    val getPopularTags: () -> List<String>
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

    val newTaskButton = JButton(TodosoBundle.message(NEW_TASK_BUTTON)).apply {
        addActionListener {
            val text = inputTextArea.text
            when (currentMode) {
                is InputMode.Edit -> onUpdateTask(text)
                is InputMode.Cancel -> onConfirmCancel(text)
                is InputMode.Note -> onCreateNote(ensureNotePrefix(text))
                is InputMode.Normal -> onNewTask(text)
            }
            inputTextArea.requestFocusInWindow()
        }
    }

    val cancelButton = JButton(TodosoBundle.message(EDIT_BUTTON)).apply {
        isVisible = false
        addActionListener {
            onCancelEdit()
            inputTextArea.requestFocusInWindow()
        }
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
                inputTextArea.requestFocusInWindow()
            }

            is InputMode.Cancel -> {
                inputTextArea.text = initialText
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_INPUT_CANCEL, JBColor(0xFFE6E6, 0x482D2D))
                newTaskButton.text = TodosoBundle.message(UPDATE_BUTTON)
                cancelButton.isVisible = true
                inputTextArea.requestFocusInWindow()
            }

            is InputMode.Note -> {
                val textWithPrefix = if (initialText.isBlank()) "// " else ensureNotePrefix(initialText)
                inputTextArea.text = textWithPrefix
                inputTextArea.background = JBColor.namedColor(BACKGROUND_COLOR_INPUT_EDIT, JBColor(0xE6F2FF, 0x2D3548))
                newTaskButton.text = TodosoBundle.message(UPDATE_BUTTON)
                cancelButton.isVisible = true
                inputTextArea.requestFocusInWindow()
            }
        }

        updateActionButtons()
        repaint()
    }

    fun setEditMode(enabled: Boolean, text: String = "") {
        setMode(if (enabled) InputMode.Edit(text) else InputMode.Normal, text)
    }

    fun setNoteMode(enabled: Boolean, text: String = "") {
        setMode(if (enabled) InputMode.Note else InputMode.Normal, text)
    }

    fun setCancelMode(enabled: Boolean, text: String = "") {
        setMode(if (enabled) InputMode.Cancel else InputMode.Normal, text)
    }

    private fun ensureNotePrefix(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.startsWith("//")) {
            trimmed
        } else {
            "// $trimmed"
        }
    }

    private fun updateActionButtons() {
        val currentText = inputTextArea.text.trim()

        when (val mode = currentMode) {
            is InputMode.Note -> {
                val cleanNoteContent = currentText.removePrefix("//").trim()
                newTaskButton.isEnabled = cleanNoteContent.isNotEmpty()
            }

            is InputMode.Edit -> {
                val hasMeaningfulContent = isInputValid(currentText)
                newTaskButton.isEnabled = hasMeaningfulContent && currentText != mode.originalText.trim()
            }

            else -> {
                newTaskButton.isEnabled = isInputValid(currentText)
            }
        }
    }

    private fun isInputValid(text: String): Boolean {
        if (text.isEmpty()) return false
        val prefixOnlyRegex = Regex("""^- \[[ x/-]]\s*$""")
        return !prefixOnlyRegex.matches(text)
    }

    fun clearInputText() {
        setMode(InputMode.Normal)
    }

    private fun showSuggestionsPopup(triggerChar: Char) {
        val items = runReadAction {
            when (triggerChar) {
                '#' -> getPopularTags().map { SuggestionItem(it, "Popular Tags", IconLoader.getIcon("/actions/checked.png", javaClass)) }
                '@' -> getProjectFiles().take(15).map { SuggestionItem(it.name, "Files", IconLoader.getIcon("/nodes/ppFile.png", javaClass), it.path) }
                else -> emptyList()
            }
        }

        if (items.isEmpty()) return

        val caretPos = inputTextArea.caretPosition
        val caretRect = try {
            inputTextArea.modelToView2D(caretPos)?.bounds
        } catch (_: Exception) {
            null
        } ?: return

        val renderer = object : GroupedItemsListRenderer<SuggestionItem>(object : ListItemDescriptorAdapter<SuggestionItem>() {
            override fun getTextFor(value: SuggestionItem) = value.text
            override fun getIconFor(value: SuggestionItem) = value.icon
            override fun getCaptionAboveOf(value: SuggestionItem): String? {
                val index = items.indexOf(value)
                if (index == 0) return value.category
                if (items[index - 1].category != value.category) return value.category
                return null
            }
            override fun hasSeparatorAboveOf(value: SuggestionItem): Boolean {
                return getCaptionAboveOf(value) != null
            }
        }) {
            override fun customizeComponent(list: JList<out SuggestionItem>?, value: SuggestionItem, isSelected: Boolean) {
                super.customizeComponent(list, value, isSelected)
                // Kita bisa menambahkan sub-teks (path) jika diperlukan di masa depan
            }
        }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setRenderer(renderer)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .setItemChosenCallback { selectedItem ->
                insertItemAtCaret(selectedItem.text)
            }
            .createPopup()

        val locationOnScreen = inputTextArea.locationOnScreen
        val popupPoint = Point(
            locationOnScreen.x + caretRect.x,
            locationOnScreen.y + caretRect.y + caretRect.height + 2
        )
        popup.showInScreenCoordinates(inputTextArea, popupPoint)
    }

    private data class FileInfo(val name: String, val path: String)

    private fun getProjectFiles(): List<FileInfo> {
        val files = mutableListOf<FileInfo>()
        val scope = GlobalSearchScope.projectScope(project)

        FilenameIndex.getAllFilenames(project).forEach { fileName ->
            if (fileName.endsWith(".kt") || fileName.endsWith(".java") || fileName.endsWith(".md")) {
                FilenameIndex.getVirtualFilesByName(fileName, scope).forEach{ file ->
                    files.add(FileInfo(file.name, file.path))
                }
            }
        }
        return files.distinctBy { it.path }.sortedBy { it.name }
    }

    private fun insertItemAtCaret(text: String) {
        val doc = inputTextArea.document
        val caretPos = inputTextArea.caretPosition

        // Memasukkan teks dengan spasi di akhir
        doc.insertString(caretPos, "$text ", null)
        inputTextArea.requestFocusInWindow()
    }

    private fun shouldTriggerPopup(): Boolean {
        val caretPos = inputTextArea.caretPosition
        val text = inputTextArea.text
        if (text.isEmpty()) return true

        val charBefore = if (caretPos > 0) text[caretPos - 1] else ' '
        return charBefore.isWhitespace()
    }

    init {
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
        background = JBUI.CurrentTheme.ToolWindow.background()

        inputTextArea.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) = this@TodosoInputPanel.repaint()
            override fun focusLost(e: FocusEvent?) = this@TodosoInputPanel.repaint()
        })

        inputTextArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateActionButtons()
            }
        })

        val inputWrapper = object : JBPanel<JBPanel<*>>(BorderLayout()) {
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
        }

        val inputScrollPane = JBScrollPane(inputTextArea).apply {
            border = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = false
        }

        inputWrapper.add(inputScrollPane, BorderLayout.CENTER)

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

        inputTextArea.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                val char = e.keyChar
                if ((char == '#' || char == '@') && shouldTriggerPopup()) {
                    SwingUtilities.invokeLater {
                        showSuggestionsPopup(char)
                    }
                }
            }
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE && currentMode !is InputMode.Normal) {
                    onCancelEdit()
                }
                
                // Submit dengan Enter (Shift+Enter untuk baris baru)
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    if (newTaskButton.isEnabled) {
                        newTaskButton.doClick()
                    }
                }
            }
        })

        add(marginWrapper, BorderLayout.CENTER)
        add(buttonsPanel, BorderLayout.SOUTH)

        updateActionButtons()
    }
}
