package com.github.nndwn.todoso.toolWindow.taskList

import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.toolWindow.TodosoMainPanel
import com.github.nndwn.todoso.toolWindow.TodosoTestHelper
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.MouseEvent

class TodosoTaskListViewTest : BasePlatformTestCase() {

  private lateinit var mainPanel: TodosoMainPanel
  private lateinit var taskListView: TodosoTaskListView
  private lateinit var service: TodosoService

  override fun setUp() {
    super.setUp()
    val content =
      """
      - [ ] Task 1 🆔 t1a2b3
      - [ ] Task 2 🆔 t2c3d4
      - [ ] Task 3 🆔 t3e4f5
      - [ ] Task 4 🆔 t4g5h6
      """
        .trimIndent()

    service = project.service<TodosoService>()
    val existingFile = service.getTodoFile()
    if (existingFile != null) {
      WriteCommandAction.runWriteCommandAction(project) {
        VfsUtil.saveText(existingFile, content)
      }
    } else {
      myFixture.addFileToProject(TodosoConstants.FILENAME, content)
    }

    mainPanel = TodosoTestHelper.createMainPanel(project)
    taskListView = mainPanel.taskListView
    mainPanel.refreshTasks()
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun createMouseEvent(component: Component, isShiftDown: Boolean = false, isControlDown: Boolean = false): MouseEvent {
    val modifiers = (if (isShiftDown) MouseEvent.SHIFT_DOWN_MASK else 0) or
        (if (isControlDown) MouseEvent.CTRL_DOWN_MASK else 0)
    return MouseEvent(
      component,
      MouseEvent.MOUSE_PRESSED,
      System.currentTimeMillis(),
      modifiers,
      10,
      10,
      1,
      false,
      MouseEvent.BUTTON1
    )
  }

  fun testSingleClickSelection() {
    val tasks = service.loadTask()
    assertEquals(4, tasks.size)

    val firstComp = taskListView.taskComponents[0]
    taskListView.handleTaskClick(tasks[0], createMouseEvent(firstComp))

    assertEquals(1, taskListView.getSelectedTasks().size)
    assertEquals(tasks[0].id, taskListView.getSelectedTask()?.id)
  }

  fun testShiftClickRangeSelection() {
    val tasks = service.loadTask()
    val comp0 = taskListView.taskComponents[0]
    val comp2 = taskListView.taskComponents[2]

    // Click Task at index 0
    taskListView.handleTaskClick(tasks[0], createMouseEvent(comp0))
    assertEquals(1, taskListView.getSelectedTasks().size)

    // Shift + Click Task at index 2
    taskListView.handleTaskClick(tasks[2], createMouseEvent(comp2, isShiftDown = true))

    val selected = taskListView.getSelectedTasks()
    assertEquals(3, selected.size)
    assertEquals(listOf(tasks[0].id, tasks[1].id, tasks[2].id), selected.map { it.id })
  }

  fun testCtrlClickToggleSelection() {
    val tasks = service.loadTask()
    val comp0 = taskListView.taskComponents[0]
    val comp2 = taskListView.taskComponents[2]

    // Click Task at index 0
    taskListView.handleTaskClick(tasks[0], createMouseEvent(comp0))

    // Ctrl + Click Task at index 2
    taskListView.handleTaskClick(tasks[2], createMouseEvent(comp2, isControlDown = true))

    val selected = taskListView.getSelectedTasks()
    assertEquals(2, selected.size)
    assertEquals(listOf(tasks[0].id, tasks[2].id), selected.map { it.id })
  }

  fun testSetSelectedTaskNullClearsSelection() {
    val tasks = service.loadTask()
    taskListView.setSelectedTask(tasks[0])
    assertEquals(1, taskListView.getSelectedTasks().size)

    taskListView.setSelectedTask(null)
    assertEquals(0, taskListView.getSelectedTasks().size)
    assertNull(taskListView.getSelectedTask())
  }

  fun testSelectionIgnoredWhenLocked() {
    val tasks = service.loadTask()
    val comp0 = taskListView.taskComponents[0]
    val comp2 = taskListView.taskComponents[2]

    // Select Task at index 0
    taskListView.handleTaskClick(tasks[0], createMouseEvent(comp0))
    assertEquals(tasks[0].id, taskListView.getSelectedTask()?.id)

    // Lock list (e.g. during Edit/Note mode)
    taskListView.isLocked = true

    // Click Task at index 2 while locked -> selection should remain Task 0
    taskListView.handleTaskClick(tasks[2], createMouseEvent(comp2))
    assertEquals(1, taskListView.getSelectedTasks().size)
    assertEquals(tasks[0].id, taskListView.getSelectedTask()?.id)

    // Unlock -> selection changes as normal
    taskListView.isLocked = false
    taskListView.handleTaskClick(tasks[2], createMouseEvent(comp2))
    assertEquals(tasks[2].id, taskListView.getSelectedTask()?.id)
  }
}
