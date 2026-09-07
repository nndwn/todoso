package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodosoServiceTest : BasePlatformTestCase() {

    private lateinit var service: TodosoService

    override fun setUp() {
        super.setUp()
        service = project.getService(TodosoService::class.java)
    }


    fun testAddMultipleTasks() {

        service.addTask("Task Pertama #core")
        service.addTask("Task Kedua #ui")


        val tasks = service.loadTask()
        assertEquals(2, tasks.size)

        assertEquals("Task Pertama #core", tasks[0].description)
        assertEquals(listOf("core"), tasks[0].tags)
        assertEquals(1, tasks[0].lineNumber)
        assertNotNull("Created date harus tercatat otomatis", tasks[0].metadata.createdDate)

        assertEquals("Task Kedua #ui", tasks[1].description)
        assertEquals(listOf("ui"), tasks[1].tags)
        assertEquals(2, tasks[1].lineNumber)
        assertNotNull("Created date harus tercatat otomatis", tasks[1].metadata.createdDate)

        assertTrue("Task pertama harus memiliki persistent ID", tasks[0].isPersistentId)
        assertTrue("Task kedua harus memiliki persistent ID", tasks[1].isPersistentId)
        assertFalse("ID task tidak boleh sama", tasks[0].id == tasks[1].id)
    }

    fun testAddTaskWithSpecialSymbolsAndUrls() {
        val input = "Refactor module https://github.com/nndwn/todoso#readme dengan #C# dan #F# & <script>alert(1)</script>"

        service.addTask(input)

        val tasks = service.loadTask()
        assertEquals(1, tasks.size)

        val task = tasks.first()
        // URL anchor #readme harus tetap utuh di deskripsi, dan tag C# serta F# terekstrak bersih
        assertEquals("Refactor module https://github.com/nndwn/todoso#readme dengan #C# dan #F# & <script>alert(1)</script>", task.description)
        assertEquals(listOf("C#", "F#"), task.tags)
    }

    fun testAddTaskWithEmbeddedMetadataEmojisInDescription() {
        val input = "Beli tiket 📅 konser dan check 🆔 tiket di tempat #event"

        service.addTask(input)

        val tasks = service.loadTask()
        assertEquals(1, tasks.size)

        val task = tasks.first()
        // Emoji di tengah kalimat harus dipertahankan sebagai teks deskripsi biasa (tidak boleh terpotong)
        assertEquals("Beli tiket 📅 konser dan check 🆔 tiket di tempat #event", task.description)
        assertEquals(listOf("event"), task.tags)
        assertTrue("Task tetap mendapat persistent ID resmi di ekor", task.isPersistentId)
    }
    fun testAddTaskWithAccidentalStatusPrefixInput() {
        val input = "- [ ] Task yang diketik manual dengan prefix"

        service.addTask(input)

        val tasks = service.loadTask()
        assertEquals(1, tasks.size)

        val task = tasks.first()
        // Prefix ganda dibersihkan, deskripsi bersih dari duplikasi `- [ ]`
        assertEquals("- [ ] Task yang diketik manual dengan prefix", task.description)
        assertEquals(TaskStatus.TODO, task.status)
    }

    fun testAddTaskWithUnicodeAndMultibyteCharacters() {
        val input = "[H] 🇯🇵 タスクを作成する #日本語 #v1.0.0"

        service.addTask(input)

        val tasks = service.loadTask()
        assertEquals(1, tasks.size)

        val task = tasks.first()
        assertEquals("🇯🇵 タスクを作成する #日本語 #v1.0.0", task.description)
        assertEquals(Priority.HIGH, task.priority)
        assertEquals(listOf("日本語", "v1.0.0"), task.tags)
    }

    fun testAddTaskWithExistingCommentsAndBlankLines() {
        WriteCommandAction.runWriteCommandAction(project) {
            val projectDir = project.guessProjectDir()!!
            val file = projectDir.createChildData(this, TodosoConstants.FILENAME)
            VfsUtil.saveText(file, "- [ ] Task Lama 🆔 init01 // ini catatan awal\n")
        }

        service.addTask("Task Baru Setelah Komentar #test")

        val tasks = service.loadTask()
        assertEquals(2, tasks.size)

        assertEquals("Task Lama", tasks[0].description)
        assertEquals("ini catatan awal", tasks[0].metadata.notes)
        assertEquals(1, tasks[0].lineNumber)

        assertEquals("Task Baru Setelah Komentar #test", tasks[1].description)
        assertEquals(listOf("test"), tasks[1].tags)
        assertEquals(2, tasks[1].lineNumber)
    }

}
