package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.MyIcons
import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

class TodoCellRenderer(
  private val service: MyProjectService,
  private val settings: MyProjectSettingsService,
) : ColoredListCellRenderer<MyProjectService.TodoTask>() {

  override fun customizeCellRenderer(
    list: JList<out MyProjectService.TodoTask>,
    value: MyProjectService.TodoTask?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    value ?: return

    val isDone = value.status == MyProjectService.TaskStatus.DONE
    val isDoing = value.status == MyProjectService.TaskStatus.DOING
    val isCancelled = value.status == MyProjectService.TaskStatus.CANCELLED
    val visualEnabled = settings.state.visualEnabled

    icon =
      when (value.status) {
        MyProjectService.TaskStatus.DOING -> MyIcons.TaskDoing
        MyProjectService.TaskStatus.DONE -> MyIcons.TaskDone
        MyProjectService.TaskStatus.CANCELLED -> MyIcons.TaskCancelled
        MyProjectService.TaskStatus.TODO -> MyIcons.TaskTodo
      }

    val baseAttributes = getBaseAttributes(value, isDone, isDoing, isCancelled, visualEnabled)
    val displayDescription =
      if (value.description.length > 100) {
        value.description.substring(0, 97) + "..."
      } else {
        value.description
      }
    renderDescriptionWithTags(displayDescription, baseAttributes, isDone, isDoing, isCancelled)
    updateToolTip(value)
  }

  private fun getBaseAttributes(
    task: MyProjectService.TodoTask,
    isDone: Boolean,
    isDoing: Boolean,
    isCancelled: Boolean,
    visualEnabled: Boolean,
  ): SimpleTextAttributes {
    val style =
      when {
        isDone || isCancelled -> SimpleTextAttributes.STYLE_STRIKEOUT
        isDoing -> SimpleTextAttributes.STYLE_BOLD
        else -> SimpleTextAttributes.STYLE_PLAIN
      }

    val color =
      when {
        isDone || isCancelled -> SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor
        isDoing && visualEnabled -> JBColor.GREEN
        visualEnabled -> task.priority.color
        else -> SimpleTextAttributes.REGULAR_ATTRIBUTES.fgColor
      }
    return SimpleTextAttributes(style, color)
  }

  private fun renderDescriptionWithTags(
    description: String,
    baseAttributes: SimpleTextAttributes,
    isDone: Boolean,
    isDoing: Boolean,
    isCancelled: Boolean,
  ) {
    val tagRegex = Regex("""#[\w.-]+""")
    var lastIndex = 0

    tagRegex.findAll(description).forEach { match ->
      if (match.range.first > lastIndex) {
        append(description.substring(lastIndex, match.range.first), baseAttributes)
      }

      val tagStyle =
        when {
          isDone || isCancelled -> SimpleTextAttributes.STYLE_STRIKEOUT
          isDoing -> SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_ITALIC
          else -> SimpleTextAttributes.STYLE_ITALIC or SimpleTextAttributes.STYLE_BOLD
        }

      val tagColor = if (isDone || isCancelled) SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor else JBColor.CYAN
      append(match.value, SimpleTextAttributes(tagStyle, tagColor))

      lastIndex = match.range.last + 1
    }

    if (lastIndex < description.length) {
      append(description.substring(lastIndex), baseAttributes)
    }
  }

  private fun updateToolTip(task: MyProjectService.TodoTask) {
    toolTipText = buildString {
      append("<html><body style='width: 250px;'>")
      if (task.isPersistentId) {
        append("<b>[${task.id}]</b> ")
      }
      if (task.priority != MyProjectService.Priority.NONE) {
        append("<b>[${task.priority.label}]</b> ")
      }
      append(task.description.replace("\n", "<br/>"))

      if (task.dates.isNotEmpty()) {

        task.dates.forEach { (emoji, date) ->
          val label =
            when (emoji) {
              "🛫" -> "Start"
              "📅" -> "Due"
              "⏳" -> "Scheduled"
              "✅" -> "Done"
              "➕" -> "Created"
              "❌" -> "Cancelled"
              "//" -> "Note"
              else -> "Info"
            }
          append("<br/><b>$label</b>: $date")
        }

        if (task.status == MyProjectService.TaskStatus.DONE) {
          service.calculateDuration(task)?.let { duration ->
            append("<br/> <b>Duration</b>: $duration")
          }
        }
      }
      append("</body></html>")
    }
  }
}
