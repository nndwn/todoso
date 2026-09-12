package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.services.TodosoService
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.time.LocalDate

class TodosoFilterTest : BasePlatformTestCase() {

    private lateinit var mainPanel: TodosoMainPanel

    override fun setUp() {
        super.setUp()
        // Metadata (Priority/Dates) must follow strict positioning rules
        val content = """
            - [ ] 🔺 Task 1 #feature 🛫 2024-01-01
            - [/] ⏫ Task 2 #issue
            - [x] 🔼 Task 3 #production 📅 2024-01-02
            - [ ] 🔽 Task 4 #development
            - [-] ⏬ Task 5 #issue
        """.trimIndent()
        myFixture.addFileToProject(TodosoConstants.FILENAME, content)
        mainPanel = TodosoMainPanel(project)
        mainPanel.refreshTasks()
        UIUtil.dispatchAllInvocationEvents()
    }

    fun testPriorityFilter() {
        mainPanel.setPriorityFilter(Priority.HIGH)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 1 task for High priority", 1, mainPanel.taskComponents.size)
        assertTrue(mainPanel.taskComponents[0].task.description.contains("Task 2"))

        mainPanel.setPriorityFilter(null)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show all 5 tasks after reset", 5, mainPanel.taskComponents.size)
    }

    fun testStatusFilter() {
        mainPanel.setStatusFilter(TaskStatus.DONE)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 1 task for Done status", 1, mainPanel.taskComponents.size)
        assertTrue(mainPanel.taskComponents[0].task.description.contains("Task 3"))

        mainPanel.setStatusFilter(TaskStatus.TODO)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 2 tasks for Todo status", 2, mainPanel.taskComponents.size)
    }

    fun testDateFilter() {
        mainPanel.setDateFilter("WITH_DATE")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 2 tasks with dates", 2, mainPanel.taskComponents.size)
        
        mainPanel.setDateFilter(null)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show all 5 tasks after reset", 5, mainPanel.taskComponents.size)
    }

    fun testTagFilter() {
        mainPanel.setTagFilter("issue")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 2 tasks with tag #issue", 2, mainPanel.taskComponents.size)
        
        mainPanel.setTagFilter(null)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show all 5 tasks after reset", 5, mainPanel.taskComponents.size)
    }

    fun testCombinedFilters() {
        mainPanel.setStatusFilter(TaskStatus.TODO)
        mainPanel.setTagFilter("feature")
        UIUtil.dispatchAllInvocationEvents()
        
        assertEquals("Should show only 1 task matching both criteria", 1, mainPanel.taskComponents.size)
        assertTrue(mainPanel.taskComponents[0].task.description.contains("Task 1"))
        
        mainPanel.setPriorityFilter(Priority.LOW)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 0 tasks when combination doesn't match", 0, mainPanel.taskComponents.size)
    }

    fun testTodayAndWeekFilters() {
        val today = LocalDate.now().toString()
        val content = """
            - [ ] 🔺 Task Today 🛫 $today
            - [ ] ⏫ Task Future 📅 2099-01-01
        """.trimIndent()
        
        WriteCommandAction.runWriteCommandAction(project) {
            val vFile = project.getService(TodosoService::class.java).getTodoFile()
            if (vFile != null) {
                VfsUtil.saveText(vFile, content)
            }
        }
        mainPanel.refreshTasks()
        UIUtil.dispatchAllInvocationEvents()

        mainPanel.setDateFilter("TODAY")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 1 task for today", 1, mainPanel.taskComponents.size)
        assertTrue(mainPanel.taskComponents[0].task.description.contains("Task Today"))

        mainPanel.setDateFilter("THIS_WEEK")
        UIUtil.dispatchAllInvocationEvents()
        assertTrue("Task Today should also be in This Week", 
                   mainPanel.taskComponents.any { it.task.description.contains("Task Today") })
    }
}
