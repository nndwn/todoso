package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.inputWindow.TodosoInputPanel
import com.github.nndwn.todoso.toolWindow.inputWindow.components.SuggestionOverlayPanel
import com.github.nndwn.todoso.toolWindow.search.TodosoSearchPanel
import com.github.nndwn.todoso.toolWindow.taskList.TodosoTaskListView
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

object TodosoTestHelper {
    fun createMainPanel(project: Project): TodosoMainPanel {
        val service = project.service<TodosoService>()
        val settings = TodosoSettingsService.getInstance(project)

        return TodosoMainPanel(
            project = project,
            service = service,
            settings = settings,
            toolbarProvider = { panel ->
                TodosoToolbar(
                    settings = settings,
                    targetComponent = panel,
                    onRefreshUI = { panel.refreshUiState() },
                    onRefreshTasks = { panel.handler.refreshTasks() },
                    onRandomTask = { panel.handler.handleRandomTask() },
                    onErrorHandler = { msg -> panel.handler.handleErrorNotification(msg) },
                    onSortChanged = { options -> panel.setCurrentSortOption(options) },
                    filterState = panel.getFilterState(),
                    onFilterChanged = { type, value -> panel.onFilterChanged(type, value) }
                )
            },
            inputPanelProvider = { panel ->
                TodosoInputPanel(
                    project = project,
                    onNewTask = { panel.hideSearchPanel(); panel.handler.handleAddTask(it) },
                    onUpdateTask = { panel.hideSearchPanel(); panel.handler.handleUpdateTask(it) },
                    onConfirmCancel = { panel.hideSearchPanel(); panel.handler.handleConfirmCancel(it) },
                    onCreateNote = { panel.hideSearchPanel(); panel.handler.handleConfirmCancel(it) },
                    onCancelEdit = { panel.handler.handleCancelEdit() },
                    fontInput = panel.uiFont,
                    getPopularTags = { TagParser.getPopularTags(service.loadTask()) },
                    getAllTasks = { service.loadTask() },
                    onSuggestionRequest = { items ->
                        if (items != null) {
                            panel.getSuggestionOverlay().updateItems(items)
                            panel.updateOverlayPosition()
                        } else {
                            panel.getSuggestionOverlay().hideOverlay()
                        }
                    },
                    onNavigationRequest = { direction ->
                        when (direction) {
                            "UP" -> panel.getSuggestionOverlay().moveUp()
                            "DOWN" -> panel.getSuggestionOverlay().moveDown()
                            "ENTER" -> panel.getSuggestionOverlay().confirmSelection()
                            "ESCAPE" -> panel.getSuggestionOverlay().hideOverlay()
                        }
                    }
                )
            },
            taskListViewProvider = { panel ->
                TodosoTaskListView(
                    service = service,
                    settings = settings,
                    onTaskSelected = { task, force -> panel.handleTaskSelection(task, force) },
                    onTaskEdit = { task -> panel.handler.setEditMode(true, task.description) },
                    onContextMenu = { task, e -> panel.showContextMenu(task, e) }
                )
            },
            searchPanelProvider = { panel ->
                TodosoSearchPanel(
                    onQueryChanged = { query -> panel.onSearchQueryChanged(query) }
                )
            },
            suggestionOverlayProvider = { panel ->
                SuggestionOverlayPanel { item ->
                    panel.getInputPanel().insertItemAtCaret(if (item.isTask) "🆔 ${item.taskId}" else item.text, item.isTask)
                }
            }
        )
    }
}
