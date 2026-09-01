package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.github.nndwn.todoso.util.toTitleCase
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class TodoViewPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

  private val service = project.service<MyProjectService>()
  private val settings = MyProjectSettingsService.getInstance(project)
  private val listModel = CollectionListModel(service.getTodoTasks())
  private val list =
    JBList(listModel).apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      emptyText.text = "No tasks found"
      cellRenderer = TodoCellRenderer(service, settings)

      if (!isEmpty) {
        selectedIndex = 0
      }
    }

  private val inputField =
    JBTextField().apply {
      addKeyListener(
        object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            when (e.keyCode) {
              KeyEvent.VK_ENTER -> submitInput()
              KeyEvent.VK_ESCAPE -> hideInputPanel()
            }
          }
        }
      )
    }
  private val inputLabel = JBLabel()
  private val inputPanel =
    JBPanel<JBPanel<*>>(BorderLayout()).apply {
      border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 0)
      isVisible = false
      add(inputLabel, BorderLayout.WEST)
      add(inputField, BorderLayout.CENTER)
    }

  private var currentInputMode: InputMode = InputMode.NONE

  private enum class InputMode(val label: String) {
    NONE(""),
    ADD(" New Task: "),
    CANCEL_REASON(" Cancel Reason: "),
  }

  init {
    toolbar = createToolbar()
    val scrollPane = JBScrollPane(list)

    val contentPanel = JBPanel<JBPanel<*>>(BorderLayout())
    contentPanel.add(scrollPane, BorderLayout.CENTER)
    contentPanel.add(inputPanel, BorderLayout.SOUTH)

    setContent(contentPanel)
    setupContextMenu()
    setupDoubleClickListener()
  }

  private fun setupDoubleClickListener() {
    object : DoubleClickListener() {
        override fun onDoubleClick(event: MouseEvent): Boolean {
          val task = list.selectedValue ?: return false
          val projectDir = project.guessProjectDir() ?: return false
          val todoFile =
            projectDir.children.find { it.name.equals(settings.state.todoFileName, ignoreCase = true) } ?: return false

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
          object : AnAction("Refresh", "Refresh tasks from todo.md", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = refreshTasks()
          }
        )
        add(
          object : AnAction("Add Task", "Add a new task", AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) = showInputPanel(InputMode.ADD)
          }
        )
        addSeparator()
        add(
          object : ToggleAction("Visual Mode", "Enable/Disable priority colors and emojis", AllIcons.Actions.Show) {
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
    val group = buildTodoMenu {
      buildStatusSubMenu()
      buildEditSubMenu()
      buildPrioritySubMenu()

      separator()

      item("Refresh", AllIcons.Actions.Refresh, CommonShortcuts.getRerun()) { refreshTasks() }

      buildTagsSubMenu()

      item("Add Task", AllIcons.General.Add, CommonShortcuts.getNew()) { showInputPanel(InputMode.ADD) }
      item("Challenge Task", AllIcons.Actions.Lightning) {}

      separator()

      buildViewSubMenu()
    }
      .toActionGroup()

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

  private fun TodoMenuBuilder.buildStatusSubMenu() {
    subMenu("Status", AllIcons.Actions.Diff) {
      MyProjectService.TaskStatus.entries.forEach { status ->
        item(
          text = status.name.toTitleCase(),
          isEnabled = { canTransitionTo(status) },
        ) {
          if (status == MyProjectService.TaskStatus.CANCELLED) {
            showInputPanel(InputMode.CANCEL_REASON)
          } else {
            list.selectedValue?.let { task ->
              service.updateTaskStatus(task, status)
              ApplicationManager.getApplication().invokeLater { refreshTasks() }
            }
          }
        }
      }
    }
  }

  private fun canTransitionTo(newStatus: MyProjectService.TaskStatus): Boolean {
    val selected = list.selectedValue ?: return false
    if (selected.status == newStatus) return false
    return when (newStatus) {
      MyProjectService.TaskStatus.DONE -> selected.status == MyProjectService.TaskStatus.DOING
      MyProjectService.TaskStatus.CANCELLED -> selected.status != MyProjectService.TaskStatus.DONE
      else -> true
    }
  }

  private fun TodoMenuBuilder.buildEditSubMenu() {
    subMenu("Edit", AllIcons.Actions.Edit) {
      item("Edit Text", AllIcons.Actions.EditSource) {}
      item("Delete", AllIcons.General.Remove) {}
    }
  }

  private fun TodoMenuBuilder.buildPrioritySubMenu() {
    subMenu("Priority", AllIcons.General.Filter) {
      MyProjectService.Priority.entries
        .filter { it != MyProjectService.Priority.NONE }
        .forEach { priority ->
          item(priority.label) {}
        }
      separator()
      item("None") {}
    }
  }

  private fun TodoMenuBuilder.buildTagsSubMenu() {
    subMenu("Tags", AllIcons.Nodes.Tag) {
      item("Add Tag", AllIcons.General.Add) {}
      item("Remove Tag") {}
    }
  }

  private fun TodoMenuBuilder.buildViewSubMenu() {
    subMenu("View", AllIcons.Actions.Show) {
      toggle("Visual Mode", AllIcons.Actions.Show, { settings.state.visualEnabled }) {
        settings.state.visualEnabled = it
        list.repaint()
      }
      subMenu("Filter Priority", AllIcons.General.Filter) {
        item("Show All") {}
        separator()
        MyProjectService.Priority.entries
          .filter { it != MyProjectService.Priority.NONE }
          .forEach { priority ->
            item(priority.label) {}
          }
      }
    }
  }

  private fun showInputPanel(mode: InputMode) {
    currentInputMode = mode
    inputField.text = ""
    inputLabel.text = mode.label
    inputPanel.isVisible = true
    inputField.requestFocusInWindow()
  }

  private fun hideInputPanel() {
    inputPanel.isVisible = false
    currentInputMode = InputMode.NONE
    list.requestFocusInWindow()
  }

  private fun submitInput() {
    val text = inputField.text.trim()
    if (text.isNotEmpty()) {
      when (currentInputMode) {
        InputMode.ADD -> {
          service.addTask(text)
        }
        InputMode.CANCEL_REASON -> {
          list.selectedValue?.let { task ->
            service.updateTaskStatus(task, MyProjectService.TaskStatus.CANCELLED, text)
          }
        }
        else -> {}
      }
      ApplicationManager.getApplication().invokeLater {
        refreshTasks()
        hideInputPanel()
      }
    } else if (currentInputMode == InputMode.CANCEL_REASON) {
      list.selectedValue?.let { task ->
        service.updateTaskStatus(task, MyProjectService.TaskStatus.CANCELLED)
      }
      ApplicationManager.getApplication().invokeLater {
        refreshTasks()
        hideInputPanel()
      }
    } else {
      hideInputPanel()
    }
  }

  private fun List<TodoMenuElement>.toActionGroup(): DefaultActionGroup {
    val group = DefaultActionGroup()
    this.forEach { element ->
      when (element) {
        is TodoMenuElement.Action -> {
          val action =
            object : AnAction(element.text, null, element.icon) {
              override fun actionPerformed(e: AnActionEvent) = element.onAction()

              override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = element.isEnabled()
              }

              override fun getActionUpdateThread() = ActionUpdateThread.EDT
            }
          element.shortcut?.let { action.registerCustomShortcutSet(it, list) }
          group.add(action)
        }
        is TodoMenuElement.SubMenu -> {
          val subGroup =
            DefaultActionGroup(element.text, true).apply {
              templatePresentation.icon = element.icon
              addAll(element.children.toActionGroup())
            }
          group.add(subGroup)
        }
        TodoMenuElement.Separator -> group.addSeparator()
        is TodoMenuElement.Toggle -> {
          group.add(
            object : ToggleAction(element.text, null, element.icon) {
              override fun isSelected(e: AnActionEvent) = element.isSelected()

              override fun setSelected(e: AnActionEvent, state: Boolean) = element.onToggle(state)

              override fun getActionUpdateThread() = ActionUpdateThread.EDT
            }
          )
        }
      }
    }
    return group
  }

  private fun refreshTasks() {
    val selectedIndex = list.selectedIndex
    listModel.replaceAll(service.getTodoTasks())
    if (selectedIndex != -1 && selectedIndex < listModel.size) {
      list.selectedIndex = selectedIndex
    }
  }
}
