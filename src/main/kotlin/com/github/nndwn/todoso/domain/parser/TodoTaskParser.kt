package com.github.nndwn.todoso.domain.parser

import com.github.nndwn.todoso.domain.model.Metadata
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask

object TodoTaskParser {

    fun parseLine(
        rawLine: String,
        lineNumber: Int,
        usedIds: MutableSet<String> = mutableSetOf(),
        ignoreId: Boolean = false
    ): TodoTask? {
        if (rawLine.isBlank()) return null

        val (contentBeforeComment, notesPart) = splitContentAndNotes(rawLine)

        val status = TaskStatus.parseFromLineStart(contentBeforeComment) ?: return null
        val priority = Priority.parseFromLine(contentBeforeComment)

        val extractedId = if (ignoreId) {
            TaskIdParser.parseId(null, usedIds)
        } else {
            TaskIdParser.parseId(contentBeforeComment, usedIds)
        }

        val tags = TagParser.parseTags(contentBeforeComment)
        val metadata = DateParser.parseDates(contentBeforeComment, notes = notesPart)

        val cleanDescription = extractCleanDescription(
            contentPart = contentBeforeComment,
            priority = priority,
            extractedId = extractedId.id,
            metadata = metadata,
            ignoreId = ignoreId
        )

        return TodoTask(
            id = extractedId.id,
            isPersistentId = extractedId.isPersistentId || !ignoreId,
            rawText = rawLine,
            description = cleanDescription,
            status = status,
            priority = priority,
            tags = tags,
            lineNumber = lineNumber,
            metadata = metadata
        )
    }

    private fun extractCleanDescription(
        contentPart: String,
        priority: Priority,
        extractedId: String,
        metadata: Metadata,
        ignoreId: Boolean = false
    ): String {
        var clean = contentPart.replaceFirst(Regex("""^\s*-\s*\[[\s/xX-]?]"""), "")

        clean = removePriorityMarkers(clean, priority)
        clean = removeTailMarkers(clean, extractedId, metadata.toEmojiTokens(), ignoreId)

        return clean.replace(Regex("""[ \t]+"""), " ").replace("\\n", "\n").trim()
    }

    private fun removePriorityMarkers(text: String, priority: Priority): String {
        if (priority == Priority.NONE) return text
        var result = text
        if (priority.emoji.isNotEmpty()) {
            result = result.replaceFirst(priority.emoji, "")
        }
        if (priority.code.isNotEmpty()) {
            result = result.replaceFirst(Regex("""\[\s*${priority.code}\s*]""", RegexOption.IGNORE_CASE), "")
        }
        if (priority.label.isNotEmpty()) {
            result = result.replaceFirst(Regex("""\[\s*${priority.label}\s*]""", RegexOption.IGNORE_CASE), "")
        }
        return result
    }

    private fun removeTailMarkers(
        text: String,
        extractedId: String,
        dateTokens: List<String>,
        ignoreId: Boolean
    ): String {
        var clean = text.trimEnd()
        var changed = true

        // Kumpulkan semua token yang mungkin ada di ekor (ID dan Date Emojis)
        val allTokens = mutableListOf<String>()
        if (!ignoreId && extractedId.isNotBlank()) {
            allTokens.add("🆔 $extractedId")
            allTokens.add("🆔$extractedId")
        }
        allTokens.addAll(dateTokens)

        // Loop terus selama kita masih menemukan token di akhir kalimat
        while (changed) {
            changed = false
            for (token in allTokens) {
                val tokenRegex = Regex("""\s*${Regex.escape(token)}\s*$""")
                if (tokenRegex.containsMatchIn(clean)) {
                    clean = clean.replace(tokenRegex, "").trimEnd()
                    changed = true
                }
            }
        }
        return clean
    }
}

internal fun stripComment(input: String): String {
    var searchIndex = 0
    while (true) {
        val commentIndex = input.indexOf("//", startIndex = searchIndex)
        if (commentIndex == -1) return input

        val isUrlProtocol =
            commentIndex >= 5 && input.substring(commentIndex - 5, commentIndex) == "http:" ||
                    commentIndex >= 6 && input.substring(commentIndex - 6, commentIndex) == "https:"

        if (isUrlProtocol) {
            searchIndex = commentIndex + 2
        } else {
            return input.substring(0, commentIndex)
        }
    }
}

internal fun splitContentAndNotes(rawLine: String): Pair<String, String> {
    val content = stripComment(rawLine)
    val notes = if (content.length < rawLine.length) {
        rawLine.substring(content.length + 2).trim().replace("\\n", "\n")
    } else {
        ""
    }
    return content to notes
}