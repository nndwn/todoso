package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodoService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel

class TodosoMainPanel(private val project: Project) : JPanel(BorderLayout()){
    private val cardLayout = CardLayout()
    private val centerContainer = JPanel(cardLayout)

    private val instructionPane = JEditorPane("text/html", getInstructionHtml()).apply {
        isEditable = false
        isOpaque = false
        isFocusable = false
        highlighter = null
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

    // Bungkus JEditorPane dengan JBScrollPane
    private val instructionScrollPane = JBScrollPane(instructionPane).apply {
        border = BorderFactory.createEmptyBorder()
        isFocusable = false
    }

    private val taskListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    init {
        // Panggil variabel instructionScrollPane yang sudah dikonfigurasi
        centerContainer.add(instructionScrollPane, CARD_INSTRUCTION)
        centerContainer.add(JBScrollPane(taskListPanel), CARD_TASK_LIST)

        add(centerContainer, BorderLayout.CENTER)

        // Load data awal dari TodoService
        refreshUiState()
    }

    fun refreshUiState() {
        val todoService = project.service<TodoService>()
        val tasks = todoService.loadTask()

        if (tasks.isEmpty()) {
            cardLayout.show(centerContainer, CARD_INSTRUCTION)
        } else {
            renderTaskList(tasks)
            cardLayout.show(centerContainer, CARD_TASK_LIST)
        }
    }

    private fun renderTaskList(tasks: List<TodoTask>) {
        taskListPanel.removeAll()
        tasks.forEach { task ->
            taskListPanel.add(JLabel("${task.status.code} ${task.description}"))
        }
        taskListPanel.revalidate()
        taskListPanel.repaint()
    }
    companion object {
        private const val CARD_INSTRUCTION = "EMPTY_STATE"
        private const val CARD_TASK_LIST = "TASK_LIST"

        private fun getInstructionHtml(): String = """
            <html>
            <body style="font-family: sans-serif; padding: 12px; color: #BBBBBB;">
                <h3 style="margin-top: 0; color: #FFFFFF;">Welcome to Todoso! 🚀</h3>
                <p>No task file was found or the task list is currently empty. Follow these quick steps to get started:</p>
                <ul>
                    <li>Enter your task description in the field below and click <b>Submit</b>.</li>
                    <li>Right-click on any task to select the required <b>Priority</b>, <b>Tags</b>, or <b>Status</b>.</li>
                    <li>
                        <b>Quick Format:</b> Type directly using this format:<br/>
                        <code style="background-color: #2B2D30; color: #A9B7C6; padding: 2px 4px;">[H] task description #tags</code>
                    </li>
                </ul>
                <p>For more detailed documentation, read here:<br/>
                   <a href="https://github.com/nndwn/todoso">https://github.com/nndwn/todoso</a>
                </p>
            </body>
            </html>
        """.trimIndent()
    }
}