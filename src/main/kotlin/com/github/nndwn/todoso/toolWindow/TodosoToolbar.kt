package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import javax.swing.JComponent

class TodosoToolbar(
    private val settings: TodosoSettingsService,
    private val targetComponent: JComponent,
    private val onRefresh: () -> Unit,
    private val onRandomTask: () -> Unit,
    private val onToggleVisualMode: () -> Unit,
    private val onSortChanged: (SortOption) -> Unit
) {

    enum class SortOption {
        DEFAULT,
        PRIORITY,
        STATUS,
        DATE
    }

    private var currentSort: SortOption = SortOption.DEFAULT
    fun createComponent(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(createRefreshAction())
            add(createRandomTaskAction())
            addSeparator()
            add(createSortActionGroup())
            addSeparator()
            add(createVisualModeToggleAction())
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
        }

    private fun createSortToggleAction(label: String, option: SortOption): ToggleAction {
        return object : ToggleAction(label) {
            override fun isSelected(e: AnActionEvent): Boolean {
                return currentSort == option
            }

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (state) {
                    currentSort = option
                    onSortChanged(option)
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }
        }
    }
    private fun createSortActionGroup(): ActionGroup {
        val sortGroup = DefaultActionGroup().apply {
            add(createSectionHeader("Sort"))

            add(createSortToggleAction("Default", SortOption.DEFAULT))
            add(createSortToggleAction("By Priority", SortOption.PRIORITY))
            add(createSortToggleAction("By Status", SortOption.STATUS))
        }

        return object : DefaultActionGroup("Sort Tasks", true) {
            init {
                templatePresentation.icon = AllIcons.Actions.ListChanges
                templatePresentation.text = "Sort Tasks"
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return sortGroup.getChildren(e)
            }
        }
    }

    private fun createSectionHeader(title: String): AnAction =
        object : AnAction(title) {
            override fun actionPerformed(e: AnActionEvent) {}
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = false // Mencegah item diklik & berwarna abu-abu
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

}