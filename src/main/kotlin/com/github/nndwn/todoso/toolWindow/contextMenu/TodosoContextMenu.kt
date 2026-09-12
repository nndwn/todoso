package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.TodosoActionHandler
import com.intellij.icons.AllIcons

class TodosoContextMenu(
    private val service : TodosoService,
    private val settings : TodosoSettingsService,
    private val handler : TodosoActionHandler,
) {
    fun build() : List<TodosoMenuElement> {
        return  buildTodosoMenu {
            editorTask()
            separator()
            toolTask()
            separator()
            visualTask()
            separator()
            sortTask()
        }
    }

    private fun TodoMenuBuilder.editorTask() {
        subMenu(TodosoBundle.message("todo.menu.change.status"), AllIcons.Actions.Diff){
            TaskStatus.entries.forEach { status ->

            }
        }
    }
    private fun TodoMenuBuilder.toolTask() {
        //don't implement
    }

    private fun TodoMenuBuilder.visualTask() {
        //don't implement
    }

    private fun TodoMenuBuilder.sortTask() {
        //don't implement
    }
}
