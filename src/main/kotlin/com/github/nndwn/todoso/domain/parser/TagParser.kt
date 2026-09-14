package com.github.nndwn.todoso.domain.parser

import com.github.nndwn.todoso.domain.model.TodoTask

object TagParser {
  val TAG_REGEX = Regex("""(?<=\s|^)#(?!\d+(?:\s|$|[.,!?]))([\p{L}\p{N}_/#.-]*[\p{L}\p{N}_/#-]|C#|F#)""")
  val VERSION_REGEX = Regex("""^v\d.*""", RegexOption.IGNORE_CASE)

  fun parseTags(input: String?): List<String> {
    if (input.isNullOrBlank()) return emptyList()

    val contentBeforeComment = stripComment(input)

    return TAG_REGEX.findAll(contentBeforeComment)
      .map { it.groupValues[1] }
      .filter { it.isNotEmpty() }
      .distinct()
      .toList()
  }

  fun truncateTag(tag: String): String = if (tag.length > 20) tag.take(17) + "..." else tag

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
      .filter { it.matches(VERSION_REGEX) }
      .distinct()
      .sortedDescending()
      .take(limit)
      .toList()
  }

  /**
   * Membuat label ber-format untuk Tag beserta jumlah task-nya (jika jumlah > 0).
   * Contoh: "feature" -> "#feature (5)", atau "production" -> "#production" jika kosong.
   */
  fun formatTagWithCount(tag: String, tasks: List<TodoTask>, isTruncated: Boolean = false): String {
    val cleanTag = tag.removePrefix("#")
    val count = tasks.count { task ->
      task.tags.any { it.removePrefix("#").equals(cleanTag, ignoreCase = true) }
    }
    val displayTag = if (isTruncated) truncateTag(cleanTag) else cleanTag
    return if (count > 0) "#$displayTag ($count)" else "#$displayTag"
  }
}
