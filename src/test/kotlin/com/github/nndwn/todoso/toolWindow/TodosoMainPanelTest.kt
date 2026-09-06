package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodosoMainPanelTest : BasePlatformTestCase() {

    fun testInjectInstructionsOnInitialization() {
        val todoFile = myFixture.addFileToProject(TodosoConstants.FILENAME, "Task 1\nTask 2")
        TodosoMainPanel(project)
        val content = VfsUtil.loadText(todoFile.virtualFile)
        val expectedHeader = TodosoBundle.message("todo.instruction.inject", TodosoConstants.GITHUB_REPO_URL)

        assertTrue("Instruction header should be present at the beginning of the file.\nContent was: $content",
            content.startsWith(expectedHeader))
        assertTrue("Original content should still be present.",
            content.contains("Task 1") && content.contains("Task 2"))
    }

    fun testNoInjectionIfHeaderAlreadyExists() {
        val header = TodosoBundle.message("todo.instruction.inject", TodosoConstants.GITHUB_REPO_URL)
        val originalContent = "$header\n\nTask 1"
        val todoFile = myFixture.addFileToProject(TodosoConstants.FILENAME, originalContent)
        TodosoMainPanel(project)
        val content = VfsUtil.loadText(todoFile.virtualFile)
        assertEquals("Content should not change if the header is already present.", originalContent, content)
    }
}
