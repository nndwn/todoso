package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.MyBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil

@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {

  init {
    thisLogger().info(MyBundle["projectService", project.name])
  }

  fun getTodoTasks(): List<String> {
    val projectDir = project.guessProjectDir() ?: return emptyList()
    val todoFile = projectDir.children.find { it.name.equals("todo.md", ignoreCase = true) } ?: return emptyList()

    return VfsUtil.loadText(todoFile)
      .lines()
      .filter { it.trimStart().startsWith("- ") }
      .map { it.trimStart().removePrefix("- ").trim() }
  }
}
