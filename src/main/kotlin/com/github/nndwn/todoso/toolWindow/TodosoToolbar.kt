package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

class TodosoToolbar(
    private val settings: TodosoSettingsService,
    private val targetComponent: JComponent,
    private val onRefresh: () -> Unit,
    private val onRandomTask: () -> Unit,
    private val onToggleVisualMode: () -> Unit,
    private val onErrorHandler : (String)-> Unit,
    private val onSortChanged: (Set<SortOption>) -> Unit
) {

    enum class SortOption(val key: String) {
        PRIORITY("PRIORITY"),
        STATUS("STATUS"),
        DATE("DATE");

        companion object {
            fun fromKey(key: String): SortOption? = entries.find { it.key == key }
        }
    }

    private val currentSort: MutableSet<SortOption> = settings.state.sortOption
        .split(",")
        .mapNotNull { SortOption.fromKey(it.trim()) }
        .toMutableSet()

    fun createComponent(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(createRefreshAction())
            add(createSelectFileAction())
            add(createRandomTaskAction())
            addSeparator()
            add(createViewOptionsActionGroup())
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("TodoToolbar", actionGroup, true)

        toolbar.targetComponent = targetComponent
        return toolbar.component
    }

    private fun createRefreshAction(): AnAction =
        object : AnAction(
            TodosoBundle.message("todo.menu.refresh"),
            TodosoBundle.message("todo.action.refresh.desc"),
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(e: AnActionEvent) = onRefresh()
        }
    private fun createRandomTaskAction(): AnAction =
        object : AnAction(
            TodosoBundle.message("todo.menu.random"),
            TodosoBundle.message("todo.action.random.desc"),
            AllIcons.Actions.Lightning,
        ) {
            override fun actionPerformed(e: AnActionEvent) = onRandomTask()
            override fun update(e: AnActionEvent) {
                val project = e.project ?: return
                val service = project.service<TodosoService>()
                val hasTodoTasks = service.loadTask().any { it.status == TaskStatus.TODO }
                e.presentation.isEnabled = hasTodoTasks
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }

    private fun createViewOptionsActionGroup(): ActionGroup {
        val group = DefaultActionGroup().apply {
            addSeparator("Group By")
            add(createDefaultSortToggleAction())
            add(createSortToggleAction(TodosoBundle.message("todo.sort.by.priority"), SortOption.PRIORITY))
            add(createSortToggleAction(TodosoBundle.message("todo.sort.by.status"), SortOption.STATUS))
            add(createSortToggleAction(TodosoBundle.message("todo.sort.by.date"), SortOption.DATE))

            addSeparator("View Color")
            add(createVisualModeToggleAction())
        }

        return object : DefaultActionGroup("View Options", true) {
            init {
                templatePresentation.icon = AllIcons.Actions.Show
                templatePresentation.text = "View Options"
            }
            override fun update(e: AnActionEvent) {
                val project = e.project ?: return
                val service = project.service<TodosoService>()

                e.presentation.isEnabled = service.loadTask().isNotEmpty()
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> = group.getChildren(e)
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
    }

    private fun createDefaultSortToggleAction(): ToggleAction {
        return object : ToggleAction(TodosoBundle.message("todo.common.default")) {
            override fun isSelected(e: AnActionEvent): Boolean = currentSort.isEmpty()

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (state) {
                    currentSort.clear()
                    settings.state.sortOption = ""
                    onSortChanged(emptySet())
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
    }

    private fun createSortToggleAction(label: String, option: SortOption): ToggleAction {
        return object : ToggleAction(label) {
            override fun isSelected(e: AnActionEvent): Boolean = currentSort.contains(option)

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (state) {
                    currentSort.add(option)
                } else {
                    currentSort.remove(option)
                }
                settings.state.sortOption = currentSort.joinToString(",") { it.key }
                onSortChanged(currentSort.toSet())
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
    }


    private fun createVisualModeToggleAction(): ToggleAction =
        object : ToggleAction(
            TodosoBundle.message("todo.menu.visual.mode"),
            TodosoBundle.message("todo.action.visual.mode.desc"),
            AllIcons.Actions.Show,
        ) {
            override fun isSelected(e: AnActionEvent): Boolean = settings.state.visualEnabled

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.state.visualEnabled = state
                onToggleVisualMode()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }

    private fun createSelectFileAction(): AnAction =
        object : AnAction(
            TodosoBundle.message("todo.open.file"),
            TodosoBundle.message("todo.open.file.desc"),
            AllIcons.Actions.MenuOpen
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("md")
                    .withTitle(TodosoBundle.message("todo.open.file"))
                    .withDescription(TodosoBundle.message("todo.open.file.desc"))

                val selectedFile: VirtualFile? = FileChooser.chooseFile(descriptor, project, null)

                if (selectedFile != null) {

                    if (!selectedFile.isValid) {

                        onErrorHandler(TodosoBundle.message("todo.action.file.error.message",selectedFile))
                        return
                    }
                    val projectDir = project.guessProjectDir()
                    val pathToSave = if (projectDir != null && VfsUtilCore.isAncestor(projectDir, selectedFile, false)) {
                        VfsUtilCore.getRelativePath(selectedFile, projectDir) ?: selectedFile.path
                    } else {
                        selectedFile.path
                    }

                    val settings = TodosoSettingsService.getInstance(project)
                    settings.state.todoFilePath = pathToSave
                    onRefresh()
                }
            }
        }

}