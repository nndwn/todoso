package com.github.nndwn.todoso

import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase


class TodoFileParserTest : BasePlatformTestCase() {


        fun testBatchParsingFromVirtualFile() {
                val dummyMarkdownContent = """
            - [ ] Valid Todo Task
            - [/] Valid Doing Task
            - [x] Valid Done Task
            - [-] Valid Cancelled Task
               - [X] Indented Subtask with capital X
            -[] Valid Todo without space inside brackets
            > - [ ] Invalid: Blockquote task should be ignored
            \- [ ] Invalid: Escaped dash line should be ignored
            - [?] Invalid: Unknown status character should be ignored
            - [a] Invalid: Alphabet status character should be ignored
            This is a plain text description - [ ] with embedded brackets
            Fix bug - [x] at the end of line
            - [ ] 🔺 Highest via Emoji
            - [ ] [H] High via short code
            - [ ] [h] High via lowercase short code
            - [ ] [Highest] Highest via full label
            - [ ] [higHest] Highest via mixed case label
            - [ ] [          h] High via code with leading spaces
            - [ ] [      H    ] High via code with padded spaces
            - [ ] 🔼 Medium via Obsidian Emoji
            - [ ] Fix High priority issue in description
            - [ ] Regular task without priority
        """.trimIndent()

                val virtualFile = LightVirtualFile("todo.md", dummyMarkdownContent)
                val lines = virtualFile.inputStream.bufferedReader().readLines()

                val expectedStatuses = mapOf(
                        0 to TaskStatus.TODO,
                        1 to TaskStatus.DOING,
                        2 to TaskStatus.DONE,
                        3 to TaskStatus.CANCELLED,
                        4 to TaskStatus.DONE,
                        5 to TaskStatus.TODO,
                        6 to null,
                        7 to null,
                        8 to null,
                        9 to null,
                        10 to null,
                        11 to null
                )


                val expectedPriorities = mapOf(
                        12 to Priority.HIGHEST,
                        13 to Priority.HIGH,
                        14 to Priority.HIGH,
                        15 to Priority.HIGHEST,
                        16 to Priority.HIGHEST,
                        17 to Priority.HIGH,
                        18 to Priority.HIGH,
                        19 to Priority.MEDIUM,
                        20 to Priority.NONE,
                        21 to Priority.NONE
                )

                expectedStatuses.forEach { (lineIndex, expectedStatus) ->
                        assertEquals("Failed at line $lineIndex", expectedStatus, TaskStatus.parseFromLineStart(lines[lineIndex]))
                }

                expectedPriorities.forEach { (lineIndex, expectedPriority) ->
                        assertEquals("Failed at line $lineIndex", expectedPriority, Priority.parseFromLine(lines[lineIndex]))
                }
        }
}