package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.MyProjectService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

class MyToolWindowFactory : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val myToolWindow = MyToolWindow(toolWindow)
    val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
    toolWindow.contentManager.addContent(content)
  }

  override fun shouldBeAvailable(project: Project) = true

  class MyToolWindow(toolWindow: ToolWindow) {

    private val service = toolWindow.project.service<MyProjectService>()

    fun getContent(): JComponent {
      val listModel = CollectionListModel(service.getTodoTasks())
      val list =
        JBList(listModel).apply {
          selectionMode = ListSelectionModel.SINGLE_SELECTION
          emptyText.text = "No tasks found in todo.md"
          cellRenderer = TodoCellRenderer()
        }

      val panel = SimpleToolWindowPanel(true, true)
      panel.toolbar = createToolbar(list, listModel)
      panel.setContent(JBScrollPane(list))
      return panel
    }

    private fun createToolbar(target: JComponent, model: CollectionListModel<MyProjectService.TodoTask>): JComponent {
      val actionGroup =
        DefaultActionGroup().apply {
          add(
            object : AnAction("Refresh", "Refresh tasks from todo.md", AllIcons.Actions.Refresh) {
              override fun actionPerformed(e: AnActionEvent) = model.replaceAll(service.getTodoTasks())
            }
          )
          add(
            object : AnAction("Add Task", "Add a new task to todo.md", AllIcons.General.Add) {
              override fun actionPerformed(e: AnActionEvent) {} // Placeholder
            }
          )
        }
      return ActionManager.getInstance()
        .createActionToolbar("TodoToolbar", actionGroup, true)
        .apply { targetComponent = target }
        .component
    }

    private class TodoCellRenderer : ColoredListCellRenderer<MyProjectService.TodoTask>() {
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

        val baseAttributes = getBaseAttributes(value, isDone, isDoing)

        // Tampilkan Emoji Priority jika ada
        if (value.priority.emojis.isNotEmpty()) {
          append("${value.priority.emojis.first()} ", baseAttributes)
        }

        renderDescriptionWithTags(value.description, baseAttributes, isDone, isDoing)
        updateToolTip(value)
      }

      private fun getBaseAttributes(
        task: MyProjectService.TodoTask,
        isDone: Boolean,
        isDoing: Boolean,
      ): SimpleTextAttributes {
        val style =
          when {
            isDone -> SimpleTextAttributes.STYLE_STRIKEOUT
            isDoing -> SimpleTextAttributes.STYLE_BOLD
            else -> SimpleTextAttributes.STYLE_PLAIN
          }
        val color =
          when {
            isDone -> SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor
            isDoing -> JBColor.GREEN
            else -> task.priority.color
          }
        return SimpleTextAttributes(style, color)
      }

      private fun renderDescriptionWithTags(
        description: String,
        baseAttributes: SimpleTextAttributes,
        isDone: Boolean,
        isDoing: Boolean,
      ) {
        val tagRegex = Regex("""#[\w.-]+""")
        var lastIndex = 0

        tagRegex.findAll(description).forEach { match ->
          if (match.range.first > lastIndex) {
            append(description.substring(lastIndex, match.range.first), baseAttributes)
          }

          val tagStyle =
            when {
              isDone -> SimpleTextAttributes.STYLE_STRIKEOUT
              isDoing -> SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_ITALIC
              else -> SimpleTextAttributes.STYLE_ITALIC or SimpleTextAttributes.STYLE_BOLD
            }
          val tagColor = if (isDone) SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor else JBColor.CYAN
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
