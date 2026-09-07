package com.github.nndwn.todoso.domain.parser

import com.github.nndwn.todoso.domain.model.ExtractedId

object TaskIdParser {
    internal val TASK_ID_REGEX = Regex("""🆔\s*([a-zA-Z0-9]{3,12})""")
    private val ALPHA_NUMERIC_CHARS = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun parseId(input: String?, usedIds: MutableSet<String> = mutableSetOf()): ExtractedId {
        if (input.isNullOrBlank()) {
            return generateUniqueInMemoryId(usedIds)
        }

        val contentBeforeComment = stripComment(input)
        val matchResult = TASK_ID_REGEX.find(contentBeforeComment)

        if (matchResult != null) {
            val extractedId = matchResult.groupValues[1]
            if (usedIds.add(extractedId)) {
                return ExtractedId(id = extractedId, isPersistentId = true)
            }
        }

        return generateUniqueInMemoryId(usedIds)
    }

    private fun generateUniqueInMemoryId(usedIds: MutableSet<String>): ExtractedId {
        var newId: String
        do {
            newId = (1..6).map { ALPHA_NUMERIC_CHARS.random() }.joinToString("")
        } while (!usedIds.add(newId))

        return ExtractedId(id = newId, isPersistentId = false)
    }
}