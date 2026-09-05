package com.github.nndwn.todoso.domain.parser

import com.github.nndwn.todoso.domain.model.Metadata
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask

object TodoTaskParser {

    fun parseLine(
        rawLine: String,
        lineNumber: Int,
        usedIds: MutableSet<String> = mutableSetOf()
    ): TodoTask? {
        if (rawLine.isBlank()) return null

        val (contentBeforeComment, notesPart) = splitContentAndNotes(rawLine)

        val status = TaskStatus.parseFromLineStart(contentBeforeComment) ?: return null

        val priority = Priority.parseFromLine(contentBeforeComment)

        val extractedId = TaskIdParser.parseId(contentBeforeComment, usedIds)

        val tags = TagParser.parseTags(contentBeforeComment)

        val metadata = DateParser.parseDates(contentBeforeComment, notes = notesPart)

        val cleanDescription = extractCleanDescription(
            contentPart = contentBeforeComment,
            priority = priority,
            extractedId = extractedId.id,
            tags = tags,
            metadata = metadata
        )

        return TodoTask(
            id = extractedId.id,
            isPersistentId = extractedId.isPersistentId,
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
        tags: List<String>,
        metadata: Metadata
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

        clean = clean.replace(Regex("""🆔\s*$extractedId"""), "")

        val dateTokens = listOfNotNull(
            metadata.startDate?.let { "🛫 $it" },
            metadata.dueDate?.let { "📅 $it" },
            metadata.endDate?.let { "✅ $it" },
            metadata.cancelDate?.let { "❌ $it" },
            metadata.createdDate?.let { "➕ $it" }
        )
        dateTokens.forEach { token ->
            clean = clean.replace(token, "")
        }

        tags.forEach { tag ->
            clean = clean.replace(Regex("""(?<=\s|^)#$tag(?=\s||[.,!?]|$)"""), "")
        }

        return clean.replace(Regex("""\s+"""), " ").trim()
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
