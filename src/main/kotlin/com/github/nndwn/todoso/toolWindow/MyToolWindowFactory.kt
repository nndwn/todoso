package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import javax.swing.BoxLayout

class MyToolWindowFactory : ToolWindowFactory {

  init {}

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val myToolWindow = MyToolWindow(toolWindow)
    val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
    toolWindow.contentManager.addContent(content)
  }

  override fun shouldBeAvailable(project: Project) = true

  class MyToolWindow(toolWindow: ToolWindow) {

    private val service = toolWindow.project.service<MyProjectService>()

    fun getContent(): JBPanel<JBPanel<*>> {
      val panel = JBPanel<JBPanel<*>>()
      panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

      fun refresh() {
        panel.removeAll()

        val tasks = service.getTodoTasks()
        if (tasks.isEmpty()) {
          panel.add(JBLabel("No tasks found in todo.md (root project)"))
        } else {
          tasks.forEach { task ->
            panel.add(JBLabel("- $task", JBLabel.LEFT))
          }
        }

        panel.revalidate()
        panel.repaint()
      }

      refresh()
      return panel
    }
  }
}
