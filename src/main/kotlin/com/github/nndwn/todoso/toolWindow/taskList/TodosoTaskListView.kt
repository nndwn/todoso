package com.github.nndwn.todoso.toolWindow.taskList

import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.itemTodoList.TodosoItemComponent
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Scrollable
import javax.swing.SwingUtilities

class TodosoTaskListView(
  private val service: TodosoService,
  private val settings: TodosoSettingsService,
  private val onTaskSelected: (TodoTask, Boolean) -> Unit,
  private val onDelete: (TodoTask) -> Unit,
  private val onContextMenu: (TodoTask, MouseEvent) -> Unit,
  private val onTabPressed: () -> Unit,
  private val onSearch: (String) -> Unit,
  private val onScroll: () -> Unit = {},
) : JBPanel<TodosoTaskListView>(BorderLayout()) {

  private val tasksContainer =
    object : JBPanel<JBPanel<*>>(null), Scrollable {
      init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        isFocusable = false
      }

      override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

      override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 20

      override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 100

      override fun getScrollableTracksViewportWidth(): Boolean = true

      override fun getScrollableTracksViewportHeight(): Boolean = false
    }

  private val scrollPane =
    JBScrollPane(tasksContainer, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
      .apply {
        border = BorderFactory.createEmptyBorder()
        viewport.isOpaque = false
        isOpaque = false
      }

  internal val taskComponents = mutableListOf<TodosoItemComponent>()
  private var selectedTask: TodoTask? = null

  init {
    isOpaque = false
    add(scrollPane, BorderLayout.CENTER)

    scrollPane.verticalScrollBar.addAdjustmentListener {
      onScroll()
    }
  }

  fun updateTasks(sortedTasks: List<TodoTask>) {
    val currentComponents = taskComponents.associateBy { it.task.id }
    val newComponents = mutableListOf<TodosoItemComponent>()
    val currentVisualEnabled = settings.state.visualEnabled

    sortedTasks.forEach { task ->
      val existing = currentComponents[task.id]
      if (existing != null) {
        existing.updateData(task, currentVisualEnabled)
        existing.setSelected(isTaskSelected(task))
        newComponents.add(existing)
      } else {
        val newComp =
          TodosoItemComponent(
            service,
            task,
            currentVisualEnabled,
            onSelect = { t -> onTaskSelected(t, false) },
            onDoubleClick = {},
            onDelete = { t -> onDelete(t) },
            onContextMenu = { t, e -> onContextMenu(t, e) },
            onNavigate = { keyCode -> handleNavigation(keyCode) },
            onTabPressed = { onTabPressed() },
            onSearch = { query -> onSearch(query) },
          )
        newComp.setSelected(isTaskSelected(task))
        newComponents.add(newComp)
      }
    }

    tasksContainer.apply {
      val currentSize = preferredSize
      preferredSize = currentSize

      try {
        val newIds = sortedTasks.map { it.id }.toSet()
        taskComponents.filter { it.task.id !in newIds }.forEach { remove(it) }

        newComponents.forEachIndexed { index, comp ->
          if (index >= componentCount || getComponent(index) != comp) {
            add(comp, index)
          }
        }
      } finally {
        preferredSize = null
      }
    }

    taskComponents.clear()
    taskComponents.addAll(newComponents)

    tasksContainer.revalidate()
    tasksContainer.repaint()

    SwingUtilities.invokeLater { scrollToSelected() }
  }

  private fun handleNavigation(keyCode: Int) {
    val current = selectedTask ?: return
    val index = taskComponents.indexOfFirst { it.task.id == current.id }
    if (index == -1) return

    when (keyCode) {
      KeyEvent.VK_UP -> {
        if (index > 0) {
          onTaskSelected(taskComponents[index - 1].task, true)
        }
      }
      KeyEvent.VK_DOWN -> {
        if (index < taskComponents.size - 1) {
          onTaskSelected(taskComponents[index + 1].task, true)
        }
      }
    }
  }

  private fun scrollToSelected() {
    val target = selectedTask ?: return
    val component = taskComponents.find { it.task.id == target.id } ?: return
    tasksContainer.scrollRectToVisible(component.bounds)
  }

  private fun isTaskSelected(task: TodoTask): Boolean {
    return task.id.isNotBlank() && task.id == selectedTask?.id
  }

  fun setSelectedTask(task: TodoTask?, requestFocus: Boolean = false) {
    this.selectedTask = task
    taskComponents.forEach {
      val isTarget = it.task.id == task?.id
      it.setSelected(isTarget)
      if (isTarget && requestFocus) {
        SwingUtilities.invokeLater {
          it.requestFocusInWindow()
        }
      }
    }
    if (task != null) {
      SwingUtilities.invokeLater { scrollToSelected() }
    }
  }

  fun getSelectedTask(): TodoTask? = selectedTask

  fun clear() {
    tasksContainer.removeAll()
    taskComponents.clear()
    tasksContainer.revalidate()
    tasksContainer.repaint()
  }
}
