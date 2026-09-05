package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil

class TodoCancelLogicTest : BasePlatformTestCase() {

  private lateinit var handler: TodoActionHandler
  private lateinit var service: MyProjectService

  private var refreshCalled = false
  private var cancelModeEnabled = false
  private var clearedInput = false
  private var inputTextValue = ""

  private val fakeView =
    object : TodoActionHandler.TodoViewActions {
      override fun refreshTasks() {
        refreshCalled = true
      }

      override fun setEditMode(enabled: Boolean, text: String) {}

      override fun getSelectedTask(): MyProjectService.TodoTask? = null

      override fun updateButtonStates() {}

      override fun setPriorityFilter(priority: MyProjectService.Priority?) {}

      override fun setStatusFilter(status: MyProjectService.TaskStatus?) {}

      override fun setTagFilter(tag: String?) {}

      override fun setCancelMode(enabled: Boolean) {
        cancelModeEnabled = enabled
      }

      override fun getInputText(): String = inputTextValue

      override fun clearInputText() {
        clearedInput = true
      }
    }

  override fun setUp() {
    super.setUp()
    service = project.getService(MyProjectService::class.java)
    val settings = project.getService(MyProjectSettingsService::class.java)
    settings.state.todoFileName = "todo.md"
    handler = TodoActionHandler(project, service, settings, fakeView)
  }

  fun testCancelTaskFlow() {
    val content = "- [ ] Task to cancel 🆔 testid"
    myFixture.addFileToProject("todo.md", content)
    val task = service.getTodoTasks()[0]

    // 1. Trigger cancel without input text
    inputTextValue = ""
    handler.updateTaskStatus(task, MyProjectService.TaskStatus.CANCELLED)

    assertTrue("Should enable cancel mode when input is empty", cancelModeEnabled)
    assertFalse("Should not have cleared input yet", clearedInput)

    // 2. User types note and confirms
    val note = "Reason for cancellation"
    handler.handleConfirmCancel(note)
    UIUtil.dispatchAllInvocationEvents()

    val updatedTask = service.getTodoTasks()[0]
    assertEquals(MyProjectService.TaskStatus.CANCELLED, updatedTask.status)
    assertTrue(updatedTask.rawText.contains("// noted: $note"))
    assertTrue("Should refresh tasks after update", refreshCalled)
    assertFalse("Should disable cancel mode after confirm", cancelModeEnabled)
    assertTrue("Should clear input after confirm", clearedInput)
  }
}
