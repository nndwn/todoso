package com.github.nndwn.todoso.toolWindow.logic

import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.toolWindow.DateFilter
import com.github.nndwn.todoso.toolWindow.FilterState
import com.github.nndwn.todoso.toolWindow.SortOption
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

object TodoTaskFilterer {

  fun filterAndSort(
    allTasks: List<TodoTask>,
    filterState: FilterState,
    currentTagFilter: String?,
    sortOptions: Set<SortOption>
  ): List<TodoTask> {
    val filtered = getFilteredTasks(allTasks, filterState, currentTagFilter)
    return applySorting(filtered, sortOptions)
  }

  private fun getFilteredTasks(
    allTasks: List<TodoTask>,
    filterState: FilterState,
    currentTagFilter: String?
  ): List<TodoTask> {
    return allTasks.filter { task ->
      val priorityMatch = filterState.priority == null || task.priority == filterState.priority
      val statusMatch = filterState.status == null || task.status == filterState.status

      val activeTag = filterState.tag ?: currentTagFilter
      val tagMatch = activeTag == null || task.tags.contains(activeTag)

      val dateMatch = when (filterState.date) {
        DateFilter.TODAY -> isTaskMatchingDate(task) { it == LocalDate.now() }
        DateFilter.THIS_WEEK -> isTaskMatchingDate(task) { isDateInCurrentWeek(it) }
        DateFilter.WITH_DATE -> task.metadata.dueDate != null || task.metadata.startDate != null || task.metadata.createdDate != null
        else -> true
      }

      val queryMatch = if (filterState.query.isNullOrBlank()) true else {
        val query = filterState.query!!.lowercase()
        task.description.lowercase().contains(query) ||
                task.metadata.notes.lowercase().contains(query) ||
                task.id.lowercase().contains(query)
      }

      priorityMatch && statusMatch && tagMatch && dateMatch && queryMatch
    }
  }

  internal fun applySorting(tasks: List<TodoTask>, options: Set<SortOption>): List<TodoTask> {
    if (options.isEmpty()) return tasks
    val comparators = mutableListOf<Comparator<TodoTask>>()
    for (option in options) {
      when (option) {
        SortOption.STATUS -> comparators.add(compareBy { it.status })
        SortOption.DATE ->
          comparators.add(compareBy { it.metadata.dueDate ?: it.metadata.startDate ?: it.metadata.createdDate ?: "9999-99-99" })
        SortOption.PRIORITY -> comparators.add(compareBy { it.priority })
      }
    }
    if (comparators.isEmpty()) return tasks
    var finalComparator = comparators[0]
    for (i in 1 until comparators.size) {
      finalComparator = finalComparator.then(comparators[i])
    }
    return tasks.sortedWith(finalComparator)
  }

  private fun isTaskMatchingDate(task: TodoTask, predicate: (LocalDate) -> Boolean): Boolean {
    val dates = listOfNotNull(
      task.metadata.dueDate,
      task.metadata.startDate,
      task.metadata.createdDate
    )
    return dates.any { dateStr ->
      try {
        val date = LocalDate.parse(dateStr.take(10))
        predicate(date)
      } catch (_: Exception) {
        false
      }
    }
  }

  private fun isDateInCurrentWeek(date: LocalDate): Boolean {
    val now = LocalDate.now()
    val weekFields = WeekFields.of(Locale.getDefault())
    val currentWeek = now.get(weekFields.weekOfWeekBasedYear())
    val currentYear = now.get(weekFields.weekBasedYear())

    return date.get(weekFields.weekOfWeekBasedYear()) == currentWeek &&
            date.get(weekBasedYear()) == currentYear
  }

  private fun weekBasedYear() = WeekFields.of(Locale.getDefault()).weekBasedYear()
}
