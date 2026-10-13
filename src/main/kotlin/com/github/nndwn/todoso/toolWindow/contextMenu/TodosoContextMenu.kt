package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.SortOption
import com.github.nndwn.todoso.toolWindow.TodosoActionHandler
import com.github.nndwn.todoso.toolWindow.inputWindow.InputMode
import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.actionSystem.CommonShortcuts

class TodosoContextMenu(
  private val service: TodosoService,
  private val settings: TodosoSettingsService,
  private val handler: TodosoActionHandler,
) {
  fun build(): List<TodosoMenuElement> {
    val rawSelectedTasks = handler.getSelectedTasks()
    val selectedTasks = rawSelectedTasks.map { service.findTaskById(it.id) ?: it }

    return buildTodosoMenu {
      if (selectedTasks.isNotEmpty()) {
        editorTask(selectedTasks)
        separator()
      }
      bookmarks(selectedTasks)
      separator()
      toolTask(selectedTasks)
      separator()
      visualTask()
      separator()
      sortTask()
    }
  }

  private fun TodoMenuBuilder.bookmarks(selectedTasks: List<TodoTask>) {
    val isSingleTask = selectedTasks.size == 1
    val primaryTask = selectedTasks.firstOrNull()?.let { service.findTaskById(it.id) ?: it }
    val isNormalMode = { handler.getCurrentMode() is InputMode.Normal }
    val projectBookmarks = TodosoBookmarkHelper.getProjectBookmarks(service.project)
    val hasBookmarks = projectBookmarks.isNotEmpty()
    val isBookmarkValid = isSingleTask && primaryTask != null && TodosoBookmarkHelper.isAttachedBookmarkValid(primaryTask, service.project)

    subMenu(
      text = TodosoBundle.message("todo.menu.bookmark.Insert"),
      icon = AllIcons.Nodes.BookmarkGroup,
      isEnabled = { selectedTasks.isNotEmpty() && hasBookmarks },
      isVisible = isNormalMode,
    ) {
      projectBookmarks.forEach { bookmark ->
        val lineBookmark = bookmark as? LineBookmark
        val file = lineBookmark?.file
        val lineDisplay = lineBookmark?.line?.plus(1) ?: 1
        val label = if (file != null) "${file.name}:$lineDisplay" else "Bookmark"

        item(
          text = label,
          icon = AllIcons.Nodes.BookmarkGroup,
          onAction = {
            val raw = handler.getSelectedTasks().ifEmpty { selectedTasks }
            val tasksToAttach = raw.map { service.findTaskById(it.id) ?: it }
            tasksToAttach.forEach { task ->
              handler.handleAttachBookmark(task, bookmark)
            }
          },
        )
      }
    }

    item(
      text = TodosoBundle.message("todo.menu.bookmark.go"),
      icon = AllIcons.Actions.TraceInto,
      isEnabled = { isSingleTask && isBookmarkValid },
      isVisible = isNormalMode,
      onAction = {
        val task = handler.getSelectedTask()?.let { service.findTaskById(it.id) ?: it } ?: primaryTask
        if (task != null) {
          handler.handleGoToBookmark(task)
        }
      },
    )
  }

  private fun TodoMenuBuilder.editorTask(selectedTasks: List<TodoTask>) {
    val isSingleTask = selectedTasks.size == 1
    val primaryTask = selectedTasks.first().let { service.findTaskById(it.id) ?: it }
    val isNormalMode = { handler.getCurrentMode() is InputMode.Normal }

    subMenu(
      text = TodosoBundle.message("todo.menu.change.status"),
      icon = AllIcons.Actions.Diff,
      isVisible = isNormalMode,
    ) {
      TaskStatus.entries.forEach { status ->
        item(
          text = status.displayName,
          icon = status.icon,
          isEnabled = {
            val raw = handler.getSelectedTasks().ifEmpty { selectedTasks }
            val tasks = raw.map { service.findTaskById(it.id) ?: it }
            tasks.any { handler.canTransitionTo(it, status) }
          },
        ) {
          val raw = handler.getSelectedTasks().ifEmpty { selectedTasks }
          val tasks = raw.map { service.findTaskById(it.id) ?: it }
          tasks.forEach { handler.updateTaskStatus(it, status) }
        }
      }
    }

    subMenu(
      text = TodosoBundle.message("todo.menu.change.priority"),
      icon = AllIcons.General.ChevronUp,
      isVisible = isNormalMode,
    ) {
      Priority.entries.forEach { priority ->
        item(
          text = priority.displayName,
          icon = priority.icon,
          isEnabled = {
            val raw = handler.getSelectedTasks().ifEmpty { selectedTasks }
            val tasks = raw.map { service.findTaskById(it.id) ?: it }
            tasks.any { it.priority != priority }
          },
          onAction = {
            val raw = handler.getSelectedTasks().ifEmpty { selectedTasks }
            val tasks = raw.map { service.findTaskById(it.id) ?: it }
            tasks.forEach { handler.handleUpdatePriority(it, priority) }
          },
        )
      }
    }

    subMenu(
      text = TodosoBundle.message("todo.menu.manage.tags"),
      icon = AllIcons.Nodes.Tag,
      isEnabled = { isSingleTask },
      isVisible = isNormalMode,
    ) {
      val taskData = service.loadTask()
      val exclusiveRelations = TodosoConstants.EXCLUSIVE_RELATIONS
      val exclusiveTags = exclusiveRelations.flatten()

      // 1. Exclusive Tag Groups
      exclusiveRelations.forEach { group ->
        group.forEach { tag ->
          toggle(
            text = TagParser.formatTagWithCount(tag, taskData),
            isSelected = {
              val currentTask = service.findTaskById(primaryTask.id) ?: primaryTask
              currentTask.tags.contains(tag)
            },
            onToggle = {
              val currentTask = service.findTaskById(primaryTask.id) ?: primaryTask
              handler.handleToggleTag(currentTask, tag)
            },
          )
        }
        separator()
      }

      // 3. Recent Versions
      val recentVersions = TagParser.getRecentVersions(tasks = taskData)

      // 2. Recent Tags (Excluding Exclusives and Recent Versions)
      val recentTags = TagParser.getRecentTags(taskData, limit = 50)
        .filter { it !in exclusiveTags && it !in recentVersions }
        .take(10)

      if (recentTags.isNotEmpty()) {
        subMenu(TodosoBundle.message("todo.suggestion.recent.tags")) {
          recentTags.forEach { tag ->
            toggle(
              text = TagParser.formatTagWithCount(tag, taskData, isTruncated = true),
              isSelected = {
                val currentTask = service.findTaskById(primaryTask.id) ?: primaryTask
                currentTask.tags.contains(tag)
              },
              onToggle = {
                val currentTask = service.findTaskById(primaryTask.id) ?: primaryTask
                handler.handleToggleTag(currentTask, tag)
              },
            )
          }
        }
      }

      // 4. Popular Tags (Excluding Exclusives and Recent Versions)
      val popularTags = TagParser.getPopularTags(taskData, limit = 50)
        .filter { it !in exclusiveTags && it !in recentVersions }
        .take(10)

      if (popularTags.isNotEmpty()) {
        subMenu(TodosoBundle.message("todo.suggestion.popular.tags")) {
          popularTags.forEach { tag ->
            toggle(
              text = TagParser.formatTagWithCount(tag, taskData, isTruncated = true),
              isSelected = {
                val currentTask = service.findTaskById(primaryTask.id) ?: primaryTask
                currentTask.tags.contains(tag)
              },
              onToggle = {
                val currentTask = service.findTaskById(primaryTask.id) ?: primaryTask
                handler.handleToggleTag(currentTask, tag)
              },
            )
          }
        }
      }

      if (recentVersions.isNotEmpty()) {
        subMenu(TodosoBundle.message("todo.filter.group.versions")) {
          recentVersions.forEach { tag ->
            toggle(
              text = TagParser.formatTagWithCount(tag, taskData),
              isSelected = {
                val currentTask = service.findTaskById(primaryTask.id) ?: primaryTask
                currentTask.tags.contains(tag)
              },
              onToggle = {
                val currentTask = service.findTaskById(primaryTask.id) ?: primaryTask
                handler.handleToggleTag(currentTask, tag)
              },
            )
          }
        }
      }
    }

    item(
      text = TodosoBundle.message("todo.menu.edit.task"),
      icon = AllIcons.Actions.Edit,
      isEnabled = { isSingleTask },
      isVisible = isNormalMode,
      onAction = {
        if (isSingleTask) {
          val latest = service.findTaskById(primaryTask.id) ?: primaryTask
          handler.setEditMode(true, latest.description)
        }
      },
    )

    val currentPrimary = service.findTaskById(primaryTask.id) ?: primaryTask
    val noteLabel = if (isSingleTask && currentPrimary.metadata.notes.isNotBlank()) {
      TodosoBundle.message("todo.menu.edit.note")
    } else {
      TodosoBundle.message("todo.menu.add.note")
    }

    item(
      text = noteLabel,
      icon = AllIcons.Actions.EditSource,
      isEnabled = { isSingleTask },
      isVisible = isNormalMode,
      onAction = {
        if (isSingleTask) {
          val latest = service.findTaskById(primaryTask.id) ?: primaryTask
          handler.setNoteMode(true, latest.metadata.notes)
        }
      },
    )

    separator()

    item(
      text = TodosoBundle.message("todo.menu.copy.context"),
      icon = AllIcons.Actions.Copy,
      shortcut = CommonShortcuts.getCopy(),
      isVisible = isNormalMode,
      onAction = {
        handler.handleCopyContext()
      },
    )

    item(
      text = TodosoBundle.message("todo.menu.set_commit_message"),
      icon = AllIcons.Actions.Commit,
      isVisible = isNormalMode,
      onAction = {
        handler.handleSetCommitMessage()
      },
    )

    item(
      text = TodosoBundle.message("todo.menu.delete"),
      icon = AllIcons.Actions.GC,
      shortcut = CommonShortcuts.getDelete(),
      isVisible = isNormalMode,
      onAction = {
        handler.handleDeleteAction()
      },
    )
  }

  private fun TodoMenuBuilder.toolTask(selectedTasks: List<TodoTask>) {
    val isSingleTask = selectedTasks.size == 1

    item(
      text = TodosoBundle.message("todo.menu.random"),
      icon = AllIcons.Actions.Lightning,
      onAction = { handler.handleRandomTask() },
    )
    item(
      text = TodosoBundle.message("todo.menu.add_to_changelog"),
      icon = AllIcons.Actions.Checked,
      isEnabled = { selectedTasks.isNotEmpty() },
      onAction = { handler.handleAddToChangelog() },
    )
    item(
      text = TodosoBundle.message("todo.menu.navigate.lane"),
      icon = AllIcons.Actions.ShowCode,
      isEnabled = { isSingleTask },
      onAction = {
        if (isSingleTask) {
          val latest = service.findTaskById(selectedTasks.first().id) ?: selectedTasks.first()
          handler.handleNavigateToTask(latest)
        }
      },
    )
    item(
      text = TodosoBundle.message("todo.filter.search"),
      icon = AllIcons.Actions.Find,
      shortcut = CommonShortcuts.getFind(),
      onAction = { handler.handleToggleSearch() },
    )
  }

  private fun TodoMenuBuilder.visualTask() {
    toggle(
      text = TodosoBundle.message("todo.view.visual.mode"),
      icon = AllIcons.Actions.Show,
      isSelected = { settings.state.visualEnabled },
      onToggle = {
        settings.state.visualEnabled = it
        handler.refreshUiState()
      },
    )
  }

  private fun TodoMenuBuilder.sortTask() {
    subMenu(TodosoBundle.message("todo.common.sort"), AllIcons.Actions.GroupBy) {
      toggle(
        text = TodosoBundle.message("todo.common.default"),
        isSelected = { handler.getSortOption().isEmpty() },
        onToggle = { active ->
          if (active) {
            handler.setSortOption(emptySet())
          }
        },
      )
      separator()
      SortOption.entries.forEach { option ->
        val label =
          when (option) {
            SortOption.PRIORITY -> TodosoBundle.message("todo.sort.by.priority")
            SortOption.STATUS -> TodosoBundle.message("todo.sort.by.status")
            SortOption.DATE -> TodosoBundle.message("todo.sort.by.date")
          }
        toggle(
          text = label,
          isSelected = { handler.getSortOption().contains(option) },
          onToggle = { active ->
            val current = handler.getSortOption().toMutableSet()
            if (active) current.add(option) else current.remove(option)
            handler.setSortOption(current)
          },
        )
      }
    }
  }
}
