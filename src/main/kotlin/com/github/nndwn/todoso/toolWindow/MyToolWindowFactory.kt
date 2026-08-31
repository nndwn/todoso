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
      val tasks = service.getTodoTasks()
      val listModel = CollectionListModel(tasks)
      val list = JBList(listModel)

      list.selectionMode = ListSelectionModel.SINGLE_SELECTION
      list.emptyText.text = "No tasks found in todo.md"

      list.cellRenderer =
        object : ColoredListCellRenderer<MyProjectService.TodoTask>() {
          override fun customizeCellRenderer(
            list: JList<out MyProjectService.TodoTask>,
            value: MyProjectService.TodoTask?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
          ) {
            if (value != null) {
              val baseStyle =
                if (value.isDone) SimpleTextAttributes.STYLE_STRIKEOUT else SimpleTextAttributes.STYLE_PLAIN
              val baseColor = if (value.isDone) SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor else value.priority.color
              val baseAttributes = SimpleTextAttributes(baseStyle, baseColor)

              val text = value.description
              val tagRegex = Regex("""#[\w.-]+""")
              var lastIndex = 0

              tagRegex.findAll(text).forEach { match ->
                // Teks sebelum tag
                if (match.range.first > lastIndex) {
                  append(text.substring(lastIndex, match.range.first), baseAttributes)
                }

                // Teks tag (Highligt Cyan)
                val tagStyle =
                  if (value.isDone) SimpleTextAttributes.STYLE_STRIKEOUT
                  else (SimpleTextAttributes.STYLE_ITALIC or SimpleTextAttributes.STYLE_BOLD)
                val tagColor = if (value.isDone) SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor else JBColor.CYAN
                append(match.value, SimpleTextAttributes(tagStyle, tagColor))

                lastIndex = match.range.last + 1
              }

              // Sisa teks setelah tag terakhir
              if (lastIndex < text.length) {
                append(text.substring(lastIndex), baseAttributes)
              }

              toolTipText =
                if (value.priority != MyProjectService.Priority.NONE) {
                  "[${value.priority.label}] ${value.description}"
                } else {
                  value.description
                }
            }
          }
        }

      // Toolbar Setup
      val actionGroup =
        DefaultActionGroup().apply {
          add(
            object : AnAction("Refresh", "Refresh tasks from todo.md", AllIcons.Actions.Refresh) {
              override fun actionPerformed(e: AnActionEvent) {
                listModel.replaceAll(service.getTodoTasks())
              }
            }
          )
          add(
            object : AnAction("Add Task", "Add a new task to todo.md", AllIcons.General.Add) {
              override fun actionPerformed(e: AnActionEvent) {
                // Fungsi kosong untuk saat ini sesuai permintaan
              }
            }
          )
        }

      val toolbar = ActionManager.getInstance().createActionToolbar("TodoToolbar", actionGroup, true)
      toolbar.targetComponent = list

      val panel = SimpleToolWindowPanel(true, true)
      panel.toolbar = toolbar.component
      panel.setContent(JBScrollPane(list))

      return panel
    }
  }
}
