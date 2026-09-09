package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.domain.model.TodoTask
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font

class TodosoTagSuggestionTest : BasePlatformTestCase() {

    private lateinit var inputPanel: TodosoInputPanel

    override fun setUp() {
        super.setUp()
        inputPanel = TodosoInputPanel(
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
        val method = TodosoInputPanel::class.java.getDeclaredMethod("getActivePrefix")
        method.isAccessible = true

        inputPanel.inputTextArea.text = "Fix this #fe"
        inputPanel.inputTextArea.caretPosition = inputPanel.inputTextArea.text.length
        assertEquals("fe", method.invoke(inputPanel))

        inputPanel.inputTextArea.text = "Normal text #"
        inputPanel.inputTextArea.caretPosition = inputPanel.inputTextArea.text.length
        assertEquals("", method.invoke(inputPanel))

        inputPanel.inputTextArea.text = "No hash here"
        inputPanel.inputTextArea.caretPosition = inputPanel.inputTextArea.text.length
        assertNull(method.invoke(inputPanel))

        inputPanel.inputTextArea.text = "Hash with space #feat "
        inputPanel.inputTextArea.caretPosition = inputPanel.inputTextArea.text.length
        assertNull(method.invoke(inputPanel))
    }

    fun testInsertItemAtCaretReplacesPrefix() {
        val method = TodosoInputPanel::class.java.getDeclaredMethod("insertItemAtCaret", String::class.java, Boolean::class.javaPrimitiveType)
        method.isAccessible = true

        inputPanel.inputTextArea.text = "New task #f"
        inputPanel.inputTextArea.caretPosition = inputPanel.inputTextArea.text.length
        
        // Simulasikan memilih tag "feature"
        method.invoke(inputPanel, "feature", false)
        
        assertEquals("Harusnya mengganti '#f' menjadi '#feature '", "New task #feature ", inputPanel.inputTextArea.text)
    }

    fun testInsertItemAtCaretReplacesPrefixWithTaskId() {
        val method = TodosoInputPanel::class.java.getDeclaredMethod("insertItemAtCaret", String::class.java, Boolean::class.javaPrimitiveType)
        method.isAccessible = true

        inputPanel.inputTextArea.text = "Reference task #feature"
        inputPanel.inputTextArea.caretPosition = inputPanel.inputTextArea.text.length
        
        // Simulasikan memilih tugas dengan ID "8XnWwK"
        method.invoke(inputPanel, "🆔 8XnWwK", true)
        
        assertEquals("Harusnya mengganti '#feature' menjadi '🆔 8XnWwK '", "Reference task 🆔 8XnWwK ", inputPanel.inputTextArea.text)
    }

    fun testShouldTriggerPopup() {
        val method = TodosoInputPanel::class.java.getDeclaredMethod("shouldTriggerPopup")
        method.isAccessible = true

        inputPanel.inputTextArea.text = ""
        inputPanel.inputTextArea.caretPosition = 0
        assertTrue("Awal baris harusnya memicu", method.invoke(inputPanel) as Boolean)

        inputPanel.inputTextArea.text = "word"
        inputPanel.inputTextArea.caretPosition = 4
        assertFalse("Tepat setelah kata tidak boleh memicu", method.invoke(inputPanel) as Boolean)

        inputPanel.inputTextArea.text = "word "
        inputPanel.inputTextArea.caretPosition = 5
        assertTrue("Setelah spasi harusnya memicu", method.invoke(inputPanel) as Boolean)
    }
}
