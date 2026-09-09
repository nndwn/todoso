package com.github.nndwn.todoso

import com.github.nndwn.todoso.domain.parser.DateParser
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.domain.parser.TaskIdParser
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTaskBuilder
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TodoFileParserTest : BasePlatformTestCase() {


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


    fun testTagParsing() {
        val cases = mapOf(
            "- [ ] #ui #project/frontend #C# #F#" to listOf("ui", "project/frontend", "C#", "F#"),
            "- [ ] #issue, and #core." to listOf("issue", "core"),
            "- [ ] Visit https://github.com/nndwn/todoso#readme" to emptyList(),
            "- [ ] Email user#domain or normal text#anchor" to emptyList(),
            "- [ ] #v1.0.1." to listOf("v1.0.1"),
            "- [ ] #123 numeric" to emptyList(),
            "- [ ] #core // #note in comment" to listOf("core")
        )

        cases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, TagParser.parseTags(input))
        }
    }


    fun testTaskIdParsing() {
        val usedIds = mutableSetOf<String>()
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

    fun testFullTaskParsing() {
        val rawLine = "- [ ] Visit https://github.com/nndwn/todoso#readme #ui 🆔 8x2k1a // check details"

        val usedIds = mutableSetOf<String>()
        val task = TodoTaskParser.parseLine(rawLine, 10, usedIds, false)

        assertNotNull(task)
        task?.let {
            assertEquals("8x2k1a", it.id)
            assertTrue(it.isPersistentId)
            assertEquals(TaskStatus.TODO, it.status)
            assertEquals(listOf("ui"), it.tags)
            assertEquals("check details", it.metadata.notes)
            assertEquals("Visit https://github.com/nndwn/todoso#readme #ui", it.description)
            assertEquals(10, it.lineNumber)
        }
    }

    fun testNewlineHandling() {
        // 1. Kasus Normal: Baris baru di deskripsi
        val raw1 = "- [ ] Line 1\\nLine 2 🆔 abc"
        val task1 = TodoTaskParser.parseLine(raw1, 1, mutableSetOf())
        assertEquals("Line 1\nLine 2", task1?.description)

        // 2. Kasus Ekstrem: Beberapa baris baru berurutan
        val raw2 = "- [ ] Multiple\\n\\nNewlines 🆔 def"
        val task2 = TodoTaskParser.parseLine(raw2, 1, mutableSetOf())
        assertEquals("Multiple\n\nNewlines", task2?.description)

        // 3. Verifikasi Pembangunan Kembali (Rebuild)
        val rebuilt = TodoTaskBuilder.rebuildTaskLine(task2!!)
        assertTrue("Harus mengandung literal \\n\\n", rebuilt.contains("Multiple\\n\\nNewlines"))
        
        // 4. Kasus Campuran: Baris baru + Tag + Notes
        val raw3 = "- [ ] Task\\nWith Tag #work 🆔 ghi // Note\\nHere"
        val task3 = TodoTaskParser.parseLine(raw3, 1, mutableSetOf())
        assertEquals("Task\nWith Tag #work", task3?.description)
        assertEquals("Note\nHere", task3?.metadata?.notes)

        // 5. Verifikasi Pembangunan Kembali (Rebuild) untuk Note
        val rebuilt3 = TodoTaskBuilder.rebuildTaskLine(task3!!)
        assertTrue("Harus mengandung literal \\n di bagian Note", rebuilt3.contains("// Note\\nHere"))
    }

    fun testEditedDateParsingAndBuilding() {
        // 1. Test Parsing: Pastikan 📝 (Edited Date) terbaca
        val rawLine = "- [ ] Task Description 🆔 abc ➕ 2026-09-09 10:00 📝 2026-09-09 18:00"
        val task = TodoTaskParser.parseLine(rawLine, 1, mutableSetOf())
        
        assertNotNull(task)
        assertEquals("Task Description", task?.description)
        assertEquals("2026-09-09 10:00", task?.metadata?.createdDate)
        assertEquals("2026-09-09 18:00", task?.metadata?.editedDate)

        // 2. Test Building: Pastikan 📝 ikut tertulis kembali
        val rebuilt = TodoTaskBuilder.rebuildTaskLine(task!!)
        assertTrue("Hasil rebuild harus mengandung emoji edited 📝", rebuilt.contains("📝 2026-09-09 18:00"))
        
        // 3. Test Dynamic Tail Removal: Posisi berbeda
        val rawDifferentOrder = "- [ ] Clean Me 📝 2026-09-09 18:00 🆔 abc"
        val taskDifferent = TodoTaskParser.parseLine(rawDifferentOrder, 1, mutableSetOf())
        assertEquals("Clean Me", taskDifferent?.description)
    }
}
