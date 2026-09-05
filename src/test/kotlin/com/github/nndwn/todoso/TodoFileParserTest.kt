package com.github.nndwn.todoso

import com.github.nndwn.todoso.domain.parser.DateParser
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.domain.parser.TaskIdParser
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodoFileParserTest : BasePlatformTestCase() {

    // --- Task Status Tests ---

    fun testTaskStatusParsing() {
        val validCases = mapOf(
            "- [ ] Task" to TaskStatus.TODO,
            "- [/] Task" to TaskStatus.DOING,
            "- [x] Task" to TaskStatus.DONE,
            "- [-] Task" to TaskStatus.CANCELLED,
            "   - [X] Indented" to TaskStatus.DONE,
            "-[] No space" to TaskStatus.TODO
        )

        validCases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, TaskStatus.parseFromLineStart(input))
        }

        val invalidCases = listOf(
            "> - [ ] Blockquote",
            "\\- [ ] Escaped",
            "- [?] Unknown",
            "- [a] Alphabet",
            "Plain text - [ ]",
            "Fix bug - [x]"
        )

        invalidCases.forEach { input ->
            assertNull("Should be null for input: $input", TaskStatus.parseFromLineStart(input))
        }
    }

    // --- Priority Tests ---

    fun testPriorityParsing() {
        val cases = mapOf(
            "- [ ] 🔺 Highest Emoji" to Priority.HIGHEST,
            "- [ ] [H] High Code" to Priority.HIGH,
            "- [ ] [h] Lowercase Code" to Priority.HIGH,
            "- [ ] [Highest] Label" to Priority.HIGHEST,
            "- [ ] [higHest] Mixed Case" to Priority.HIGHEST,
            "- [ ] [   h] Leading space" to Priority.HIGH,
            "- [ ] [  H  ] Padded" to Priority.HIGH,
            "- [ ] 🔼 Medium Emoji" to Priority.MEDIUM,
            "- [ ]🔺No space after status" to Priority.HIGHEST
        )

        cases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, Priority.parseFromLine(input))
        }

        val negativeCases = listOf(
            "- [ ] Regular task",
            "- [ ] [URGENT] Unknown",
            "- [ ] Fix graph 🔺 in middle",
            "- [ ] Fix High priority issue in description"
        )

        negativeCases.forEach { input ->
            assertEquals("Should be NONE for input: $input", Priority.NONE, Priority.parseFromLine(input))
        }
    }

    // --- Tag Tests ---

    fun testTagParsing() {
        val cases = mapOf(
            "- [ ] #ui #project/frontend #C# #F#" to listOf("ui", "project/frontend", "C#", "F#"),
            "- [ ] #issue, and #core." to listOf("issue", "core"),
            "- [ ] Visit https://github.com/nndwn/todoso#readme" to emptyList<String>(),
            "- [ ] Email user#domain or normal text#anchor" to emptyList<String>(),
            "- [ ] #v1.0.1." to listOf("v1.0.1"),
            "- [ ] #123 numeric" to emptyList<String>(),
            "- [ ] #core // #note in comment" to listOf("core")
        )

        cases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, TagParser.parseTags(input))
        }
    }

    // --- Task ID Tests ---

    fun testTaskIdParsing() {
        val usedIds = mutableSetOf<String>()

        // Persistent ID parsing
        val res1 = TaskIdParser.parseId("- [ ] 🆔 8x2k1a #ui", usedIds)
        assertEquals("8x2k1a", res1.id)
        assertTrue(res1.isPersistentId)

        val res2 = TaskIdParser.parseId("- [ ] 🆔a1b2c3", usedIds)
        assertEquals("a1b2c3", res2.id)
        assertTrue(res2.isPersistentId)

        // Duplicate ID should trigger fallback to in-memory ID
        val resDuplicate = TaskIdParser.parseId("- [ ] 🆔 8x2k1a", usedIds)
        assertFalse("Duplicate ID should not be persistent", resDuplicate.isPersistentId)
        assertNotSame("8x2k1a", resDuplicate.id)

        // ID in comment should be ignored
        val resComment = TaskIdParser.parseId("- [ ] Task // check 🆔 999999", usedIds)
        assertFalse("ID in comment should not be persistent", resComment.isPersistentId)
        assertEquals(6, resComment.id.length)
    }

    // --- Date and Duration Tests ---

    fun testDateAndDurationParsing() {
        // Parse dates with time
        val meta1 = DateParser.parseDates("- [/] Task 🛫 2026-09-06 09:30 ✅ 2026-09-06 11:15")
        assertEquals("2026-09-06 09:30", meta1.startDate)
        assertEquals("2026-09-06 11:15", meta1.endDate)
        assertEquals("1h 45m", DateParser.calculateDuration(meta1))

        // Parse dates without time
        val meta2 = DateParser.parseDates("- [ ] Task 🛫 2026-09-06 ✅ 2026-09-06")
        assertEquals("2026-09-06", meta2.startDate)
        assertEquals("2026-09-06", meta2.endDate)
        assertEquals("0m", DateParser.calculateDuration(meta2))

        // Duration with only start or end should be null
        val meta3 = DateParser.parseDates("- [ ] 🛫 2026-09-06")
        assertNull(DateParser.calculateDuration(meta3))
    }

    // --- Full Integration Tests ---

    fun testFullTaskParsing() {
        val rawLine = "- [ ] Visit https://github.com/nndwn/todoso#readme #ui 🆔 8x2k1a // check details"

        // Buat set untuk menampung ID agar collision detection berjalan tepat
        val usedIds = mutableSetOf<String>()
        val task = TodoTaskParser.parseLine(rawLine, lineNumber = 10, usedIds = usedIds)

        assertNotNull(task)
        task?.let {
            assertEquals("8x2k1a", it.id)
            assertTrue(it.isPersistentId)
            assertEquals(TaskStatus.TODO, it.status)
            assertEquals(listOf("ui"), it.tags)
            assertEquals("check details", it.metadata.notes)
            assertEquals("Visit https://github.com/nndwn/todoso#readme", it.description)
            assertEquals(10, it.lineNumber)
        }
    }
}
