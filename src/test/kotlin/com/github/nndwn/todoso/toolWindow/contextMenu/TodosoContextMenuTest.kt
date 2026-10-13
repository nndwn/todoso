package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.TodosoActionHandler
import com.github.nndwn.todoso.toolWindow.TodosoMainPanel
import com.github.nndwn.todoso.toolWindow.TodosoTestHelper
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil

class TodosoContextMenuTest : BasePlatformTestCase() {

  private lateinit var mainPanel: TodosoMainPanel
  private lateinit var handler: TodosoActionHandler
  private lateinit var service: TodosoService
  private lateinit var settings: TodosoSettingsService

  override fun setUp() {
    super.setUp()
    val content =
      """
      - [ ] Task 1 🆔 t1a2b3
      - [ ] Task 2 🆔 t2c3d4
      """
        .trimIndent()

    service = project.service<TodosoService>()
    settings = project.service<TodosoSettingsService>()
    val existingFile = service.getTodoFile()
    if (existingFile != null) {
      WriteCommandAction.runWriteCommandAction(project) {
        VfsUtil.saveText(existingFile, content)
      }
    } else {
      myFixture.addFileToProject(TodosoConstants.FILENAME, content)
    }

    mainPanel = TodosoTestHelper.createMainPanel(project)
    handler = mainPanel.handler
    mainPanel.refreshTasks()
    UIUtil.dispatchAllInvocationEvents()
  }

  fun testBatchChangeStatus() {
    val tasks = service.loadTask()
    assertEquals(2, tasks.size)

    mainPanel.taskListView.setSelectedTasks(tasks)
    assertEquals(2, mainPanel.taskListView.getSelectedTasks().size)

    val menu = TodosoContextMenu(service, settings, handler).build()
    assertNotNull(menu)

    // Execute status change on selected tasks
    mainPanel.taskListView.getSelectedTasks().forEach {
      handler.updateTaskStatus(it, TaskStatus.DONE)
    }
    UIUtil.dispatchAllInvocationEvents()

    val updatedTasks = service.loadTask()
    assertTrue(updatedTasks.all { it.status == TaskStatus.DONE })
  }

  fun testBatchChangePriority() {
    val tasks = service.loadTask()
    assertEquals(2, tasks.size)

    mainPanel.taskListView.setSelectedTasks(tasks)

    mainPanel.taskListView.getSelectedTasks().forEach {
      handler.handleUpdatePriority(it, Priority.HIGH)
    }
    UIUtil.dispatchAllInvocationEvents()

    val updatedTasks = service.loadTask()
    assertTrue(updatedTasks.all { it.priority == Priority.HIGH })
  }

  fun testSingleTaskActionsDisabledWhenMultipleTasksSelected() {
    val tasks = service.loadTask()
    mainPanel.taskListView.setSelectedTasks(tasks)

    val menuElements = TodosoContextMenu(service, settings, handler).build()

    val actionItems = menuElements.filterIsInstance<TodosoMenuElement.Action>()

    val editTaskItem = actionItems.find { it.text.contains("Edit") && !it.text.contains("Note") }
    val noteItem = actionItems.find { it.text.contains("Note") }

    if (editTaskItem != null) {
      assertFalse(editTaskItem.isEnabled())
    }

    if (noteItem != null) {
      assertFalse(noteItem.isEnabled())
    }
  }
}
