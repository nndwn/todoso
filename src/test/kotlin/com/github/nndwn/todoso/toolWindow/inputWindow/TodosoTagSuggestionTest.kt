package com.github.nndwn.todoso.toolWindow.inputWindow

import com.github.nndwn.todoso.services.TodosoSuggestionService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font

class TodosoTagSuggestionTest : BasePlatformTestCase() {

    private lateinit var inputPanel: TodosoInputPanel
    private lateinit var suggestionService: TodosoSuggestionService

    override fun setUp() {
        super.setUp()
        suggestionService = project.service<TodosoSuggestionService>()
        inputPanel = TodosoInputPanel(
            project = project,
            onNewTask = {},
            onUpdateTask = {},
            onConfirmCancel = {},
            onCreateNote = {},
            onCancelEdit = {},
            fontInput = Font("Monospaced", Font.PLAIN, 12),
            getPopularTags = { listOf("feature", "bug", "core", "ui") },
            getAllTasks = { emptyList() }
        )
    }

    fun testGetActivePrefix() {
        assertEquals("fe", suggestionService.getActivePrefix("Fix this #fe", 12))
        assertEquals("", suggestionService.getActivePrefix("Normal text #", 13))
        assertNull("Tanpa simbol hash harusnya null", suggestionService.getActivePrefix("No hash here", 12))
        // Jika kursor tepat setelah #, prefix adalah "" (empty string)
        assertEquals("Caret tepat setelah # harusnya empty string", "", suggestionService.getActivePrefix("Hash with space #feat ", 17))
        // Jika kursor setelah spasi, harusnya null
        assertNull("Caret setelah spasi harusnya null", suggestionService.getActivePrefix("Hash with space #feat ", 22))
    }

    fun testInsertItemAtCaretReplacesPrefix() {
        inputPanel.inputTextArea.text = "New task #f"
        inputPanel.inputTextArea.caretPosition = inputPanel.inputTextArea.text.length
        
        inputPanel.insertItemAtCaret("feature", false)
        
        assertEquals("Harusnya mengganti '#f' menjadi '#feature '", "New task #feature ", inputPanel.inputTextArea.text)
    }

    fun testInsertItemAtCaretReplacesPrefixWithTaskId() {
        inputPanel.inputTextArea.text = "Reference task #feature"
        inputPanel.inputTextArea.caretPosition = inputPanel.inputTextArea.text.length
        
        inputPanel.insertItemAtCaret("🆔 8XnWwK", true)
        
        assertEquals("Harusnya mengganti '#feature' menjadi '🆔 8XnWwK '", "Reference task 🆔 8XnWwK ", inputPanel.inputTextArea.text)
    }

    fun testShouldTriggerPopup() {
        inputPanel.inputTextArea.text = ""
        inputPanel.inputTextArea.caretPosition = 0
        assertTrue("Awal baris harusnya memicu", inputPanel.shouldTriggerPopup())

        inputPanel.inputTextArea.text = "word"
        inputPanel.inputTextArea.caretPosition = 4
        assertFalse("Tepat setelah kata tidak boleh memicu", inputPanel.shouldTriggerPopup())

        inputPanel.inputTextArea.text = "word "
        inputPanel.inputTextArea.caretPosition = 5
        assertTrue("Setelah spasi harusnya memicu", inputPanel.shouldTriggerPopup())
    }
}
