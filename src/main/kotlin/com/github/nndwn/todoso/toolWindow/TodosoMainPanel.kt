package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.services.TodosoDataChangeListener
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.itemTodoList.TodosoItemComponent
import com.github.nndwn.todoso.toolWindow.inputWindow.TodosoInputPanel
import com.github.nndwn.todoso.toolWindow.inputWindow.components.SuggestionOverlayPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.swing.*

class TodosoMainPanel(
  private val project: Project,
  private val service: TodosoService = project.service(),
  private val settings: TodosoSettingsService = TodosoSettingsService.getInstance(project),
) : JPanel(BorderLayout()), TodosoActionHandler.TodoViewActions {

  companion object {
    private const val CARD_INSTRUCTION = "EMPTY_STATE"
    private const val CARD_TASK_LIST = "TASK_LIST"
    private const val CARD_NO_MATCH = "NO_MATCH"
    private const val HTML = "text/html"
  }

  val uiFont: Font = JBUI.Fonts.label()
  private val handler = TodosoActionHandler(project, service, this)

  // Filter States (In-Memory Only)
  private val filterState = TodosoToolbar.FilterState()
  private var currentTagFilter: String? = null

  private var currentSortOption: Set<TodosoToolbar.SortOption> =
    settings.state.sortOption.split(",").mapNotNull { TodosoToolbar.SortOption.fromKey(it.trim()) }.toSet()

  private val cardLayout = CardLayout()
  private val centerContainer = JPanel(cardLayout)
  private val instructionPane =
    JEditorPane(HTML, TodosoConstants.getInstructionHtml()).apply {
      isEditable = false
      isOpaque = false
      isFocusable = false
      highlighter = null
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

  private val instructionScrollPane =
    JBScrollPane(instructionPane).apply {
      border = BorderFactory.createEmptyBorder()
      isFocusable = false
    }

  private val tasksContainer = object : JBPanel<JBPanel<*>>(null), Scrollable {
    init {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
    }
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 20
    override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 100
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
  }

  private var selectedTask: TodoTask? = null
  internal val taskComponents = mutableListOf<TodosoItemComponent>()

  private val toolbarPanel by lazy {
    TodosoToolbar(
      settings = settings,
      targetComponent = this,
      onRefreshUI = { refreshUiState() },
      onRandomTask = { handler.handleRandomTask() },
      onErrorHandler = { errorMessage ->
        handler.handleErrorNotification(errorMessage)
      },
      onRefreshTasks = { handler.refreshTasks() },
      onSortChanged = { sortOptions ->
        currentSortOption = sortOptions
        refreshTasks()
      },
      filterState = filterState,
      onFilterChanged = { type, value ->
        when (type) {
          TodosoToolbar.FilterType.PRIORITY -> filterState.priority = value as Priority?
          TodosoToolbar.FilterType.STATUS -> filterState.status = value as TaskStatus?
          TodosoToolbar.FilterType.DATE -> filterState.date = value as String?
          TodosoToolbar.FilterType.TAG -> filterState.tag = value as String?
          TodosoToolbar.FilterType.RESET_ALL -> {
            filterState.priority = null
            filterState.status = null
            filterState.date = null
            filterState.tag = null
          }
        }
        refreshUiState()
      }
    )
  }

  private val suggestionOverlay: SuggestionOverlayPanel = SuggestionOverlayPanel { item ->
    inputPanel.insertItemAtCaret(if (item.isTask) "🆔 ${item.taskId}" else item.text, item.isTask)
  }

  private val noMatchPane = JEditorPane(HTML, "").apply {
      isEditable = false
      isOpaque = false
      isFocusable = false
      highlighter = null
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
  }

  private val inputPanel by lazy {
    TodosoInputPanel(
      project = project,
      onNewTask = { text -> handler.handleAddTask(text) },
      onUpdateTask = { text -> handler.handleUpdateTask(text) },
      onConfirmCancel = { note -> handler.handleConfirmCancel(note) },
      onCreateNote = { note -> handler.handleConfirmCancel(note) },
      onCancelEdit = { handler.handleCancelEdit() },
      fontInput = uiFont,
      getPopularTags = { TagParser.getPopularTags(service.loadTask()) },
      getAllTasks = { service.loadTask() },
      onSuggestionRequest = { items ->
        if (items != null) {
          suggestionOverlay.updateItems(items)
          updateOverlayPosition()
        } else {
          suggestionOverlay.hideOverlay()
        }
      },
      onNavigationRequest = { direction ->
        when (direction) {
          "UP" -> suggestionOverlay.moveUp()
          "DOWN" -> suggestionOverlay.moveDown()
          "ENTER" -> suggestionOverlay.confirmSelection()
          "ESCAPE" -> suggestionOverlay.hideOverlay()
        }
      },
    )
  }

  private val layeredPane = JLayeredPane()
  private val mainContent = JPanel(BorderLayout())

  init {
    val toolbarComponent = toolbarPanel.createComponent()
    mainContent.add(toolbarComponent, BorderLayout.NORTH)

    centerContainer.add(instructionScrollPane, CARD_INSTRUCTION)
    centerContainer.add(JBScrollPane(tasksContainer, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
        border = BorderFactory.createEmptyBorder()
        viewport.isOpaque = false
        isOpaque = false
    }, CARD_TASK_LIST)
    
    centerContainer.add(JBScrollPane(noMatchPane).apply {
        border = BorderFactory.createEmptyBorder()
        isFocusable = false
    }, CARD_NO_MATCH)

    mainContent.add(centerContainer, BorderLayout.CENTER)
    mainContent.add(inputPanel, BorderLayout.SOUTH)

    layeredPane.add(mainContent, JLayeredPane.DEFAULT_LAYER as Any)
    layeredPane.add(suggestionOverlay, JLayeredPane.POPUP_LAYER as Any)

    add(layeredPane, BorderLayout.CENTER)

    layeredPane.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          mainContent.bounds = layeredPane.bounds
          updateOverlayPosition()
        }
      }
    )

    addHierarchyListener { event ->
      if ((event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && isShowing) {
        refreshTasks()
      }
    }
    service.injectInstructionsIfNeeded()
    subsChange()
  }

  private fun subsChange() {
    project.messageBus
      .connect()
      .subscribe(
        TodosoDataChangeListener.TOPIC,
        TodosoDataChangeListener {
          ApplicationManager.getApplication().invokeLater {
            refreshUiState()
          }
        },
      )
  }

  fun refreshUiState() {
    val allTasks = service.loadTask()

    if (allTasks.isEmpty()) {
      tasksContainer.removeAll()
      taskComponents.clear()
      cardLayout.show(centerContainer, CARD_INSTRUCTION)
    } else {
      // 1. Filter Tasks
      val filteredTasks = allTasks.filter { task ->
        val priorityMatch = filterState.priority == null || task.priority == filterState.priority
        val statusMatch = filterState.status == null || task.status == filterState.status
        
        val activeTag = filterState.tag ?: currentTagFilter
        val tagMatch = activeTag == null || task.tags.contains(activeTag)
        
        val dateMatch = when (filterState.date) {
            "TODAY" -> isTaskMatchingDate(task) { it == LocalDate.now() }
            "THIS_WEEK" -> isTaskMatchingDate(task) { isDateInCurrentWeek(it) }
            "WITH_DATE" -> task.metadata.dueDate != null || task.metadata.startDate != null || task.metadata.createdDate != null
            else -> true
        }
                        
        priorityMatch && statusMatch && tagMatch && dateMatch
      }

      if (filteredTasks.isEmpty()) {
          tasksContainer.removeAll()
          taskComponents.clear()
          noMatchPane.text = buildNoMatchHtml()
          cardLayout.show(centerContainer, CARD_NO_MATCH)
          return
      }

      val sortedTasks = applySorting(filteredTasks, currentSortOption)
      
      val newComponents = mutableListOf<TodosoItemComponent>()
      var isOrderChanged = sortedTasks.size != taskComponents.size
      
      if (!isOrderChanged) {
          for (i in sortedTasks.indices) {
              if (sortedTasks[i].lineNumber != taskComponents[i].task.lineNumber) {
                  isOrderChanged = true
                  break
              }
          }
      }

      if (isOrderChanged) {
          tasksContainer.removeAll()
          sortedTasks.forEach { task ->
              val component = TodosoItemComponent(
                  task, 
                  settings.state.visualEnabled,
                  onSelect = { t -> handleTaskSelection(t) },
                  onEdit = { /* ... */ }
              )
              if (task.lineNumber == selectedTask?.lineNumber || (task.id.isNotBlank() && task.id == selectedTask?.id)) {
                  component.setSelected(true)
              }
              tasksContainer.add(component)
              newComponents.add(component)
          }
          taskComponents.clear()
          taskComponents.addAll(newComponents)
      } else {
          val currentVisualEnabled = settings.state.visualEnabled
          sortedTasks.forEachIndexed { index, task ->
              val comp = taskComponents[index]
              comp.updateData(task, currentVisualEnabled)
              val isSelected = task.lineNumber == selectedTask?.lineNumber || (task.id.isNotBlank() && task.id == selectedTask?.id)
              comp.setSelected(isSelected)
          }
      }
      
      tasksContainer.revalidate()
      tasksContainer.repaint()
      cardLayout.show(centerContainer, CARD_TASK_LIST)
      
      SwingUtilities.invokeLater { scrollToSelected() }
    }
  }

  private fun scrollToSelected() {
      val target = selectedTask ?: return
      val component = taskComponents.find { it.task.id == target.id } ?: return
      tasksContainer.scrollRectToVisible(component.bounds)
  }

  private fun buildNoMatchHtml(): String {
      return """
          <html>
          <body style="font-family: sans-serif; padding: 20px; text-align: center; color: #BBBBBB;">
              <h2 style="color: #FFFFFF;">No Tasks Found</h2>
              <p>No tasks match your active filters.</p>
              <p style="margin-top: 10px;">
                  Try adjusting your <b>Priority</b>, <b>Status</b>, or <b>Tag</b> filters in the toolbar above.
              </p>
          </body>
          </html>
      """.trimIndent()
  }

  private fun handleTaskSelection(task: TodoTask) {
      if (selectedTask?.id == task.id) {
          selectedTask = null
          taskComponents.forEach { it.setSelected(false) }
      } else {
          selectedTask = task
          taskComponents.forEach { it.setSelected(it.task.id == task.id) }
      }
      requestUnfocus()
      updateButtonStates()
  }

  private fun applySorting(tasks: List<TodoTask>, options: Set<TodosoToolbar.SortOption>): List<TodoTask> {
    if (options.isEmpty()) return tasks
    val comparators = mutableListOf<Comparator<TodoTask>>()
    for (option in options) {
      when (option) {
        TodosoToolbar.SortOption.STATUS -> comparators.add(compareBy { it.status })
        TodosoToolbar.SortOption.DATE ->
          comparators.add(compareBy { it.metadata.dueDate ?: it.metadata.startDate ?: it.metadata.createdDate ?: "9999-99-99" })
        TodosoToolbar.SortOption.PRIORITY -> comparators.add(compareBy { it.priority })
      }
    }
    if (comparators.isEmpty()) return tasks
    var finalComparator = comparators[0]
    for (i in 1 until comparators.size) {
      finalComparator = finalComparator.then(comparators[i])
    }
    return tasks.sortedWith(finalComparator)
  }

  override fun refreshTasks() {
    service.markCacheDirty()
    refreshUiState()
  }

  override fun setEditMode(enabled: Boolean, text: String) {
    inputPanel.setEditMode(enabled, text)
  }

  override fun setCancelMode(enabled: Boolean) {
    inputPanel.setCancelMode(enabled)
  }

  override fun getSelectedTask(): TodoTask? = selectedTask
  override fun getInputText(): String = inputPanel.inputTextArea.text
  override fun clearInputText() { inputPanel.clearInputText() }
  override fun requestUnfocus() { inputPanel.requestUnfocus() }
  override fun setSelectedTask(task: TodoTask?) { this.selectedTask = task }
  override fun setTagFilter(tag: String?) {
    currentTagFilter = tag
    refreshUiState()
  }
  override fun setPriorityFilter(priority: Priority?) {
    filterState.priority = priority
    refreshUiState()
  }

  override fun setStatusFilter(status: TaskStatus?) {
    filterState.status = status
    refreshUiState()
  }
  override fun setDateFilter(filter: String?) {
    filterState.date = filter
    refreshUiState()
  }
  override fun updateButtonStates() { /* ... */ }

  private fun updateOverlayPosition() {
    if (!suggestionOverlay.isVisible) return
    val relativeBounds = SwingUtilities.convertRectangle(inputPanel.parent, inputPanel.bounds, layeredPane)
    val overlayWidth = relativeBounds.width - JBUI.scale(30)
    val overlayHeight = suggestionOverlay.preferredSize.height.coerceAtMost(JBUI.scale(400))
    val x = relativeBounds.x + JBUI.scale(15)
    val y = relativeBounds.y - overlayHeight - JBUI.scale(8)
    suggestionOverlay.bounds = Rectangle(x, y, overlayWidth, overlayHeight)
    layeredPane.moveToFront(suggestionOverlay)
    suggestionOverlay.revalidate()
    suggestionOverlay.repaint()
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
