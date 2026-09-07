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
        var clean = contentPart

        clean = clean.replaceFirst(Regex("""^\s*-\s*\[[\s/xX-]?]"""), "")

        if (priority != Priority.NONE) {
            if (priority.emoji.isNotEmpty()) {
                clean = clean.replaceFirst(priority.emoji, "")
            }
            if (priority.code.isNotEmpty()) {
                clean = clean.replaceFirst(
                    Regex(
                        """\[\s*${priority.code}\s*]""",
                        RegexOption.IGNORE_CASE
                    ), ""
                )
            }
            if (priority.label.isNotEmpty()) {
                clean = clean.replaceFirst(
                    Regex(
                        """\[\s*${priority.label}\s*]""",
                        RegexOption.IGNORE_CASE
                    ), ""
                )
            }
        }

        if (!ignoreId) {
            val matchResult = TaskIdParser.TASK_ID_REGEX.findAll(clean).lastOrNull()
            if (matchResult != null && matchResult.groupValues[1] == extractedId) {
                val remaining = clean.substring(matchResult.range.last + 1).trim()
                if (remaining.isEmpty()) {
                    clean = clean.substring(0, matchResult.range.first).trimEnd()
                }
            }
        }

        // Remove ONLY date tokens at the end. 
        // Tags are preserved in description regardless of position per user request.
        
        val dateTokens = metadata.toEmojiTokens()
        
        fun removeEndMetadata() {
            var changed = true
            while (changed) {
                changed = false
                val current = clean.trimEnd()
                
                // Try remove date token at end
                for (token in dateTokens) {
                    if (current.endsWith(token)) {
                        clean = current.substring(0, current.length - token.length).trimEnd()
                        changed = true
                        break
                    }
                }
            }
        }

        removeEndMetadata()

        return clean.replace(Regex("""[ \t]+"""), " ").trim()
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
