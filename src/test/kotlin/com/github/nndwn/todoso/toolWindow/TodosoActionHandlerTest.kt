package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.toolWindow.inputWindow.InputMode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil

class TodosoActionHandlerTest : BasePlatformTestCase() {

  private lateinit var mainPanel: TodosoMainPanel
  private lateinit var handler: TodosoActionHandler
  private lateinit var service: TodosoService

  override fun setUp() {
    super.setUp()
    val content =
      """
      - [ ] Task 1 🆔 t1a2b3
      - [ ] Task 2 🆔 t2c3d4
      """
        .trimIndent()

    service = project.service<TodosoService>()
    val existingFile = service.getTodoFile()
    if (existingFile != null) {
      WriteCommandAction.runWriteCommandAction(project) {
        VfsUtil.saveText(existingFile, content)
      }
    } else {
      myFixture.addFileToProject(TodosoConstants.FILENAME, content)
    }

    mainPanel = TodosoTestHelper.createMainPanel(project)
    handler = mainPanel.handler
    mainPanel.refreshTasks()
    UIUtil.dispatchAllInvocationEvents()
  }

  fun testCancelStatusSavesDraftAndEntersCancelMode() {
    val task1 = service.loadTask().first { it.id == "t1a2b3" }
    mainPanel.inputPanel.inputTextArea.text = "Draft task yang sedang ditulis #dev"

    handler.updateTaskStatus(task1, TaskStatus.CANCELLED)
    UIUtil.dispatchAllInvocationEvents()

    assertTrue("Handler harus masuk ke InputMode.Cancel", handler.getCurrentMode() is InputMode.Cancel)
    assertEquals("Area input harus kosong untuk diisi note pembatalan", "", mainPanel.inputPanel.inputTextArea.text)

    val currentTask1 = service.loadTask().first { it.id == task1.id }
    assertEquals("Status task belum boleh berubah sebelum konfirmasi note", TaskStatus.TODO, currentTask1.status)
  }

  fun testConfirmCancelRestoresSavedDraftAndUpdatesTaskStatus() {
    val task1 = service.loadTask().first { it.id == "t1a2b3" }
    val draftText = "Draft task yang sedang ditulis #dev"
    mainPanel.inputPanel.inputTextArea.text = draftText

    handler.updateTaskStatus(task1, TaskStatus.CANCELLED)
    UIUtil.dispatchAllInvocationEvents()

    handler.handleConfirmCancel("Diabaikan karena duplicate")
    UIUtil.dispatchAllInvocationEvents()

    val updatedTask1 = service.loadTask().first { it.id == task1.id }
    assertEquals("Status task1 harus menjadi CANCELLED", TaskStatus.CANCELLED, updatedTask1.status)
    assertTrue("Note pembatalan harus tersimpan", updatedTask1.metadata.notes.contains("Diabaikan karena duplicate"))

    assertTrue("Input mode harus kembali ke Normal", handler.getCurrentMode() is InputMode.Normal)
    assertEquals("Draft task dari memory harus dikembalikan ke area input", draftText, mainPanel.inputPanel.inputTextArea.text)
  }

  fun testCancelEditRestoresSavedDraftWithoutUpdatingTaskStatus() {
    val task1 = service.loadTask().first { it.id == "t1a2b3" }
    val draftText = "Draft task yang sedang ditulis #dev"
    mainPanel.inputPanel.inputTextArea.text = draftText

    handler.updateTaskStatus(task1, TaskStatus.CANCELLED)
    UIUtil.dispatchAllInvocationEvents()

    handler.handleCancelEdit()
    UIUtil.dispatchAllInvocationEvents()

    val currentTask1 = service.loadTask().first { it.id == task1.id }
    assertEquals("Status task1 harus tetap TODO saat diedit/dibatalkan", TaskStatus.TODO, currentTask1.status)

    assertTrue("Input mode harus kembali ke Normal", handler.getCurrentMode() is InputMode.Normal)
    assertEquals("Draft task dari memory harus dikembalikan ke area input", draftText, mainPanel.inputPanel.inputTextArea.text)
  }

