package com.github.nndwn.todoso.domain.parser

import com.github.nndwn.todoso.domain.model.TodoTask

object TagParser {
  val TAG_REGEX = Regex("""(?<=\s|^)#(?!\d+(?:\s|$|[.,!?]))([\p{L}\p{N}_/#.-]*[\p{L}\p{N}_/#-]|C#|F#)""")

  fun parseTags(input: String?): List<String> {
    if (input.isNullOrBlank()) return emptyList()

    val contentBeforeComment = stripComment(input)

    return TAG_REGEX.findAll(contentBeforeComment)
      .map { it.groupValues[1] }
      .filter { it.isNotEmpty() }
      .distinct()
      .toList()
  }

  fun getPopularTags(tasks: List<TodoTask>, limit: Int = 10): List<String> {
    return tasks
      .asSequence()
      .flatMap { task -> task.tags.map { tag -> tag.removePrefix("#") to task } }
      .groupBy({ it.first }, { it.second })
      .map { (tag, taskList) ->
        val frequency = taskList.size
        val latestDate =
          taskList
            .mapNotNull {
              it.metadata.endDate ?: it.metadata.startDate ?: it.metadata.createdDate
            }
            .maxOrNull() ?: ""

        Triple(tag, frequency, latestDate)
      }
      .sortedWith(compareByDescending<Triple<String, Int, String>> { it.second }.thenByDescending { it.third })
      .map { it.first }
      .take(limit)
      .toList()
  }

  fun getRecentVersions(limit: Int = 3, tasks: List<TodoTask>): List<String> {
    return tasks
      .asSequence()
      .flatMap { it.tags }
      .filter { it.startsWith("v", ignoreCase = true) }
      .distinct()
      .sortedDescending()
      .take(limit)
      .toList()
  }
}
