package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.domain.model.Metadata
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodosoCommitHelperTest : BasePlatformTestCase() {

  private fun createTask(description: String, id: String): TodoTask {
    return TodoTask(
      id = id,
      isPersistentId = id.isNotBlank(),
      rawText = "- [ ] $description 🆔 $id",
      description = description,
      status = TaskStatus.TODO,
      priority = Priority.NONE,
      tags = emptyList(),
      lineNumber = 1,
      metadata = Metadata(),
    )
  }

  fun testFormatTaskCommitMessageWithId() {
    val task = createTask("Fix UI navigation bug #dev", "w4vbbg")
    val formatted = TodosoCommitHelper.formatTaskCommitMessage(task)
    assertEquals("- Fix UI navigation bug #dev 🆔 w4vbbg", formatted)
  }

  fun testFormatTaskCommitMessageWithoutId() {
    val task = createTask("Fix UI navigation bug #dev", "")
    val formatted = TodosoCommitHelper.formatTaskCommitMessage(task)
    assertEquals("- Fix UI navigation bug #dev", formatted)
  }

  fun testFormatTaskCommitMessageAvoidsDuplicateId() {
    val task = createTask("Fix UI navigation bug #dev 🆔 w4vbbg", "w4vbbg")
    val formatted = TodosoCommitHelper.formatTaskCommitMessage(task)
    assertEquals("- Fix UI navigation bug #dev 🆔 w4vbbg", formatted)
  }

  fun testSetCommitMessageFallbackHandling() {
    val task = createTask("Integrate commit message #v2.1.0", "w4vbbg")
    var errorNotified = false
    TodosoCommitHelper.setCommitMessage(task, project, onError = { errorNotified = true })
    // In headless test env without Commit tool window registered, error callback triggers
    assertTrue("Should trigger error notification when Commit tool window is unavailable", errorNotified)
  }
}
