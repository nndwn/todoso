package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class TodosoToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val service = project.service<TodosoService>()
    val settings = TodosoSettingsService.getInstance(project)

    val mainPanel =
      TodosoMainPanel(
        project = project,
        service = service,
        settings = settings,
      )

    val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }
}
