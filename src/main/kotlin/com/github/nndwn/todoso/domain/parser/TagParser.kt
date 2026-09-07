package com.github.nndwn.todoso.domain.parser

object TagParser {
    val TAG_REGEX = Regex("""(?<=\s|^)#(?!\d+(?:\s|$|[.,!?]))([\w/#.-]*[\w/#-]|C#|F#)""")

    fun parseTags(input: String?): List<String> {
        if (input.isNullOrBlank()) return emptyList()

        val contentBeforeComment = stripComment(input)

        return TAG_REGEX.findAll(contentBeforeComment)
            .map { it.groupValues[1] }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}