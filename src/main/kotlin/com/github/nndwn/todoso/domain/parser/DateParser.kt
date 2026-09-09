package com.github.nndwn.todoso.domain.parser

import com.github.nndwn.todoso.domain.model.Metadata
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateParser {
    private const val DATE_TIME_PATTERN = """\d{4}-\d{2}-\d{2}(?:\s+\d{2}:\d{2})?"""

    private val START_REGEX = Regex("""${Metadata.ICON_START}\s*($DATE_TIME_PATTERN)""")
    private val DUE_REGEX = Regex("""${Metadata.ICON_DUE}\s*($DATE_TIME_PATTERN)""")
    private val END_REGEX = Regex("""${Metadata.ICON_DONE}\s*($DATE_TIME_PATTERN)""")
    private val CANCEL_REGEX = Regex("""${Metadata.ICON_CANCEL}\s*($DATE_TIME_PATTERN)""")
    private val CREATED_REGEX = Regex("""${Metadata.ICON_CREATED}\s*($DATE_TIME_PATTERN)""")
    private val EDITED_REGEX = Regex("""${Metadata.ICON_EDITED}\s*($DATE_TIME_PATTERN)""")

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun parseDates(input: String?, notes: String = ""): Metadata {
        if (input.isNullOrBlank()) return Metadata(notes = notes)

        val contentBeforeComment = stripComment(input)

        return Metadata(
            startDate = START_REGEX.find(contentBeforeComment)?.groupValues?.get(1),
            dueDate = DUE_REGEX.find(contentBeforeComment)?.groupValues?.get(1),
            endDate = END_REGEX.find(contentBeforeComment)?.groupValues?.get(1),
            cancelDate = CANCEL_REGEX.find(contentBeforeComment)?.groupValues?.get(1),
            createdDate = CREATED_REGEX.find(contentBeforeComment)?.groupValues?.get(1),
            editedDate = EDITED_REGEX.find(contentBeforeComment)?.groupValues?.get(1),
            notes = notes
        )
    }

    fun calculateDuration(metadata: Metadata): String? {
        val startStr = metadata.startDate ?: return null
        val endStr = metadata.endDate ?: return null

        return try {
            val start = parseFlexibleDateTime(startStr)
            val end = parseFlexibleDateTime(endStr)
            val duration = Duration.between(start, end)

            if (duration.isNegative) return null

            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60

            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFlexibleDateTime(text: String): LocalDateTime {
        return try {
            LocalDateTime.parse(text, DATE_FORMATTER)
        } catch (_: Exception) {
            LocalDateTime.parse("$text 00:00", DATE_FORMATTER)
        }
    }
}