  fun testCancelStatusWithEmptyDraft() {
    val task1 = service.loadTask().first { it.id == "t1a2b3" }
    mainPanel.inputPanel.inputTextArea.text = ""

    handler.updateTaskStatus(task1, TaskStatus.CANCELLED)
    UIUtil.dispatchAllInvocationEvents()

    handler.handleConfirmCancel("Not needed")
    UIUtil.dispatchAllInvocationEvents()

    val updatedTask1 = service.loadTask().first { it.id == task1.id }
    assertEquals("Status task1 harus menjadi CANCELLED", TaskStatus.CANCELLED, updatedTask1.status)
    assertEquals("Area input tetap kosong jika tidak ada draft sebelumnya", "", mainPanel.inputPanel.inputTextArea.text)
  }

  fun testEditTaskPreservesDraftAndRestoresOnUpdate() {
    val task1 = service.loadTask().first { it.id == "t1a2b3" }
    val draftText = "Draft task yang sedang ditulis #dev"
    mainPanel.inputPanel.inputTextArea.text = draftText

    mainPanel.setSelectedTask(task1)
    handler.setEditMode(true, task1.description)
    UIUtil.dispatchAllInvocationEvents()

    assertTrue("Mode harus Edit", handler.getCurrentMode() is InputMode.Edit)
    assertEquals("Input text berisi deskripsi task1 yang diedit", task1.description, mainPanel.inputPanel.inputTextArea.text)

    handler.handleUpdateTask("Task 1 Ter-update #ui")
    UIUtil.dispatchAllInvocationEvents()

    val updatedTask1 = service.loadTask().first { it.id == task1.id }
    assertTrue("Deskripsi task1 harus ter-update", updatedTask1.description.contains("Task 1 Ter-update"))
    assertTrue("Mode harus kembali ke Normal", handler.getCurrentMode() is InputMode.Normal)
    assertEquals("Draft task harus dikembalikan setelah edit selesai", draftText, mainPanel.inputPanel.inputTextArea.text)
  }

  fun testEditTaskPreservesDraftAndRestoresOnCancel() {
    val task1 = service.loadTask().first { it.id == "t1a2b3" }
    val draftText = "Draft task yang sedang ditulis #dev"
    mainPanel.inputPanel.inputTextArea.text = draftText

    mainPanel.setSelectedTask(task1)
    handler.setEditMode(true, task1.description)
    UIUtil.dispatchAllInvocationEvents()

    handler.handleCancelEdit()
    UIUtil.dispatchAllInvocationEvents()

    val updatedTask1 = service.loadTask().first { it.id == task1.id }
    assertEquals("Deskripsi task1 tidak berubah jika dibatalkan", "Task 1", updatedTask1.description)
    assertTrue("Mode harus kembali ke Normal", handler.getCurrentMode() is InputMode.Normal)
    assertEquals("Draft task harus dikembalikan saat edit dibatalkan", draftText, mainPanel.inputPanel.inputTextArea.text)
  }

  fun testAddNotePreservesDraftAndRestoresOnUpdate() {
    val task1 = service.loadTask().first { it.id == "t1a2b3" }
    val draftText = "Draft task yang sedang ditulis #dev"
    mainPanel.inputPanel.inputTextArea.text = draftText

    mainPanel.setSelectedTask(task1)
    handler.setNoteMode(true, "")
    UIUtil.dispatchAllInvocationEvents()

    assertTrue("Mode harus Note", handler.getCurrentMode() is InputMode.Note)

    handler.handleUpdateNote("// Catatan baru untuk task1")
    UIUtil.dispatchAllInvocationEvents()

    val updatedTask1 = service.loadTask().first { it.id == task1.id }
    assertTrue("Note task1 harus ter-update", updatedTask1.metadata.notes.contains("Catatan baru untuk task1"))
    assertTrue("Mode harus kembali ke Normal", handler.getCurrentMode() is InputMode.Normal)
    assertEquals("Draft task harus dikembalikan setelah note ditambahkan", draftText, mainPanel.inputPanel.inputTextArea.text)
  }

  fun testInputFocusInNormalModeClearsListSelection() {
    val task1 = service.loadTask().first { it.id == "t1a2b3" }
    mainPanel.setSelectedTask(task1)
    assertEquals("Task 1 harus terpilih saat dilih", task1.id, mainPanel.getSelectedTask()?.id)

    mainPanel.inputPanel.inputTextArea.requestFocusInWindow()
    UIUtil.dispatchAllInvocationEvents()

    mainPanel.inputPanel.inputTextArea.text = "Mulai mengetik task baru..."
    UIUtil.dispatchAllInvocationEvents()

    assertNull("Seleksi di item task harus dibersihkan saat user mengetik task baru di InputMode.Normal", mainPanel.getSelectedTask())
  }
}
