package com.github.nndwn.todoso.toolWindow.inputWindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Font

class TodosoInputPanelUiTest : BasePlatformTestCase() {

  private lateinit var inputPanel: TodosoInputPanel

  private var lastNewTask: String? = null
  private var lastUpdatedTask: String? = null
  private var lastConfirmedCancel: String? = null
  private var lastCreatedNote: String? = null
  private var cancelEditCalled = false

  override fun setUp() {
    super.setUp()

    lastNewTask = null
    lastUpdatedTask = null
    lastConfirmedCancel = null
    lastCreatedNote = null
    cancelEditCalled = false

    inputPanel =
      TodosoInputPanel(
        project = project,
        onNewTask = { lastNewTask = it },
        onUpdateTask = { lastUpdatedTask = it },
        onConfirmCancel = { lastConfirmedCancel = it },
        onCreateNote = { lastCreatedNote = it },
        onCancelEdit = { cancelEditCalled = true },
        fontInput = Font("Monospaced", Font.PLAIN, 12),
        getPopularTags = { emptyList() },
        getAllTasks = { emptyList() },
        onSuggestionRequest = {},
        onNavigationRequest = {},
      )
  }

  fun testNormalModeValidationAndSubmit() {
    assertFalse("Tombol harus disabled saat teks kosong", inputPanel.actionButton.isEnabled)

    inputPanel.inputTextArea.text = "- [ ] "
    assertFalse("Tombol harus disabled jika hanya berisi prefix status kosong", inputPanel.actionButton.isEnabled)

    inputPanel.inputTextArea.text = "#onlytag"
    assertFalse("Tombol harus disabled jika hanya berisi tag", inputPanel.actionButton.isEnabled)

    inputPanel.inputTextArea.text = "[H] 🔺"
    assertFalse("Tombol harus disabled jika hanya berisi prioritas", inputPanel.actionButton.isEnabled)

    inputPanel.inputTextArea.text = "🛫 2026-09-09"
    assertFalse("Tombol harus disabled jika hanya berisi tanggal", inputPanel.actionButton.isEnabled)

    inputPanel.inputTextArea.text = "Fix UI Navigation Bug #ui"
    assertTrue("Tombol harus enabled saat input memiliki deskripsi valid", inputPanel.actionButton.isEnabled)

    inputPanel.actionButton.doClick()
    assertEquals("Fix UI Navigation Bug #ui", lastNewTask)
  }

  fun testEditModeUnchangedTextValidation() {
    val original = "Beli kopi di minimarket"
    inputPanel.setEditMode(true, original)

    assertTrue("Mode aktif harus Edit", inputPanel.currentMode is InputMode.Edit)
    assertEquals("Beli kopi di minimarket", inputPanel.inputTextArea.text)
    assertFalse("Tombol update harus disabled jika teks tidak diubah", inputPanel.actionButton.isEnabled)

    inputPanel.inputTextArea.text = "Beli kopi di minimarket #urgent"
    assertTrue("Tombol update harus enabled setelah teks diubah", inputPanel.actionButton.isEnabled)

    inputPanel.actionButton.doClick()
    assertEquals("Beli kopi di minimarket #urgent", lastUpdatedTask)
  }

  fun testNoteModeAutoPrefixAndValidation() {
    inputPanel.setNoteMode(true)
    assertTrue("Mode aktif harus Note", inputPanel.currentMode is InputMode.Note)
    assertEquals("// ", inputPanel.inputTextArea.text)

    inputPanel.inputTextArea.text = "// ini catatan penting"
    inputPanel.actionButton.doClick()
    assertEquals("// ini catatan penting", lastCreatedNote)
  }

  fun testCancelModeExecution() {
    inputPanel.setCancelMode(true, "Alasan: Diabaikan oleh Product Owner")
    assertTrue("Mode aktif harus Cancel", inputPanel.currentMode is InputMode.Cancel)

    inputPanel.actionButton.doClick()
    assertEquals("Alasan: Diabaikan oleh Product Owner", lastConfirmedCancel)
  }

  fun testCancelEditAndClearState() {
    inputPanel.setEditMode(true, "Task yang sedang diedit")
    inputPanel.clearInputText()
    assertTrue("Mode harus kembali ke Normal", inputPanel.currentMode is InputMode.Normal)
    assertEquals("", inputPanel.inputTextArea.text)
  }

  fun testInsertMarkdownAttachment() {
    val mockFile = myFixture.addFileToProject("images/test.png", "").virtualFile

    // 1. Test insertion when text area is empty
    inputPanel.inputTextArea.text = "My Task"
    inputPanel.insertMarkdownAttachment(mockFile)

    // Flush EDT events
    UIUtil.dispatchAllInvocationEvents()

    val expectedSnippet = "![test.png](images/test.png)"
    assertEquals("My Task // $expectedSnippet", inputPanel.inputTextArea.text)

    // 2. Test insertion when notes already exist
    inputPanel.inputTextArea.text = "Task // existing note"
    inputPanel.insertMarkdownAttachment(mockFile)

    UIUtil.dispatchAllInvocationEvents()

    assertEquals("Task // existing note $expectedSnippet", inputPanel.inputTextArea.text)

    // 3. Test non-image file
    val mockDoc = myFixture.addFileToProject("docs/readme.txt", "").virtualFile
    inputPanel.inputTextArea.text = "Task"
    inputPanel.insertMarkdownAttachment(mockDoc)

    UIUtil.dispatchAllInvocationEvents()

    assertEquals("Task // [readme.txt](docs/readme.txt)", inputPanel.inputTextArea.text)
  }
}
