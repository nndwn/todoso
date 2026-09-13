package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus

enum class SortOption(val key: String) {
  PRIORITY("PRIORITY"),
  STATUS("STATUS"),
  DATE("DATE");

  companion object {
    fun fromKey(key: String): SortOption? = entries.find { it.key == key }
  }
}

enum class DateFilter {
    TODAY, THIS_WEEK, WITH_DATE
}

enum class FilterType {
    PRIORITY, STATUS, DATE, TAG, SEARCH, RESET_ALL
}

data class FilterState(
    var priority: Priority? = null,
    var status: TaskStatus? = null,
    var date: DateFilter? = null,
    var tag: String? = null,
    var query: String? = null
)
