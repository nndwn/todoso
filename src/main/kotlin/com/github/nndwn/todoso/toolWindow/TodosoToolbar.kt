package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoIcons
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
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
  private val onRefreshUI: () -> Unit,
  private val onRefreshTasks: () -> Unit,
  private val onRandomTask: () -> Unit,
  private val onErrorHandler: (String) -> Unit,
  private val onSortChanged: (Set<SortOption>) -> Unit,
  private val filterState: FilterState,
  private val onFilterChanged: (FilterType, Any?) -> Unit,
) {

  data class FilterState(
    var priority: Priority? = null,
    var status: TaskStatus? = null,
    var date: String? = null,
    var tag: String? = null
  )

  enum class FilterType { PRIORITY, STATUS, DATE, TAG, RESET_ALL }

  companion object {
    private const val EXTENSION_MD = "md"
  }

  enum class SortOption(val key: String) {
    PRIORITY("PRIORITY"),
    STATUS("STATUS"),
    DATE("DATE");

    companion object {
      fun fromKey(key: String): SortOption? = entries.find { it.key == key }
    }
  }

  private val currentSort: MutableSet<SortOption> =
    settings.state.sortOption.split(",").mapNotNull { SortOption.fromKey(it.trim()) }.toMutableSet()

  fun createComponent(): JComponent {
    val actionGroup =
      DefaultActionGroup().apply {
        add(createRefreshAction())
        add(createSelectFileAction())
        add(createRandomTaskAction())
        addSeparator()
        add(createFilterActionGroup())
        add(createTagsFilterActionGroup())
        add(createViewOptionsActionGroup())
      }

    val toolbar = ActionManager.getInstance().createActionToolbar("TodoToolbar", actionGroup, true)

    toolbar.targetComponent = targetComponent
    return toolbar.component
  }

  private fun createRefreshAction(): AnAction =
    object :
      AnAction(
        TodosoBundle.message("todo.menu.refresh"),
        TodosoBundle.message("todo.action.refresh.desc"),
        AllIcons.Actions.Refresh,
      ) {
      override fun actionPerformed(e: AnActionEvent) = onRefreshTasks()
    }

  private fun createRandomTaskAction(): AnAction =
    object :
      AnAction(
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
    val group =
      DefaultActionGroup().apply {
        addSeparator(TodosoBundle.message("todo.view.group.by"))
        add(createDefaultSortToggleAction())
        add(createSortToggleAction(TodosoBundle.message("todo.sort.by.priority"), SortOption.PRIORITY))
        add(createSortToggleAction(TodosoBundle.message("todo.sort.by.status"), SortOption.STATUS))
        add(createSortToggleAction(TodosoBundle.message("todo.sort.by.date"), SortOption.DATE))

        addSeparator(TodosoBundle.message("todo.view.color"))
        add(createVisualModeToggleAction())
      }

    return object : DefaultActionGroup(TodosoBundle.message("todo.view.options"), true) {
      init {
        templatePresentation.icon = AllIcons.Actions.Show
        templatePresentation.text = TodosoBundle.message("todo.view.options")
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

  private fun createFilterActionGroup(): ActionGroup {
    return object : DefaultActionGroup("Filters", true) {
      init {
        templatePresentation.icon = AllIcons.General.Filter
      }

      override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TodosoService>()
        e.presentation.isEnabled = service.loadTask().isNotEmpty()
      }

      override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val service = project.service<TodosoService>()
        val tasks = service.loadTask()

        val dynamicGroup = DefaultActionGroup()

        // 0. Global Reset
        val hasActiveFilter = filterState.priority != null || filterState.status != null || 
                             filterState.date != null || filterState.tag != null
        if (hasActiveFilter) {
          dynamicGroup.add(createClearAllAction())
          dynamicGroup.addSeparator()
        }

        // 1. Priority
        dynamicGroup.addSeparator(TodosoBundle.message("todo.view.group.priority"))
        Priority.entries
          .filter { it != Priority.NONE }
          .forEach { dynamicGroup.add(createPriorityFilterAction(it)) }

        // 2. Status
        dynamicGroup.addSeparator(TodosoBundle.message("todo.view.group.status"))
        TaskStatus.entries.forEach { dynamicGroup.add(createStatusFilterAction(it)) }

        // 3. Date (Only show if at least one task has a date)
        val hasAnyDate =
          tasks.any {
            it.metadata.dueDate != null ||
              it.metadata.startDate != null ||
              it.metadata.createdDate != null
          }
        if (hasAnyDate) {
          dynamicGroup.addSeparator(TodosoBundle.message("todo.sort.by.date"))
          dynamicGroup.add(createDateFilterAction("TODAY"))
          dynamicGroup.add(createDateFilterAction("THIS_WEEK"))
          dynamicGroup.add(createDateFilterAction("WITH_DATE"))
        }

        return dynamicGroup.getChildren(e)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun createTagsFilterActionGroup(): ActionGroup {
    return object : DefaultActionGroup("Tags", true) {
      init {
        templatePresentation.icon = AllIcons.Nodes.Tag
      }

      override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TodosoService>()
        e.presentation.isEnabled = service.loadTask().any { it.tags.isNotEmpty() }
      }

      override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val service = project.service<TodosoService>()
        val tasks = service.loadTask()
        
        val dynamicGroup = DefaultActionGroup()
        
        val popularTags = TagParser.getPopularTags(tasks)
        if (popularTags.isNotEmpty()) {
          dynamicGroup.addSeparator(TodosoBundle.message("todo.suggestion.popular.tags"))
          popularTags.forEach { tag ->
            val count = tasks.count { it.tags.contains(tag) }
            dynamicGroup.add(createTagFilterAction(tag, count))
          }
        }
        
        val recentVersions = TagParser.getRecentVersions(tasks = tasks)
        if (recentVersions.isNotEmpty()) {
          dynamicGroup.addSeparator("Versions")
          recentVersions.forEach { tag ->
            val count = tasks.count { it.tags.contains(tag) }
            dynamicGroup.add(createTagFilterAction(tag, count))
          }
        }
        
        return dynamicGroup.getChildren(e)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun createClearAllAction(): AnAction =
    object : AnAction("Clear All Filters", "Reset all active filters", AllIcons.Actions.GC) {
      override fun actionPerformed(e: AnActionEvent) = onFilterChanged(FilterType.RESET_ALL, null)
    }

  private fun createPriorityFilterAction(priority: Priority): ToggleAction {
    val text = priority.label
    return object : ToggleAction(text) {
      override fun isSelected(e: AnActionEvent): Boolean = filterState.priority == priority
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        onFilterChanged(FilterType.PRIORITY, if (state) priority else null)
      }
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun createStatusFilterAction(status: TaskStatus): ToggleAction {
    val text = status.name.lowercase().replaceFirstChar { it.uppercase() }
    return object : ToggleAction(text) {
      override fun isSelected(e: AnActionEvent): Boolean = filterState.status == status
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        onFilterChanged(FilterType.STATUS, if (state) status else null)
      }
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun createDateFilterAction(filter: String): ToggleAction {
    val text = when(filter) {
        "TODAY" -> "Today"
        "THIS_WEEK" -> "This Week"
        "WITH_DATE" -> "Has Date"
        else -> filter
    }
    return object : ToggleAction(text) {
      override fun isSelected(e: AnActionEvent): Boolean = filterState.date == filter
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        onFilterChanged(FilterType.DATE, if (state) filter else null)
      }
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun createTagFilterAction(tag: String, count: Int): ToggleAction {
    val text = "#$tag ($count)"
    return object : ToggleAction(text) {
      override fun isSelected(e: AnActionEvent): Boolean = filterState.tag == tag
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        onFilterChanged(FilterType.TAG, if (state) tag else null)
      }
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
    object :
      ToggleAction(
        TodosoBundle.message("todo.view.visual.mode"),
        TodosoBundle.message("todo.action.visual.mode.desc"),
        AllIcons.Actions.Show,
      ) {
      override fun isSelected(e: AnActionEvent): Boolean = settings.state.visualEnabled

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        settings.state.visualEnabled = state
        onRefreshUI()
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

  private fun createSelectFileAction(): AnAction =
    object :
      AnAction(
        TodosoBundle.message("todo.open.file"),
        TodosoBundle.message("todo.open.file.desc"),
        TodosoIcons.FolderMd,
      ) {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor =
          FileChooserDescriptorFactory.createSingleFileDescriptor(EXTENSION_MD)
            .withTitle(TodosoBundle.message("todo.open.file"))
            .withDescription(TodosoBundle.message("todo.open.file.desc"))

        val selectedFile: VirtualFile? = FileChooser.chooseFile(descriptor, project, null)

        if (selectedFile != null) {

          if (!selectedFile.isValid) {

            onErrorHandler(TodosoBundle.message("todo.action.file.error.message", selectedFile))
            return
          }
          val projectDir = project.guessProjectDir()
          val pathToSave =
            if (projectDir != null && VfsUtilCore.isAncestor(projectDir, selectedFile, false)) {
              VfsUtilCore.getRelativePath(selectedFile, projectDir) ?: selectedFile.path
            } else {
              selectedFile.path
            }

          val settings = TodosoSettingsService.getInstance(project)
          settings.state.todoFilePath = pathToSave
          onRefreshUI()
        }
      }
    }
}
