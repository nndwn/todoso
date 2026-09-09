package com.github.nndwn.todoso.integration

import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.DateParser
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.toolWindow.TodosoInputPanel
import com.github.nndwn.todoso.toolWindow.TodosoMainPanel
import com.github.nndwn.todoso.toolWindow.TodosoToolbar
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font
import java.io.File

class FeatureConsistencyTest : BasePlatformTestCase() {

    private lateinit var service: TodosoService

    override fun setUp() {
        super.setUp()
        service = project.getService(TodosoService::class.java)
    }

    /**
     * README: "Date then Priority (Combined Sort): Evaluates tasks by date first; 
     * if dates are equal or missing, it automatically falls back to sorting by priority."
     */
    fun testCombinedSortDateThenPriority() {
        val tasks = listOf(
            createTask("Task A", Priority.LOW, "2024-01-01"),
            createTask("Task B", Priority.HIGH, "2024-01-01"), // Same date, higher priority
            createTask("Task C", Priority.MEDIUM, "2023-12-31") // Earlier date
        )

        val panel = TodosoMainPanel(project)
        val sortOptions = setOf(TodosoToolbar.SortOption.DATE, TodosoToolbar.SortOption.PRIORITY)
        
        // Accessing private method for testing core logic
        val method = panel.javaClass.getDeclaredMethod("applySorting", List::class.java, Set::class.java)
        method.isAccessible = true
        
        @Suppress("UNCHECKED_CAST")
        val sorted = method.invoke(panel, tasks, sortOptions) as List<TodoTask>

        assertEquals("Task C", sorted[0].description) // Earliest date first
        assertEquals("Task B", sorted[1].description) // Same date, HIGH priority
        assertEquals("Task A", sorted[2].description) // Same date, LOW priority
    }

    /**
     * README: "Flexible Casing: Automatically detects todo.md, TODO.md, Todo.md, or any casing variation without issues."
     */
    fun testFileCasingDetection() {
        myFixture.addFileToProject("tOdO.mD", "- [ ] Casing Test")
        
        // TodosoService uses TodosoConstants.FILENAME ("TODO.md") by default if path is empty
        val file = service.getTodoFile()
        assertNotNull("Should detect tOdO.mD even when searching for TODO.md", file)
        assertEquals("tOdO.mD", file?.name)
    }

    /**
     * README: "Empty Checkbox Protection: Submitting inputs containing only empty status checkboxes is automatically rejected."
     */
    fun testEmptyInputProtection() {
        // Accessing private validation in TodosoInputPanel
        val panel = TodosoInputPanel(
            onNewTask = {}, onUpdateTask = {}, onConfirmCancel = {}, 
            onCreateNote = {}, onCancelEdit = {}, 
            fontInput = Font("Monospaced", 0, 12),
            getPopularTags = { emptyList() },
            getAllTasks = { emptyList() }
        )
        
        val method = panel.javaClass.getDeclaredMethod("isInputValid", String::class.java)
        method.isAccessible = true

        assertFalse("Pure checkbox should be invalid", method.invoke(panel, "- [ ]") as Boolean)
        assertFalse("Checkbox with spaces should be invalid", method.invoke(panel, "- [/]  ") as Boolean)
        assertTrue("Checkbox with text should be valid", method.invoke(panel, "- [ ] My Task") as Boolean)
        assertTrue("Plain text should be valid", method.invoke(panel, "Just text") as Boolean)
    }

    /**
     * README: "Execution Duration Calculation: Automatically calculates execution duration between Start Date (🛫) and Completion Date (✅)"
     */
    fun testDurationCalculation() {
        val line = "- [x] Done Task 🛫 2024-01-01 10:00 ✅ 2024-01-01 11:30"
        val task = TodoTaskParser.parseLine(line, 1)
        
        assertNotNull(task)
        val duration = DateParser.calculateDuration(task!!.metadata)
        assertEquals("1h 30m", duration)
    }

    private fun createTask(desc: String, priority: Priority, date: String): TodoTask {
        return TodoTaskParser.parseLine("- [ ] $desc 📅 $date", 1)!!
            .copy(priority = priority)
    }
}
