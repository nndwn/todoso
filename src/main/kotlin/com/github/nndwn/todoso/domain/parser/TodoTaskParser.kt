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

        return clean.replace(Regex("""[ \t]+"""), " ").trim()
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
        var clean = text
        var changed = true
        while (changed) {
            changed = false
            val current = clean.trimEnd()

            val cleanAfterId = tryRemoveIdTail(current, extractedId, ignoreId)
            if (cleanAfterId != null) {
                clean = cleanAfterId
                changed = true
                continue
            }

            val cleanAfterDate = tryRemoveDateTail(current, dateTokens)
            if (cleanAfterDate != null) {
                clean = cleanAfterDate
                changed = true
            }
        }
        return clean
    }

    private fun tryRemoveIdTail(text: String, extractedId: String, ignoreId: Boolean): String? {
        if (ignoreId || extractedId.isBlank()) return null
        val idTailRegex = Regex("""\s*🆔\s*${Regex.escape(extractedId)}\s*$""")
        return if (idTailRegex.containsMatchIn(text)) {
            text.replace(idTailRegex, "").trimEnd()
        } else null
    }

    private fun tryRemoveDateTail(text: String, dateTokens: List<String>): String? {
        for (token in dateTokens) {
            val dateTailRegex = Regex("""\s*${Regex.escape(token)}\s*$""")
            if (dateTailRegex.containsMatchIn(text)) {
                return text.replace(dateTailRegex, "").trimEnd()
            }
        }
        return null
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
        rawLine.substring(content.length + 2).trim()
    } else {
        ""
    }
    return content to notes
}