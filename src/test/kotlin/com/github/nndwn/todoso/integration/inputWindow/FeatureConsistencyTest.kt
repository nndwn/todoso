package com.github.nndwn.todoso.integration.inputWindow

import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.DateParser
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.toolWindow.SortOption
import com.github.nndwn.todoso.toolWindow.inputWindow.TodosoInputPanel
import com.github.nndwn.todoso.toolWindow.logic.TodoTaskFilterer
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font

class FeatureConsistencyTest : BasePlatformTestCase() {

  private lateinit var service: TodosoService

  override fun setUp() {
    super.setUp()
    service = project.getService(TodosoService::class.java)
  }

  fun testCombinedSortDateThenPriority() {
    val tasks =
      listOf(
        createTask("Task A", Priority.LOW, "2024-01-01"),
        createTask("Task B", Priority.HIGH, "2024-01-01"),
        createTask("Task C", Priority.MEDIUM, "2023-12-31"),
      )

    val sortOptions = setOf(SortOption.DATE, SortOption.PRIORITY)
    val sorted = TodoTaskFilterer.applySorting(tasks, sortOptions)

    // New logic: Descending date (latest first)
    assertEquals("Task B", sorted[0].description) // 2024-01-01, High
    assertEquals("Task A", sorted[1].description) // 2024-01-01, Low
    assertEquals("Task C", sorted[2].description) // 2023-12-31, Medium
  }

  fun testFileCasingDetection() {
    myFixture.addFileToProject("tOdO.mD", "- [ ] Casing Test")
    val file = service.getTodoFile()
    assertNotNull("Should detect tOdO.mD", file)
    assertEquals("tOdO.mD", file?.name)
  }

  fun testEmptyInputProtection() {
    val panel =
      TodosoInputPanel(
        project = project,
        onNewTask = {},
        onUpdateTask = {},
        onConfirmCancel = {},
        onCreateNote = {},
        onCancelEdit = {},
        fontInput = Font("Monospaced", 0, 12),
        getPopularTags = { emptyList() },
        getAllTasks = { emptyList() },
        onSuggestionRequest = {},
        onNavigationRequest = {},
        onTabPressed = {}
      )

    // Direct access to internal method
    assertFalse("Pure checkbox should be invalid", panel.isInputValid("- [ ]"))
    assertFalse("Checkbox with spaces should be invalid", panel.isInputValid("- [/]  "))
    assertTrue("Checkbox with text should be valid", panel.isInputValid("- [ ] My Task"))
    assertTrue("Plain text should be valid", panel.isInputValid("Just text"))
  }

  fun testDurationCalculation() {
    val line = "- [x] Done Task 🛫 2024-01-01 10:00 ✅ 2024-01-01 11:30"
    val task = TodoTaskParser.parseLine(line, 1)

    assertNotNull(task)
    val duration = DateParser.calculateDuration(task!!.metadata)
    assertEquals("1h 30m", duration)
  }


  fun testAddToChangelog() {
    val task = TodoTaskParser.parseLine("- [ ] New Feature #feature", 1)!!
    val result = service.addToChangelog(task)
    assertTrue("Should succeed adding to changelog", result)

    val projectDir = project.guessProjectDir()
    val changelogFile = projectDir?.findChild("CHANGELOG.md")
    assertNotNull("CHANGELOG.md should be created", changelogFile)

    val content = VfsUtil.loadText(changelogFile!!)
    assertTrue("Should contain header", content.contains("# Changelog"))
    assertTrue("Should contain Unreleased", content.contains("## [Unreleased]"))
    assertTrue("Should contain task description", content.contains("- New Feature #feature"))
  }

  private fun createTask(desc: String, priority: Priority, date: String): TodoTask {
    return TodoTaskParser.parseLine("- [ ] $desc 📅 $date", 1)!!.copy(priority = priority)
  }
}
