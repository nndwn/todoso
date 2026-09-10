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
    assertEquals(
      "Refactor module https://github.com/nndwn/todoso#readme dengan #C# dan #F# & <script>alert(1)</script>",
      task.description,
    )
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

  fun testEditTaskAddsEditedDate() {
    // 1. Tambah task awal
    service.addTask("Task Awal")
    var task = service.loadTask().first()
    val createdDate = task.metadata.createdDate
    assertNotNull("Created date harus ada", createdDate)
    assertNull("Edited date harusnya null di awal", task.metadata.editedDate)

    // 2. Edit task
    service.editTask(task, "Task Setelah Diubah")

    // 3. Verifikasi
    val tasks = service.loadTask()
    val editedTask = tasks.first()

    assertEquals("Task Setelah Diubah", editedTask.description)
    assertEquals(createdDate, editedTask.metadata.createdDate)
    assertNotNull("Edited date harus muncul setelah diedit", editedTask.metadata.editedDate)
    assertTrue(
      "Edited date harus mengandung format tanggal yang valid",
      editedTask.metadata.editedDate!!.contains(Regex("""\d{4}-\d{2}-\d{2}""")),
    )
  }

  fun testBlankTaskProtectionInService() {
    // Bersihkan file dulu agar mulai dari nol khusus untuk test ini
    WriteCommandAction.runWriteCommandAction(project) {
      service.getTodoFile()?.setBinaryContent(ByteArray(0))
    }
    service.markCacheDirty()

    // 1. Cek jika hanya berisi Tag
    service.addTask("#onlytag")
    assertTrue("Tugas yang hanya berisi tag tidak boleh disimpan", service.loadTask().isEmpty())

    // 2. Cek jika hanya berisi Prioritas Emoji
    service.addTask("🔺")
    assertTrue("Tugas yang hanya berisi prioritas tidak boleh disimpan", service.loadTask().isEmpty())

    // 3. Cek jika hanya berisi Tanggal (Dinamis)
    service.addTask("📅 2026-09-10")
    assertTrue("Tugas yang hanya berisi tanggal tidak boleh disimpan", service.loadTask().isEmpty())

    // 4. Campuran metadata tanpa deskripsi
    service.addTask("#urgent ⏫ 🛫 2026-09-10 🆔 a1b2c3")
    assertTrue("Tugas campuran tanpa deskripsi tidak boleh disimpan", service.loadTask().isEmpty())

    // 5. Pastikan jika ada deskripsi baru boleh
    service.addTask("Tugas Valid #urgent")
    assertEquals("Tugas valid harusnya tersimpan", 1, service.loadTask().size)
  }
}
