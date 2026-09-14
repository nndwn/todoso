package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

object TodosoTestHelper {
  fun createMainPanel(project: Project): TodosoMainPanel {
    val service = project.service<TodosoService>()
    val settings = TodosoSettingsService.getInstance(project)

    return TodosoMainPanel(
      project = project,
      service = service,
      settings = settings,
    )
  }
}
