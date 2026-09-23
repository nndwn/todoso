package com.github.nndwn.todoso.toolWindow.inputWindow

import com.github.nndwn.todoso.toolWindow.inputWindow.components.SuggestionOverlayPanel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.event.KeyEvent

class TodosoTagSuggestionTest : BasePlatformTestCase() {

  private lateinit var inputPanel: TodosoInputPanel
  private var lastRequestedItems: List<SuggestionItem>? = null

  private fun createSuggestionItem(tag: String, category: String) = SuggestionItem(
    text = tag,
    category = category,
    type = SuggestionType.TAGS,
    tagDisplay = tag
  )

  override fun setUp() {
    super.setUp()
    lastRequestedItems = null
    inputPanel =
      TodosoInputPanel(
        onNewTask = {},
        onUpdateTask = {},
        onConfirmCancel = {},
        onCreateNote = {},
        onCancelEdit = {},
        fontInput = Font("Monospaced", Font.PLAIN, 12),
        onSuggestionProvider = { prefix ->
          listOf("feature", "bug")
            .filter { it.startsWith(prefix) }
            .map { createSuggestionItem(it, "Popular Tags") }
        },
        onSuggestionRequest = { lastRequestedItems = it },
        onNavigationRequest = {},
        onTabPressed = {},
        onAttachFileRequest = {},
        onTextValidator = { true }
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
    // Sekarang menghapus teks hingga kosong memicu saran Priority, jadi overlay TIDAK null
    inputPanel.inputTextArea.text = ""
    inputPanel.inputTextArea.caretPosition = 0
    UIUtil.dispatchAllInvocationEvents()

    assertNotNull("Overlay harusnya muncul (Priority) saat teks kosong", lastRequestedItems)
  }

  fun testQuickTagsForNewUser() {
    // Override inputPanel dengan kondisi tag kosong
    inputPanel =
      TodosoInputPanel(
        onNewTask = {},
        onUpdateTask = {},
        onConfirmCancel = {},
        onCreateNote = {},
        onCancelEdit = {},
        fontInput = Font("Monospaced", Font.PLAIN, 12),
        onSuggestionProvider = { prefix ->
          listOf("feature", "bug")
            .filter { it.startsWith(prefix) }
            .map { createSuggestionItem(it, "Quick Tags") }
        },
        onSuggestionRequest = { lastRequestedItems = it },
        onNavigationRequest = {},
        onTabPressed = {},
        onAttachFileRequest = {},
        onTextValidator = { true }
      )

    inputPanel.inputTextArea.text = "#"
    inputPanel.inputTextArea.caretPosition = 1
    UIUtil.dispatchAllInvocationEvents()

    assertNotNull("Overlay harus muncul", lastRequestedItems)
    assertTrue("Harus berisi Quick Tags", lastRequestedItems!!.any { it.category == "Quick Tags" })
    assertTrue("Harus mengandung 'feature'", lastRequestedItems!!.any { it.text == "feature" })
  }

  fun testDeepSearchForNonPopularTags() {
    val nonPopularTag = "very-rare-tag"

    inputPanel =
      TodosoInputPanel(
        onNewTask = {},
        onUpdateTask = {},
        onConfirmCancel = {},
        onCreateNote = {},
        onCancelEdit = {},
        fontInput = Font("Monospaced", Font.PLAIN, 12),
        onSuggestionProvider = { prefix ->
          val popular = listOf("popular1", "popular2")
          val rare = listOf(nonPopularTag)
          
          val result = mutableListOf<SuggestionItem>()
          popular.filter { it.startsWith(prefix) }.forEach { result.add(createSuggestionItem(it, "Popular Tags")) }
          rare.filter { it.startsWith(prefix) }.forEach { result.add(createSuggestionItem(it, "All Tags")) }
          result
        },
        onSuggestionRequest = { lastRequestedItems = it },
        onNavigationRequest = {},
        onTabPressed = {},
        onAttachFileRequest = {},
        onTextValidator = { true }
      )

    // Cari prefix tag langka tersebut
    inputPanel.inputTextArea.text = "#very"
    inputPanel.inputTextArea.caretPosition = 5
    UIUtil.dispatchAllInvocationEvents()

    assertNotNull(lastRequestedItems)
    assertTrue("Harus menemukan tag langka lewat Deep Search", lastRequestedItems!!.any { it.text == nonPopularTag })
    assertEquals(
      "Kategori harus 'All Tags'",
      "All Tags",
      lastRequestedItems!!.find { it.text == nonPopularTag }?.category,
    )
  }

  fun testPrioritySuggestionOnlyInNormalMode() {
    var requestedItems: List<SuggestionItem>? = null

    lateinit var testPanel: TodosoInputPanel
    testPanel = TodosoInputPanel(
      onNewTask = {},
      onUpdateTask = {},
      onConfirmCancel = {},
      onCreateNote = {},
      onCancelEdit = {},
      fontInput = Font("Monospaced", Font.PLAIN, 12),
      onSuggestionProvider = { _ ->
        val currentText = testPanel.inputTextArea.text.trim()
        if (currentText.isEmpty()) {
          if (testPanel.currentMode is InputMode.Normal) {
            listOf(SuggestionItem("[H]", "Priority", type = SuggestionType.PRIORITY, tagDisplay = "HIGH"))
          } else {
            emptyList()
          }
        } else {
          emptyList()
        }
      },
      onSuggestionRequest = { requestedItems = it },
      onNavigationRequest = {},
      onTabPressed = {},
      onAttachFileRequest = {},
      onTextValidator = { true }
    )

    // Mode Edit: saat teks kosong, tidak boleh ada suggestion priority
    testPanel.setEditMode(true, "Task Lama")
    testPanel.inputTextArea.text = ""
    UIUtil.dispatchAllInvocationEvents()
    assertNull("Overlay tidak boleh terpicu untuk Priority di EditMode saat teks kosong", requestedItems)

    // Mode Note: saat teks kosong, tidak boleh ada suggestion priority
    testPanel.setNoteMode(true)
    testPanel.inputTextArea.text = ""
    UIUtil.dispatchAllInvocationEvents()
    assertNull("Overlay tidak boleh terpicu untuk Priority di NoteMode saat teks kosong", requestedItems)

    // Mode Normal: saat dikembalikan ke NormalMode dan teks kosong, suggestion priority harus terpicu
    testPanel.setMode(InputMode.Normal)
    testPanel.showSuggestionsPopup('!')
    UIUtil.dispatchAllInvocationEvents()
    assertNotNull("Overlay harus terpicu di NormalMode saat teks kosong", requestedItems)
    assertTrue(requestedItems!!.any { it.type == SuggestionType.PRIORITY })
  }

  fun testUntruncatedRelatedTaskSuggestions() {
    val longDescription =
      "Ini adalah deskripsi task yang sangat panjang lebih dari lima puluh karakter untuk memastikan tidak terpotong sama sekali"
    var selectedItem: SuggestionItem? = null

    val overlay = SuggestionOverlayPanel { item ->
      selectedItem = item
    }

    val relatedTaskItem = SuggestionItem(
      text = longDescription,
      category = "Related Tasks",
      isTask = true,
      taskId = "abc123",
      type = SuggestionType.TASK
    )

    overlay.updateItems(listOf(relatedTaskItem))
    UIUtil.dispatchAllInvocationEvents()

    assertEquals(longDescription, overlay.getSelected()?.text)
    overlay.confirmSelection()
    assertEquals(longDescription, selectedItem?.text)
  }
}
