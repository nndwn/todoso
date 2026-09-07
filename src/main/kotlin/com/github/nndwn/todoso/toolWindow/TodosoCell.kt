package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoIcons
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.DateParser
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

class TodosoCell(
    private val service: TodosoService,
    private val settings: TodosoSettingsService
) : ColoredListCellRenderer<TodoTask>() {
    override fun customizeCellRenderer(
        list: JList<out TodoTask>,
        value: TodoTask?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        value ?: return
        val isDone = value.status == TaskStatus.DONE
        val isDoing = value.status == TaskStatus.DOING
        val isCancelled = value.status == TaskStatus.CANCELLED
        val visualEnabled = settings.state.visualEnabled

        icon =
            when (value.status) {
                TaskStatus.DOING -> TodosoIcons.TaskDoing
                TaskStatus.DONE -> TodosoIcons.TaskDone
                TaskStatus.CANCELLED -> TodosoIcons.TaskCancelled
                TaskStatus.TODO -> TodosoIcons.TaskTodo
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
        task: TodoTask,
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

        var lastIndex = 0

        TagParser.TAG_REGEX.findAll(description).forEach { match ->
            val tagRange = match.range
            if (tagRange.first > lastIndex) {
                append(description.substring(lastIndex, tagRange.first), baseAttributes)
            }

            val tagStyle = when {
                isDone || isCancelled -> SimpleTextAttributes.STYLE_STRIKEOUT
                isDoing -> SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_ITALIC
                else -> SimpleTextAttributes.STYLE_ITALIC or SimpleTextAttributes.STYLE_BOLD
            }

            val tagColor =
                if (isDone || isCancelled) SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor else JBColor.CYAN
            append(match.value, SimpleTextAttributes(tagStyle, tagColor))

            lastIndex = tagRange.last + 1
        }

        if (lastIndex < description.length) {
            append(description.substring(lastIndex), baseAttributes)
        }
    }

    private fun updateToolTip(task: TodoTask) {
        toolTipText = buildString {
            append("<html><body style='width: 250px;'>")

            if (task.isPersistentId) {
                append("<b>[${task.id}]</b> ")
            }

            if (task.priority != Priority.NONE) {
                append("<b>[${task.priority.label}]</b> ")
            }

            append(task.description.replace("\n", "<br/>"))

            val meta = task.metadata
            val dateLabels = listOfNotNull(
                meta.startDate?.let { "Start" to it },
                meta.dueDate?.let { "Due" to it },
                meta.endDate?.let { "Done" to it },
                meta.cancelDate?.let { "Cancelled" to it },
                meta.createdDate?.let { "Created" to it }
            )

            if (dateLabels.isNotEmpty()) {
                dateLabels.forEach { (label, date) ->
                    append("<br/><b>$label</b>: $date")
                }

                if (task.status == TaskStatus.DONE) {
                    DateParser.calculateDuration(meta)?.let { duration ->
                        append("<br/><b>Duration</b>: $duration")
                    }
                }
            }

            if (meta.notes.isNotBlank()) {
                append("<br/><b>Note</b>: ${meta.notes}")
            }

            append("</body></html>")
        }
    }
}