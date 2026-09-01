package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodoActionHandlerTest : BasePlatformTestCase() {

  private lateinit var handler: TodoActionHandler
  private lateinit var service: MyProjectService
  private var refreshCalled = false
  private var editModeEnabled = false

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
    }

  override fun setUp() {
    super.setUp()
    service = project.getService(MyProjectService::class.java)
    val settings = project.getService(MyProjectSettingsService::class.java)
    handler = TodoActionHandler(project, service, settings, fakeView)
  }

  fun testCanTransitionTo() {
    val task =
      MyProjectService.TodoTask(
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
}
