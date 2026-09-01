package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.MyIcons
import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

class MyToolWindowFactory : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val myToolWindow = MyToolWindow(project)
    val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
    toolWindow.contentManager.addContent(content)
  }

  override fun shouldBeAvailable(project: Project) = true

  class MyToolWindow(project: Project) {

    private val service = project.service<MyProjectService>()
    private val settings = MyProjectSettingsService.getInstance(project)

    fun getContent(): JComponent {
      val tasks = service.getTodoTasks()
      val listModel = CollectionListModel(tasks)
      val list =
        JBList(listModel).apply {
          selectionMode = ListSelectionModel.SINGLE_SELECTION
          emptyText.text = "No tasks found"
          cellRenderer = TodoCellRenderer(settings)

          if (tasks.isNotEmpty()) {
            selectedIndex = 0
          }
        }

      // Setup Context Menu (Klik Kanan)
      setupContextMenu(list, listModel)

      val panel = SimpleToolWindowPanel(true, true)
      panel.toolbar = createToolbar(list, listModel)
      panel.setContent(JBScrollPane(list))
      return panel
    }

    private fun setupContextMenu(
      list: JBList<MyProjectService.TodoTask>,
      model: CollectionListModel<MyProjectService.TodoTask>,
    ) {
      val group =
        DefaultActionGroup().apply {
          // Status Sub-menu
          add(
            DefaultActionGroup("Status", true).apply {
              templatePresentation.icon = AllIcons.Actions.Diff
              add(
                object : AnAction("Doing") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("Done", null, AllIcons.Actions.Checked) {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("Cancelled") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("Todo") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
            }
          )

          // Edit Sub-menu
          add(
            DefaultActionGroup("Edit", true).apply {
              templatePresentation.icon = AllIcons.Actions.Edit
              add(
                object : AnAction("Edit Text", null, AllIcons.Actions.EditSource) {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("Delete", null, AllIcons.General.Remove) {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
            }
          )

          // Priority Sub-menu
          add(
            DefaultActionGroup("Priority", true).apply {
              templatePresentation.icon = AllIcons.General.Filter
              add(
                object : AnAction("Highest") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("High") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("Medium") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("Low") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("Lowest") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("None") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
            }
          )

          addSeparator()

          // Refresh with Shortcut
          val refreshAction =
            object : AnAction("Refresh", "Refresh tasks from todo.md", AllIcons.Actions.Refresh) {
              override fun actionPerformed(e: AnActionEvent) = model.replaceAll(service.getTodoTasks())
            }
          refreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), list)
          add(refreshAction)

          // Tags Sub-menu
          add(
            DefaultActionGroup("Tags", true).apply {
              templatePresentation.icon = AllIcons.Nodes.Tag
              add(
                object : AnAction("Add Tag", null, AllIcons.General.Add) {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
              add(
                object : AnAction("Remove Tag") {
                  override fun actionPerformed(e: AnActionEvent) {}
                }
              )
            }
          )

          // Add Task with Shortcut
          val addTaskAction =
            object : AnAction("Add Task", "Add a new task to todo.md", AllIcons.General.Add) {
              override fun actionPerformed(e: AnActionEvent) {}
            }
          addTaskAction.registerCustomShortcutSet(CommonShortcuts.getNew(), list)
          add(addTaskAction)

          add(
            object : AnAction("Challenge Task", null, AllIcons.Actions.Lightning) {
              override fun actionPerformed(e: AnActionEvent) {}
            }
          )

          addSeparator()

          // View Sub-menu
          add(
            DefaultActionGroup("View", true).apply {
              templatePresentation.icon = AllIcons.Actions.Show
              add(
                object : ToggleAction("Visual Mode", null, AllIcons.Actions.Show) {
                  override fun isSelected(e: AnActionEvent): Boolean = settings.state.visualEnabled

                  override fun setSelected(e: AnActionEvent, state: Boolean) {
                    settings.state.visualEnabled = state
                    list.repaint()
                  }

                  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                }
              )
              add(
                DefaultActionGroup("Filter Priority", true).apply {
                  add(
                    object : AnAction("Show All") {
                      override fun actionPerformed(e: AnActionEvent) {}
                    }
                  )
                }
              )
            }
          )
        }

      list.addMouseListener(
        object : PopupHandler() {
          override fun invokePopup(component: java.awt.Component, x: Int, y: Int) {
            val index = list.locationToIndex(java.awt.Point(x, y))
            if (index != -1 && list.getCellBounds(index, index).contains(java.awt.Point(x, y))) {
              list.selectedIndex = index
            }

            ActionManager.getInstance().createActionPopupMenu("TodoViewPopup", group).component.show(component, x, y)
          }
        }
      )
    }

    private fun createToolbar(
      target: JBList<MyProjectService.TodoTask>,
      model: CollectionListModel<MyProjectService.TodoTask>,
    ): JComponent {
      val actionGroup =
        DefaultActionGroup().apply {
          add(
            object : AnAction("Refresh", "Refresh tasks from todo.md", AllIcons.Actions.Refresh) {
              override fun actionPerformed(e: AnActionEvent) = model.replaceAll(service.getTodoTasks())
            }
          )
          add(
            object : AnAction("Add Task", "Add a new task to todo.md", AllIcons.General.Add) {
              override fun actionPerformed(e: AnActionEvent) {}
            }
          )
          addSeparator()
          add(
            object : ToggleAction("Visual Mode", "Enable/Disable priority colors and emojis", AllIcons.Actions.Show) {
              override fun isSelected(e: AnActionEvent): Boolean = settings.state.visualEnabled

              override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.state.visualEnabled = state
                target.repaint()
              }

              override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            }
          )
        }
      return ActionManager.getInstance()
        .createActionToolbar("TodoToolbar", actionGroup, true)
        .apply { targetComponent = target }
        .component
    }

    private class TodoCellRenderer(private val settings: MyProjectSettingsService) :
      ColoredListCellRenderer<MyProjectService.TodoTask>() {

      override fun customizeCellRenderer(
        list: JList<out MyProjectService.TodoTask>,
        value: MyProjectService.TodoTask?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
      ) {
        value ?: return

        val isDone = value.status == MyProjectService.TaskStatus.DONE
        val isDoing = value.status == MyProjectService.TaskStatus.DOING
        val isCancelled = value.status == MyProjectService.TaskStatus.CANCELLED
        val visualEnabled = settings.state.visualEnabled

        // 1. Set Status Icon from SVG
        icon =
          when (value.status) {
            MyProjectService.TaskStatus.DOING -> MyIcons.TaskDoing
            MyProjectService.TaskStatus.DONE -> MyIcons.TaskDone
            MyProjectService.TaskStatus.CANCELLED -> MyIcons.TaskCancelled
            MyProjectService.TaskStatus.TODO -> MyIcons.TaskTodo
          }

        val baseAttributes = getBaseAttributes(value, isDone, isDoing, isCancelled, visualEnabled)
        renderDescriptionWithTags(value.description, baseAttributes, isDone, isDoing, isCancelled)
        updateToolTip(value)
      }

      private fun getBaseAttributes(
        task: MyProjectService.TodoTask,
        isDone: Boolean,
        isDoing: Boolean,
        isCancelled: Boolean,
        visualEnabled: Boolean,
      ): SimpleTextAttributes {
        val style =
          when {
            isDone || isCancelled -> SimpleTextAttributes.STYLE_STRIKEOUT
            isDoing -> SimpleTextAttributes.STYLE_BOLD
            else -> SimpleTextAttributes.STYLE_PLAIN
          }

        val color =
          when {
            isDone || isCancelled -> SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor
            isDoing && visualEnabled -> JBColor.GREEN
            visualEnabled -> task.priority.color
            else -> SimpleTextAttributes.REGULAR_ATTRIBUTES.fgColor
          }
        return SimpleTextAttributes(style, color)
      }

      private fun renderDescriptionWithTags(
        description: String,
        baseAttributes: SimpleTextAttributes,
        isDone: Boolean,
        isDoing: Boolean,
        isCancelled: Boolean,
      ) {
        val tagRegex = Regex("""#[\w.-]+""")
        var lastIndex = 0

        tagRegex.findAll(description).forEach { match ->
          if (match.range.first > lastIndex) {
            append(description.substring(lastIndex, match.range.first), baseAttributes)
          }

          val tagStyle =
            when {
              isDone || isCancelled -> SimpleTextAttributes.STYLE_STRIKEOUT
              isDoing -> SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_ITALIC
              else -> SimpleTextAttributes.STYLE_ITALIC or SimpleTextAttributes.STYLE_BOLD
            }

          val tagColor = if (isDone || isCancelled) SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor else JBColor.CYAN
          append(match.value, SimpleTextAttributes(tagStyle, tagColor))

          lastIndex = match.range.last + 1
        }

        if (lastIndex < description.length) {
          append(description.substring(lastIndex), baseAttributes)
        }
      }

      private fun updateToolTip(task: MyProjectService.TodoTask) {
        toolTipText = buildString {
          if (task.priority != MyProjectService.Priority.NONE) {
            append("[${task.priority.label}] ")
          }
          append(task.description)

          if (task.dates.isNotEmpty()) {
            append("\nDates:")
            task.dates.forEach { (emoji, date) ->
              val label =
                when (emoji) {
                  "🛫" -> "Start"
                  "📅" -> "Due"
                  "⏳" -> "Scheduled"
                  "✅" -> "Done"
                  "➕" -> "Created"
                  "//" -> "Legacy Info"
                  else -> "Info"
                }
              append("\n  $emoji $label: $date")
            }
          }
        }
      }
    }
  }
}
