package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class TodosoMainPanel(
    private val project: Project,
    private val service: TodoService = project.service(),
    private val settings: TodosoSettingsService = TodosoSettingsService.getInstance(project)
) : JPanel(BorderLayout()) {

    companion object {
        private const val CARD_INSTRUCTION = "EMPTY_STATE"
        private const val CARD_TASK_LIST = "TASK_LIST"
        private const val HTML = "text/html"
    }

    private val cardLayout = CardLayout()
    private val centerContainer = JPanel(cardLayout)

    private val listModel = CollectionListModel<TodoTask>()
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        emptyText.text = TodosoBundle.message("todo.list.empty")
        cellRenderer = TodosoCell(service, settings)
    }

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

    private val toolbarPanel = TodosoToolbar(
        settings = settings,
        targetComponent = list,
        onRefresh = { refreshUiState() },
        onRandomTask = { /* panggil handler random task */ },
        onToggleVisualMode = { list.repaint() }
    )

    init {
        val toolbarComponent = toolbarPanel.createComponent()
        add(toolbarComponent, BorderLayout.NORTH)

        centerContainer.add(instructionScrollPane, CARD_INSTRUCTION)
        centerContainer.add(JBScrollPane(list), CARD_TASK_LIST)
        service.injectInstructionsIfNeeded()
        add(centerContainer, BorderLayout.CENTER)

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
}