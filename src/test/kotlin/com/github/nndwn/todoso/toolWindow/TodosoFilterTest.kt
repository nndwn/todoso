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
        // Metadata (Priority/Dates) must follow strict positioning rules.
        // Added persistent IDs to prevent auto-persistence from interfering with tests.
        val content = """
            - [ ] 🔺 Task 1 #feature 🛫 2024-01-01 🆔 t1a2b3
            - [/] ⏫ Task 2 #issue 🆔 t2c3d4
            - [x] 🔼 Task 3 #production 📅 2024-01-02 🆔 t3e4f5
            - [ ] 🔽 Task 4 #development 🆔 t4g5h6
            - [-] ⏬ Task 5 #issue 🆔 t5i6j7
        """.trimIndent()
        myFixture.addFileToProject(TodosoConstants.FILENAME, content)
        mainPanel = TodosoTestHelper.createMainPanel(project)
        mainPanel.refreshTasks()
        UIUtil.dispatchAllInvocationEvents()
    }

    fun testPriorityFilter() {
        mainPanel.setPriorityFilter(Priority.HIGH)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 1 task for High priority", 1, mainPanel.taskListView.taskComponents.size)
        assertTrue(mainPanel.taskListView.taskComponents[0].task.description.contains("Task 2"))

        mainPanel.setPriorityFilter(null)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show all 5 tasks after reset", 5, mainPanel.taskListView.taskComponents.size)
    }

    fun testStatusFilter() {
        mainPanel.setStatusFilter(TaskStatus.DONE)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 1 task for Done status", 1, mainPanel.taskListView.taskComponents.size)
        assertTrue(mainPanel.taskListView.taskComponents[0].task.description.contains("Task 3"))

        mainPanel.setStatusFilter(TaskStatus.TODO)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 2 tasks for Todo status", 2, mainPanel.taskListView.taskComponents.size)
    }

    fun testDateFilter() {
        mainPanel.setDateFilter("WITH_DATE")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 2 tasks with dates", 2, mainPanel.taskListView.taskComponents.size)
        
        mainPanel.setDateFilter(null)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show all 5 tasks after reset", 5, mainPanel.taskListView.taskComponents.size)
    }

    fun testTagFilter() {
        mainPanel.setTagFilter("issue")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 2 tasks with tag #issue", 2, mainPanel.taskListView.taskComponents.size)
        
        mainPanel.setTagFilter(null)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show all 5 tasks after reset", 5, mainPanel.taskListView.taskComponents.size)
    }

    fun testCombinedFilters() {
        mainPanel.setStatusFilter(TaskStatus.TODO)
        mainPanel.setTagFilter("feature")
        UIUtil.dispatchAllInvocationEvents()
        
        assertEquals("Should show only 1 task matching both criteria", 1, mainPanel.taskListView.taskComponents.size)
        assertTrue(mainPanel.taskListView.taskComponents[0].task.description.contains("Task 1"))
        
        mainPanel.setPriorityFilter(Priority.LOW)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("Should show 0 tasks when combination doesn't match", 0, mainPanel.taskListView.taskComponents.size)
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
        assertEquals("Should show 1 task for today", 1, mainPanel.taskListView.taskComponents.size)
        assertTrue(mainPanel.taskListView.taskComponents[0].task.description.contains("Task Today"))

        mainPanel.setDateFilter("THIS_WEEK")
        UIUtil.dispatchAllInvocationEvents()
        assertTrue("Task Today should also be in This Week", 
                   mainPanel.taskListView.taskComponents.any { it.task.description.contains("Task Today") })
    }
}
