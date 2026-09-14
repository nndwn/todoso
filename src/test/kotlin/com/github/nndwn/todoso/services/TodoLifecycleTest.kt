package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.domain.model.TaskStatus
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TodoLifecycleTest : BasePlatformTestCase() {

  private lateinit var service: TodosoService

  override fun setUp() {
    super.setUp()
    service = project.getService(TodosoService::class.java)
  }

  /**
   * Menguji apakah `editedDate` otomatis ditambahkan/diperbarui saat task diedit.
   */
  fun testEditedDateIsUpdatedOnEdit() {
    service.addTask("Initial Task")
    var task = service.loadTask().first()
    assertNull("Task baru tidak boleh memiliki editedDate", task.metadata.editedDate)

    // Lakukan edit
    service.editTask(task, "Updated Task")
    
    task = service.loadTask().first()
    assertNotNull("Setelah edit, editedDate harus terisi", task.metadata.editedDate)
    assertTrue("editedDate harus berisi tanggal hari ini", task.metadata.editedDate!!.startsWith(getTodayPrefix()))
  }

  /**
   * Menguji transisi siklus hidup: TODO -> DOING (Harus ada startDate).
   */
  fun testStartDateAddedOnDoing() {
    service.addTask("Future Task")
    var task = service.loadTask().first()
    assertNull(task.metadata.startDate)

    service.updateTaskStatus(task, TaskStatus.DOING)

    task = service.loadTask().first()
    assertNotNull("Transisi ke DOING harus mencatat startDate", task.metadata.startDate)
    assertTrue(task.metadata.startDate!!.startsWith(getTodayPrefix()))
  }

  /**
   * Menguji transisi siklus hidup: DOING -> DONE (Harus ada endDate).
   */
  fun testEndDateAddedOnDone() {
    service.addTask("Work Task")
    var task = service.loadTask().first()
    
    service.updateTaskStatus(task, TaskStatus.DOING)
    service.updateTaskStatus(service.loadTask().first(), TaskStatus.DONE)

    task = service.loadTask().first()
    assertNotNull("Transisi ke DONE harus mencatat endDate", task.metadata.endDate)
  }

  /**
   * Menguji transisi siklus hidup: Apapun -> CANCELLED (Harus ada cancelDate).
   */
  fun testCancelDateAddedOnCancelled() {
    service.addTask("Aborted Task")
    var task = service.loadTask().first()

    service.updateTaskStatus(task, TaskStatus.CANCELLED, "Reason for cancel")

    task = service.loadTask().first()
    assertNotNull("Transisi ke CANCELLED harus mencatat cancelDate", task.metadata.cancelDate)
    assertEquals("Reason for cancel", task.metadata.notes)
  }

  /**
   * Menguji pembersihan riwayat (Reset): DONE -> TODO.
   * Riwayat Start, End, dan Cancel harus dihapus agar tidak valid lagi.
   */
  fun testLifecycleCleanupOnReturnToTodo() {
    service.addTask("Cycle Task")
    var task = service.loadTask().first()

    // Jalankan siklus sampai DONE
    service.updateTaskStatus(task, TaskStatus.DOING)
    service.updateTaskStatus(service.loadTask().first(), TaskStatus.DONE)
    
    task = service.loadTask().first()
    assertNotNull(task.metadata.startDate)
    assertNotNull(task.metadata.endDate)

    // Kembalikan ke TODO
    service.updateTaskStatus(task, TaskStatus.TODO)
    
    task = service.loadTask().first()
    assertNull("Kembali ke TODO harus menghapus startDate", task.metadata.startDate)
    assertNull("Kembali ke TODO harus menghapus endDate", task.metadata.endDate)
    assertNull("Kembali ke TODO harus menghapus cancelDate", task.metadata.cancelDate)
  }

  /**
   * Menguji transisi: CANCELLED -> DOING.
   * cancelDate harus dihapus dan diganti dengan startDate baru.
   */
  fun testCleanupOnResumingCancelledTask() {
    service.addTask("Retry Task")
    var task = service.loadTask().first()

    service.updateTaskStatus(task, TaskStatus.CANCELLED, "Stop")
    task = service.loadTask().first()
    assertNotNull(task.metadata.cancelDate)

    // Resume ke DOING
    service.updateTaskStatus(task, TaskStatus.DOING)
    
    task = service.loadTask().first()
    assertNull("Resume ke DOING harus menghapus cancelDate", task.metadata.cancelDate)
    assertNotNull("Resume ke DOING harus mencatat startDate baru", task.metadata.startDate)
  }

  private fun getTodayPrefix(): String {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
  }
}
