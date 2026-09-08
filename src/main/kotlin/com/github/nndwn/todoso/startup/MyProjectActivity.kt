package com.github.nndwn.todoso.startup

import com.github.nndwn.todoso.services.TodosoService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity{
    override suspend fun execute(project: Project) {
        val service = project.service<TodosoService>()
        service.loadTask()
    }
}