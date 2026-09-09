package com.github.nndwn.todoso.integration.inputWindow

import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.DateParser
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.toolWindow.TodosoMainPanel
import com.github.nndwn.todoso.toolWindow.TodosoToolbar
import com.github.nndwn.todoso.toolWindow.inputWindow.TodosoInputPanel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font

class FeatureConsistencyTest : BasePlatformTestCase() {

    private lateinit var service: TodosoService

    override fun setUp() {
        super.setUp()
        service = project.getService(TodosoService::class.java)
    }

    fun testCombinedSortDateThenPriority() {
        val tasks = listOf(
            createTask("Task A", Priority.LOW, "2024-01-01"),
            createTask("Task B", Priority.HIGH, "2024-01-01"),
            createTask("Task C", Priority.MEDIUM, "2023-12-31")
        )

        val panel = TodosoMainPanel(project)
        val sortOptions = setOf(TodosoToolbar.SortOption.DATE, TodosoToolbar.SortOption.PRIORITY)
        
        val method = panel.javaClass.getDeclaredMethod("applySorting", List::class.java, Set::class.java)
        method.isAccessible = true
        
        @Suppress("UNCHECKED_CAST")
        val sorted = method.invoke(panel, tasks, sortOptions) as List<TodoTask>

        assertEquals("Task C", sorted[0].description)
        assertEquals("Task B", sorted[1].description)
        assertEquals("Task A", sorted[2].description)
    }

    fun testFileCasingDetection() {
        myFixture.addFileToProject("tOdO.mD", "- [ ] Casing Test")
        val file = service.getTodoFile()
        assertNotNull("Should detect tOdO.mD", file)
        assertEquals("tOdO.mD", file?.name)
    }

    fun testEmptyInputProtection() {
        val panel = TodosoInputPanel(
            onNewTask = {}, onUpdateTask = {}, onConfirmCancel = {}, 
            onCreateNote = {}, onCancelEdit = {}, 
            fontInput = Font("Monospaced", 0, 12),
            getPopularTags = { emptyList() },
            getAllTasks = { emptyList() }
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

    private fun createTask(desc: String, priority: Priority, date: String): TodoTask {
        return TodoTaskParser.parseLine("- [ ] $desc 📅 $date", 1)!!
            .copy(priority = priority)
    }
}
