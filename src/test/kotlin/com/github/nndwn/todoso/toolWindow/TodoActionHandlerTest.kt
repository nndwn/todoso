package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil

class TodoActionHandlerTest : BasePlatformTestCase() {

  private lateinit var handler: TodoActionHandler
  private lateinit var service: MyProjectService
  private var refreshCalled = false
  private var editModeEnabled = false

  private var priorityFilter: MyProjectService.Priority? = null

  private val fakeView =
    object : TodoActionHandler.TodoViewActions {
      override fun refreshTasks() {
        refreshCalled = true
      }

      override fun setEditMode(enabled: Boolean, text: String) {
        editModeEnabled = enabled
      }

      override fun getSelectedTask(): MyProjectService.TodoTask? = null

      override fun updateButtonStates() {}

      override fun setPriorityFilter(priority: MyProjectService.Priority?) {
        priorityFilter = priority
      }

      override fun setStatusFilter(status: MyProjectService.TaskStatus?) {
        statusFilter = status
      }

      override fun setTagFilter(tag: String?) {
        tagFilter = tag
      }
    }

  private var statusFilter: MyProjectService.TaskStatus? = null
  private var tagFilter: String? = null

  override fun setUp() {
    super.setUp()
    service = project.getService(MyProjectService::class.java)
    val settings = project.getService(MyProjectSettingsService::class.java)
    handler = TodoActionHandler(project, service, settings, fakeView)
  }

  fun testCanTransitionTo() {
    val task =
      MyProjectService.TodoTask(
        "test-id",
        true,
        "- [ ] Task",
        "Task",
        MyProjectService.TaskStatus.TODO,
        MyProjectService.Priority.NONE,
        emptyList(),
        emptyMap(),
        0,
      )

    assertTrue(handler.canTransitionTo(task, MyProjectService.TaskStatus.DOING))
    assertFalse(handler.canTransitionTo(task, MyProjectService.TaskStatus.DONE)) // Must be DOING first
  }

  fun testHandleRandomTask() {
    val content =
      """
      - [ ] Task 1
      - [ ] Task 2
      """
        .trimIndent()
    myFixture.addFileToProject("todo.md", content)

    handler.handleRandomTask()
    UIUtil.dispatchAllInvocationEvents()

    val tasks = service.getTodoTasks()
    val doingTasks = tasks.filter { it.status == MyProjectService.TaskStatus.DOING }
    assertEquals(1, doingTasks.size)
    assertTrue(refreshCalled)
  }

  fun testSetPriorityFilter() {
    handler.setPriorityFilter(MyProjectService.Priority.HIGH)
    assertEquals(MyProjectService.Priority.HIGH, priorityFilter)
  }

  fun testSetStatusFilter() {
    handler.setStatusFilter(MyProjectService.TaskStatus.DONE)
    assertEquals(MyProjectService.TaskStatus.DONE, statusFilter)
  }
}
