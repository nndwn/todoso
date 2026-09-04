package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.MyBundle
import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.HierarchyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class TodoViewPanel(
  private val project: Project,
  private val service: MyProjectService = project.service(),
  private val settings: MyProjectSettingsService = MyProjectSettingsService.getInstance(project),
) : SimpleToolWindowPanel(true, true), TodoActionHandler.TodoViewActions {

  private val handler = TodoActionHandler(project, service, settings, this)
  private val listModel = CollectionListModel(service.getTodoTasks())
  private val list =
    JBList(listModel).apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      emptyText.text = MyBundle.message("todo.window.empty")
      cellRenderer = TodoCellRenderer(service, settings)
      if (!isEmpty) selectedIndex = 0
    }

  private val footerPanel =
    TodoFooterPanel(
      onNewTask = { handler.handleAddTask(it) },
      onCancelTask = { handler.handleCancelAction(it) },
      onUpdateTask = { handler.handleUpdateTask(it) },
      onCancelEdit = { handler.handleCancelEdit() },
    )

  private val tagCloudPanel = TodoTagCloudPanel { tag -> setTagFilter(tag) }

  init {
    toolbar = createToolbar()
    val scrollPane = JBScrollPane(list)
    val contentPanel =
      JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(scrollPane, BorderLayout.CENTER)
        val southPanel =
          JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(tagCloudPanel, BorderLayout.NORTH)
            add(footerPanel, BorderLayout.CENTER)
          }
        add(southPanel, BorderLayout.SOUTH)
      }
    setContent(contentPanel)
    setupContextMenu()
    setupDoubleClickListener()
    setupSelectionListener()
    setupVisibilityListener()
    refreshTasks()
    updateButtonStates()
  }

  private fun setupVisibilityListener() {
    addHierarchyListener { e ->
      if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && isShowing) {
        refreshTasks()
      }
    }
  }

  private var currentTagFilter: String? = null

  override fun refreshTasks() {
    val selectedIndex = list.selectedIndex
    val pFilterName = settings.state.priorityFilterName
    val sFilterName = settings.state.statusFilterName

    val allTasks = service.getTodoTasks()

    val filteredTasks = allTasks.filter { task ->
      val pMatch = pFilterName == null || task.priority.name == pFilterName
      val sMatch = sFilterName == null || task.status.name == sFilterName
      val tMatch = currentTagFilter == null || task.tags.contains(currentTagFilter)
      pMatch && sMatch && tMatch
    }
    listModel.replaceAll(filteredTasks)

    val tags = service.getTagCounts()
    tagCloudPanel.isVisible = tags.isNotEmpty()
    tagCloudPanel.setTags(tags, currentTagFilter)

    if (selectedIndex != -1 && selectedIndex < listModel.size) {
      list.selectedIndex = selectedIndex
    }
  }

  override fun setPriorityFilter(priority: MyProjectService.Priority?) {
    settings.state.priorityFilterName = priority?.name
    refreshTasks()
  }

  override fun setStatusFilter(status: MyProjectService.TaskStatus?) {
    settings.state.statusFilterName = status?.name
    refreshTasks()
  }

  override fun setTagFilter(tag: String?) {
    currentTagFilter = tag
    refreshTasks()
  }

  override fun setEditMode(enabled: Boolean, text: String) {
    footerPanel.setEditMode(enabled, text)
  }

  override fun getSelectedTask(): MyProjectService.TodoTask? = list.selectedValue

  override fun updateButtonStates() {
    if (footerPanel.isEditMode) {
      footerPanel.canceledTaskButton.isEnabled = true
      return
    }
    val selected = list.selectedValue
    footerPanel.canceledTaskButton.isEnabled =
      selected != null &&
        selected.status != MyProjectService.TaskStatus.DONE &&
        selected.status != MyProjectService.TaskStatus.CANCELLED
  }

  private fun setupSelectionListener() {
    list.addListSelectionListener { updateButtonStates() }
  }

  private fun setupDoubleClickListener() {
    object : DoubleClickListener() {
        override fun onDoubleClick(event: MouseEvent): Boolean {
          val task = getSelectedTask() ?: return false
          val projectDir = project.guessProjectDir() ?: return false
          val todoFile =
            projectDir.children.find {
              it.name.equals(settings.state.todoFileName, ignoreCase = true)
            } ?: return false

          OpenFileDescriptor(project, todoFile, task.lineNumber, 0).navigate(true)
          return true
        }
      }
      .installOn(list)
  }

  private fun createToolbar(): JComponent {
    val actionGroup =
      DefaultActionGroup().apply {
        add(
          object :
            AnAction(
              MyBundle.message("todo.menu.refresh"),
              MyBundle.message("todo.action.refresh.desc"),
              AllIcons.Actions.Refresh,
            ) {
            override fun actionPerformed(e: AnActionEvent) = refreshTasks()
          }
        )
        add(
          object :
            AnAction(
              MyBundle.message("todo.menu.random"),
              MyBundle.message("todo.action.random.desc"),
              AllIcons.Actions.Lightning,
            ) {
            override fun actionPerformed(e: AnActionEvent) = handler.handleRandomTask()
          }
        )
        addSeparator()
        add(
          object :
            ToggleAction(
              MyBundle.message("todo.menu.visual.mode"),
              MyBundle.message("todo.action.visual.mode.desc"),
              AllIcons.Actions.Show,
            ) {
            override fun isSelected(e: AnActionEvent): Boolean = settings.state.visualEnabled

            override fun setSelected(e: AnActionEvent, state: Boolean) {
              settings.state.visualEnabled = state
              list.repaint()
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
          }
        )
      }
    return ActionManager.getInstance()
      .createActionToolbar("TodoToolbar", actionGroup, true)
      .apply { targetComponent = list }
      .component
  }

  private fun setupContextMenu() {
    val contextMenu = TodoContextMenu(service, settings, handler)
    val group = contextMenu.build().toActionGroup(list)

    list.addMouseListener(
      object : PopupHandler() {
        override fun invokePopup(component: Component, x: Int, y: Int) {
          val index = list.locationToIndex(Point(x, y))
          if (index != -1 && list.getCellBounds(index, index).contains(Point(x, y))) {
            list.selectedIndex = index
          }
          val popupMenu = ActionManager.getInstance().createActionPopupMenu("TodoViewPopup", group)
          popupMenu.setTargetComponent(list)
          popupMenu.component.show(component, x, y)
        }
      }
    )
  }
}
