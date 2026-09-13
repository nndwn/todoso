package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.TodosoActionHandler
import com.github.nndwn.todoso.toolWindow.SortOption
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
        val selected = handler.getSelectedTask() ?: return

        item(
            text = TodosoBundle.message("todo.menu.edit.task"),
            icon = AllIcons.Actions.Edit,
            onAction = { handler.setEditMode(true, selected.description) }
        )

        subMenu(TodosoBundle.message("todo.menu.change.status"), AllIcons.Actions.Diff){
            TaskStatus.entries.forEach { status ->
                item(
                    text = status.displayName,
                    icon = status.icon,
                    isEnabled = { handler.canTransitionTo(selected, status) }
                ) {
                    handler.updateTaskStatus(selected, status)
                }
            }
        }

        subMenu(TodosoBundle.message("todo.menu.change.priority"), AllIcons.General.Filter) {
            Priority.entries.forEach { priority ->
                item(
                    text = priority.displayName,
                    icon = priority.icon,
                    isEnabled = { selected.priority != priority },
                    onAction = { handler.handleUpdatePriority(selected, priority) }
                )
            }
        }

        separator()

        item(
            text = TodosoBundle.message("todo.menu.copy.context"),
            icon = AllIcons.Actions.Copy,
            onAction = { handler.handleCopyContext() }
        )

        item(
            text = TodosoBundle.message("todo.menu.delete"),
            icon = AllIcons.Actions.GC,
            onAction = { handler.handleDeleteAction() }
        )
    }
    private fun TodoMenuBuilder.toolTask() {
        item(
            text = TodosoBundle.message("todo.menu.refresh"),
            icon = AllIcons.Actions.Refresh,
            onAction = { handler.refreshTasks() }
        )
        item(
            text = TodosoBundle.message("todo.menu.random"),
            icon = AllIcons.Actions.Lightning,
            onAction = { handler.handleRandomTask() }
        )
        item(
            text = "Navigate to Line",
            icon = AllIcons.Actions.MenuOpen,
            onAction = { handler.getSelectedTask()?.let { handler.handleNavigateToTask(it) } }
        )
        item(
            text = TodosoBundle.message("todo.filter.search"),
            icon = AllIcons.Actions.Find,
            onAction = { handler.handleToggleSearch() }
        )
    }

    private fun TodoMenuBuilder.visualTask() {
        toggle(
            text = TodosoBundle.message("todo.view.visual.mode"),
            icon = AllIcons.Actions.Show,
            isSelected = { settings.state.visualEnabled },
            onToggle = { settings.state.visualEnabled = it; handler.refreshTasks() }
        )
    }

    private fun TodoMenuBuilder.sortTask() {
        subMenu(TodosoBundle.message("todo.common.sort"), AllIcons.Actions.GroupBy) {
            item(
                text = TodosoBundle.message("todo.common.default"),
                onAction = { 
                    settings.state.sortOption = ""
                    handler.refreshTasks() 
                }
            )
            separator()
            SortOption.entries.forEach { option ->
                val label = when(option) {
                    SortOption.PRIORITY -> TodosoBundle.message("todo.sort.by.priority")
                    SortOption.STATUS -> TodosoBundle.message("todo.sort.by.status")
                    SortOption.DATE -> TodosoBundle.message("todo.sort.by.date")
                }
                toggle(
                    text = label,
                    isSelected = { settings.state.sortOption.split(",").contains(option.key) },
                    onToggle = { active ->
                        val current = settings.state.sortOption.split(",").filter { it.isNotBlank() }.toMutableList()
                        if (active) current.add(option.key) else current.remove(option.key)
                        settings.state.sortOption = current.distinct().joinToString(",")
                        handler.refreshTasks()
                    }
                )
            }
        }
    }
}
