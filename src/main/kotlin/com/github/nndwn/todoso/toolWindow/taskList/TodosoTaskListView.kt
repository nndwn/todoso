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
  private val selectedTasks = LinkedHashSet<TodoTask>()
  private var anchorTask: TodoTask? = null
  var isLocked: Boolean = false
  private var editingTaskId: String? = null

  fun setEditingTaskId(taskId: String?) {
    this.editingTaskId = taskId
    taskComponents.forEach { comp ->
      comp.setEditing(taskId != null && comp.task.id == taskId)
    }
  }

  init {
    isOpaque = false
    add(scrollPane, BorderLayout.CENTER)

    scrollPane.verticalScrollBar.addAdjustmentListener {
      onScroll()
    }
  }

  override fun updateUI() {
    super.updateUI()
    runCatching {
      tasksContainer.removeAll()
      taskComponents.clear()
      selectedTasks.clear()
      anchorTask = null
      tasksContainer.revalidate()
      tasksContainer.repaint()
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
            onSelect = { t, e -> handleTaskClick(t, e) },
            onDoubleClick = {},
            onDelete = { t -> onDelete(t) },
            onContextMenu = { t, e -> onContextMenu(t, e) },
            onNavigate = { keyCode, isShiftDown -> handleNavigation(keyCode, isShiftDown) },
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

    // Remove deleted tasks from selection
    val validIds = sortedTasks.map { it.id }.toSet()
    selectedTasks.removeIf { it.id !in validIds }
    if (anchorTask != null && anchorTask!!.id !in validIds) {
      anchorTask = selectedTasks.lastOrNull()
    }

    updateComponentsSelection()
    setEditingTaskId(editingTaskId)

    tasksContainer.revalidate()
    tasksContainer.repaint()

    SwingUtilities.invokeLater { scrollToSelected() }
  }

  fun handleTaskClick(task: TodoTask, e: MouseEvent? = null) {
    if (isLocked) return

    if (e != null && e.isShiftDown && anchorTask != null) {
      val anchorIndex = taskComponents.indexOfFirst { it.task.id == anchorTask?.id }
      val targetIndex = taskComponents.indexOfFirst { it.task.id == task.id }
      if (anchorIndex != -1 && targetIndex != -1) {
        val start = minOf(anchorIndex, targetIndex)
        val end = maxOf(anchorIndex, targetIndex)
        selectedTasks.clear()
        for (i in start..end) {
          selectedTasks.add(taskComponents[i].task)
        }
      } else {
        selectedTasks.clear()
        selectedTasks.add(task)
        anchorTask = task
      }
    } else if (e != null && (e.isControlDown || e.isMetaDown)) {
      if (selectedTasks.contains(task)) {
        selectedTasks.remove(task)
      } else {
        selectedTasks.add(task)
        anchorTask = task
      }
    } else {
      selectedTasks.clear()
      selectedTasks.add(task)
      anchorTask = task
    }

    updateComponentsSelection()
    scrollToTask(task)

    val activeComp = taskComponents.find { it.task.id == task.id }
    if (activeComp != null) {
      SwingUtilities.invokeLater {
        activeComp.requestFocusInWindow()
      }
    }

    onTaskSelected(task, true)
  }

  private fun handleNavigation(keyCode: Int, isShiftDown: Boolean = false) {
    if (isLocked) return

    val current = selectedTasks.lastOrNull() ?: anchorTask ?: return
    val index = taskComponents.indexOfFirst { it.task.id == current.id }
    if (index == -1) return

    val nextIndex = when (keyCode) {
      KeyEvent.VK_UP -> if (index > 0) index - 1 else null
      KeyEvent.VK_DOWN -> if (index < taskComponents.size - 1) index + 1 else null
      else -> null
    } ?: return

    val nextTask = taskComponents[nextIndex].task

    if (isShiftDown && anchorTask != null) {
      val anchorIndex = taskComponents.indexOfFirst { it.task.id == anchorTask?.id }
      if (anchorIndex != -1) {
        val start = minOf(anchorIndex, nextIndex)
        val end = maxOf(anchorIndex, nextIndex)
        selectedTasks.clear()
        for (i in start..end) {
          selectedTasks.add(taskComponents[i].task)
        }
      } else {
        selectedTasks.clear()
        selectedTasks.add(nextTask)
        anchorTask = nextTask
      }
    } else {
      selectedTasks.clear()
      selectedTasks.add(nextTask)
      anchorTask = nextTask
    }

    updateComponentsSelection()
    scrollToTask(nextTask)

    val nextComp = taskComponents[nextIndex]
    SwingUtilities.invokeLater {
      nextComp.requestFocusInWindow()
    }

    onTaskSelected(nextTask, true)
  }

  private fun scrollToTask(task: TodoTask?) {
    val target = task ?: selectedTasks.lastOrNull() ?: anchorTask ?: return
    val component = taskComponents.find { it.task.id == target.id } ?: return
    component.scrollRectToVisible(Rectangle(0, 0, component.width, component.height))
  }

  private fun scrollToSelected() {
    scrollToTask(selectedTasks.lastOrNull() ?: anchorTask)
  }

  private fun isTaskSelected(task: TodoTask): Boolean {
    return task.id.isNotBlank() && selectedTasks.any { it.id == task.id }
  }

  private fun updateComponentsSelection() {
    taskComponents.forEach { comp ->
      comp.setSelected(isTaskSelected(comp.task))
    }
  }

  fun setSelectedTask(task: TodoTask?, requestFocus: Boolean = false) {
    selectedTasks.clear()
    if (task != null) {
      selectedTasks.add(task)
      anchorTask = task
    } else {
      anchorTask = null
    }

    updateComponentsSelection()

    if (task != null) {
      scrollToTask(task)
      if (requestFocus) {
        val comp = taskComponents.find { it.task.id == task.id }
        if (comp != null) {
          SwingUtilities.invokeLater {
            comp.requestFocusInWindow()
          }
        }
      }
    }
  }

  fun setSelectedTasks(tasks: Collection<TodoTask>) {
    selectedTasks.clear()
    selectedTasks.addAll(tasks)
    anchorTask = tasks.lastOrNull()
    updateComponentsSelection()
  }

  fun getSelectedTask(): TodoTask? = selectedTasks.firstOrNull()

  fun getSelectedTasks(): List<TodoTask> = selectedTasks.toList()

  fun clear() {
    tasksContainer.removeAll()
    taskComponents.clear()
    selectedTasks.clear()
    anchorTask = null
    tasksContainer.revalidate()
    tasksContainer.repaint()
  }
}
