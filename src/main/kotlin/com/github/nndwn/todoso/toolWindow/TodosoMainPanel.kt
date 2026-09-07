package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class TodosoMainPanel(
    private val project: Project,
    private val service: TodosoService = project.service(),
    private val settings: TodosoSettingsService = TodosoSettingsService.getInstance(project)
) : JPanel(BorderLayout()) , TodosoActionHandler.TodoViewActions {

    companion object {
        private const val CARD_INSTRUCTION = "EMPTY_STATE"
        private const val CARD_TASK_LIST = "TASK_LIST"
        private const val HTML = "text/html"
    }

    val editorFont = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)

    private val handler = TodosoActionHandler(project, service, this)

    private var currentSortOption: TodosoToolbar.SortOption = TodosoToolbar.SortOption.DEFAULT

    private val cardLayout = CardLayout()
    private val centerContainer = JPanel(cardLayout)
    private val instructionPane = JEditorPane(HTML, TodosoConstants.getInstructionHtml()).apply {
        isEditable = false
        isOpaque = false
        isFocusable = false
        highlighter = null
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

    private val instructionScrollPane = JBScrollPane(instructionPane).apply {
        border = BorderFactory.createEmptyBorder()
        isFocusable = false
    }
    private val listModel = CollectionListModel<TodoTask>()
    private val list = JBList(listModel).apply {
        font = editorFont.deriveFont(12f)
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        emptyText.text = TodosoBundle.message("todo.list.empty")
        cellRenderer = TodosoCell(service, settings)
    }

    private val toolbarPanel = TodosoToolbar(
        settings = settings,
        targetComponent = list,
        onRefresh = { refreshUiState() },
        onRandomTask = { handler.handleRandomTask() },
        onToggleVisualMode = { list.repaint() },
        onSortChanged = { sortOption ->
            currentSortOption = sortOption
            refreshTasks() }
    )

    private val inputPanel = TodosoInputPanel(
        onNewTask = { text -> handler.handleAddTask(text) },
        onUpdateTask = { text -> handler.handleUpdateTask(text) },
        onConfirmCancel = { note -> handler.handleConfirmCancel(note) },
        onCreateNote = { note -> handler.handleConfirmCancel(note) },
        onCancelEdit = { handler.handleCancelEdit() },
        fontInput = editorFont
    )

    init {
        val toolbarComponent = toolbarPanel.createComponent()
        add(toolbarComponent, BorderLayout.NORTH)

        centerContainer.add(instructionScrollPane, CARD_INSTRUCTION)
        centerContainer.add(JBScrollPane(list), CARD_TASK_LIST)
        add(centerContainer, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)

        service.injectInstructionsIfNeeded()
        refreshUiState()
    }

    fun refreshUiState() {
        val tasks = service.loadTask()

        if (tasks.isEmpty()) {
            listModel.removeAll()
            cardLayout.show(centerContainer, CARD_INSTRUCTION)
        } else {

            listModel.replaceAll(tasks)
            cardLayout.show(centerContainer, CARD_TASK_LIST)
        }
    }
    private fun applySorting(tasks: List<TodoTask>, option: TodosoToolbar.SortOption): List<TodoTask> {
        return when (option) {
            TodosoToolbar.SortOption.DEFAULT -> tasks
            TodosoToolbar.SortOption.PRIORITY -> tasks.sortedBy { it.priority }
            TodosoToolbar.SortOption.STATUS -> tasks.sortedBy { it.status }
            TodosoToolbar.SortOption.DATE -> tasks.sortedBy { task ->
                task.metadata.dueDate ?: task.metadata.startDate ?: "9999-99-99"
            }
        }
    }

    override fun refreshTasks() {
        refreshUiState()
    }

    override fun setEditMode(enabled: Boolean, text: String) {
        inputPanel.setEditMode(enabled, text)
    }

    override fun setCancelMode(enabled: Boolean) {
        inputPanel.setCancelMode(enabled)
    }

    override fun getSelectedTask(): TodoTask? = list.selectedValue

    override fun getInputText(): String = inputPanel.inputTextArea.text

    override fun clearInputText() {
        inputPanel.clearInputText()
    }

    override fun updateButtonStates() {
        // Callback untuk update state tombol jika ada dependensi eksternal
    }

    override fun setPriorityFilter(priority: Priority?) {}
    override fun setStatusFilter(status: TaskStatus?) {}
    override fun setTagFilter(tag: String?) {}
}