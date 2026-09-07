package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodoServiceEditTaskTest : BasePlatformTestCase() {

    private lateinit var service: TodosoService

    override fun setUp() {
        super.setUp()
        service = project.getService(TodosoService::class.java)
    }

    /**
     * Edge Case 1: Memastikan tag lama dapat dihapus jika pengguna menghapusnya dari input editor.
     */
    fun testEditTaskRemovesDeletedTags() {
        service.addTask("Fix UI Navigation #ui #frontend")
        val initialTasks = service.loadTask()
        assertEquals(1, initialTasks.size)
        val initialTask = initialTasks.first()
        assertEquals(listOf("ui", "frontend"), initialTask.tags)

        service.editTask(initialTask, "Fix UI Navigation #ui")

        val updatedTasks = service.loadTask()
        assertEquals(1, updatedTasks.size)
        val updatedTask = updatedTasks.first()

        assertEquals("Fix UI Navigation #ui", updatedTask.description)
        assertEquals(listOf("ui"), updatedTask.tags)
        assertTrue("Persistent ID harus tetap aktif", updatedTask.isPersistentId)
    }

    /**
     * Edge Case 2: Memastikan Priority awal tidak berubah/hilang saat deskripsi diedit.
     */
    fun testEditTaskPreservesOriginalPriority() {
        service.addTask("[H] Refactor Core Module #core")
        val initialTasks = service.loadTask()
        val initialTask = initialTasks.first()
        assertEquals(Priority.HIGH, initialTask.priority)

        service.editTask(initialTask, "Refactor Core Module V2 #core #v2")

        val updatedTasks = service.loadTask()
        val updatedTask = updatedTasks.first()

        assertEquals(Priority.HIGH, updatedTask.priority)
        assertEquals(listOf("core", "v2"), updatedTask.tags)
    }

    /**
     * Edge Case 3: Memastikan URL dengan fragment anchor (#) di dalam deskripsi tidak dianggap sebagai Tag.
     */
    fun testEditTaskWithUrlAnchorDoesNotExtractFalseTag() {
        service.addTask("Task Awal")
        val initialTask = service.loadTask().first()

        // Edit dengan memasukkan URL ber-anchor
        service.editTask(initialTask, "Read docs at https://github.com/nndwn/todoso#readme #doc")

        val updatedTask = service.loadTask().first()

        assertEquals("Read docs at https://github.com/nndwn/todoso#readme #doc", updatedTask.description)
        assertEquals(listOf("doc"), updatedTask.tags)
    }

    /**
     * Edge Case 4: Memastikan input kosong/blank diabaikan dan tidak merusak task yang ada.
     */
    fun testEditTaskWithBlankInputIsIgnored() {
        service.addTask("Task Utama #important")
        val initialTask = service.loadTask().first()

        service.editTask(initialTask, "   ")

        val currentTasks = service.loadTask()
        val currentTask = currentTasks.first()

        assertEquals("Task Utama #important", currentTask.description)
        assertEquals(listOf("important"), currentTask.tags)
    }

    /**
     * Edge Case 5: Memastikan status checkbox (- [ ]) tidak berubah saat melakukan edit biasa.
     */
    fun testEditTaskPreservesTaskStatus() {

        service.addTask("Task dalam pengerjaan")
        var task = service.loadTask().first()
        service.updateTaskStatus(task, TaskStatus.DOING)
        task = service.loadTask().first()
        assertEquals(TaskStatus.DOING, task.status)

        service.editTask(task, "Task dalam pengerjaan (Updated)")

        val updatedTask = service.loadTask().first()
        assertEquals(TaskStatus.DOING, updatedTask.status)
        assertEquals("Task dalam pengerjaan (Updated)", updatedTask.description)
    }
}