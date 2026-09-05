package com.github.nndwn.todoso.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class TodoService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): TodoService = project.service()
    }
}