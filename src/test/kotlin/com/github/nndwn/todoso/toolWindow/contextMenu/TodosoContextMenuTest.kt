package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.domain.model.Metadata
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.TodosoActionHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodosoContextMenuTest : BasePlatformTestCase() {
    
    fun testBuildMenuStructure() {
        val service = project.getService(TodosoService::class.java)
        val settings = TodosoSettingsService.getInstance(project)
        
        // Mock view actions
        val view = object : TodosoActionHandler.TodoViewActions {
            var selected: TodoTask? = null
            override fun refreshTasks() {}
            override fun setEditMode(enabled: Boolean, text: String) {}
            override fun getSelectedTask(): TodoTask? = selected
            override fun updateButtonStates() {}
            override fun setPriorityFilter(priority: Priority?) {}
            override fun setStatusFilter(status: TaskStatus?) {}
            override fun setDateFilter(filter: String?) {}
            override fun setTagFilter(tag: String?) {}
            override fun setCancelMode(enabled: Boolean) {}
            override fun setNoteMode(enabled: Boolean, text: String) {}
            override fun getInputText(): String = ""
            override fun clearInputText() {}
            override fun requestUnfocus() {}
            override fun setSelectedTask(task: TodoTask?) { selected = task }
            override fun toggleSearch() {}
        }

        val handler = TodosoActionHandler(project, service, view)
        val contextMenu = TodosoContextMenu(service, settings, handler)

        // 1. Test tanpa seleksi
        var elements = contextMenu.build()
        assertTrue(elements.any { it is TodosoMenuElement.Action && it.text.contains("Refresh") })
        
        // 2. Test dengan seleksi
        val dummyTask = TodoTask(
            id = "test",
            isPersistentId = true,
            rawText = "- [ ] Test",
            description = "Test",
            status = TaskStatus.TODO,
            priority = Priority.MEDIUM,
            tags = emptyList(),
            lineNumber = 1,
            metadata = Metadata()
        )
        view.selected = dummyTask
        
        elements = contextMenu.build()
        assertTrue("Editor actions should be present with selection",
            elements.any { it is TodosoMenuElement.Action && it.text.contains("Edit") })
            
        val statusSubMenu = elements.filterIsInstance<TodosoMenuElement.SubMenu>()
            .find { it.text.contains("Status") }
        assertNotNull(statusSubMenu)
    }
}
