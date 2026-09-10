package com.github.nndwn.todoso.toolWindow.inputWindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.event.KeyEvent

class TodosoTagSuggestionTest : BasePlatformTestCase() {

  private lateinit var inputPanel: TodosoInputPanel
  private var lastRequestedItems: List<SuggestionItem>? = null

  override fun setUp() {
    super.setUp()
    lastRequestedItems = null
    inputPanel =
      TodosoInputPanel(
        project = project,
        onNewTask = {},
        onUpdateTask = {},
        onConfirmCancel = {},
        onCreateNote = {},
        onCancelEdit = {},
        fontInput = Font("Monospaced", Font.PLAIN, 12),
        getPopularTags = { listOf("feature", "bug") },
        getAllTasks = { emptyList() },
        onSuggestionRequest = { lastRequestedItems = it },
        onNavigationRequest = {},
      )
  }

  fun testGetActivePrefix() {
    assertEquals("fe", inputPanel.getActivePrefix("Fix this #fe", 12))
    assertEquals("", inputPanel.getActivePrefix("Normal text #", 13))
    assertNull(inputPanel.getActivePrefix("No hash here", 12))
  }

  fun testSuggestionTriggerOnHash() {
    inputPanel.inputTextArea.text = "#"
    inputPanel.inputTextArea.caretPosition = 1

    // Simulasikan pengetikan '#'
    val event =
      KeyEvent(inputPanel.inputTextArea, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, '#')
    inputPanel.inputTextArea.keyListeners.forEach { it.keyTyped(event) }

    UIUtil.dispatchAllInvocationEvents()

    assertNotNull("Saran harusnya terpicu saat menekan #", lastRequestedItems)
    assertTrue("Harus mengandung tag 'feature'", lastRequestedItems!!.any { it.text == "feature" })
  }

  fun testInsertItemAtCaretReplacesPrefix() {
    inputPanel.inputTextArea.text = "New task #f"
    inputPanel.inputTextArea.caretPosition = 11

    inputPanel.insertItemAtCaret("feature", false)
    assertEquals("New task #feature", inputPanel.inputTextArea.text)
  }

  fun testOverlayHidesWhenHashIsRemoved() {
    // 1. Ketik '#' untuk memicu overlay
    inputPanel.inputTextArea.text = "#"
    inputPanel.inputTextArea.caretPosition = 1
    UIUtil.dispatchAllInvocationEvents()
    assertNotNull("Overlay harusnya muncul saat ada #", lastRequestedItems)

    // 2. Hapus '#' (simulasi backspace)
    inputPanel.inputTextArea.text = ""
    inputPanel.inputTextArea.caretPosition = 0
    UIUtil.dispatchAllInvocationEvents()

    assertNull("Overlay harusnya tersembunyi (null) saat # dihapus", lastRequestedItems)
  }
}
