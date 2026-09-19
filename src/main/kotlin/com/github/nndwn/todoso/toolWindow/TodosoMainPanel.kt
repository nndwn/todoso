package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.domain.parser.TodoValidator
import com.github.nndwn.todoso.services.TodosoDataChangeListener
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.contextMenu.TodosoContextMenu
import com.github.nndwn.todoso.toolWindow.contextMenu.toActionGroup
import com.github.nndwn.todoso.toolWindow.inputWindow.SuggestionItem
import com.github.nndwn.todoso.toolWindow.inputWindow.SuggestionNav
import com.github.nndwn.todoso.toolWindow.inputWindow.SuggestionType
import com.github.nndwn.todoso.toolWindow.inputWindow.TodosoInputPanel
import com.github.nndwn.todoso.toolWindow.inputWindow.components.SuggestionOverlayPanel
import com.github.nndwn.todoso.toolWindow.logic.TodoTaskFilterer
import com.github.nndwn.todoso.toolWindow.search.TodosoSearchPanel
import com.github.nndwn.todoso.toolWindow.taskList.TodosoTaskListView
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

class TodosoMainPanel(private val project: Project) : JPanel(BorderLayout()), TodosoActionHandler.TodoViewActions {

  private val service = project.service<TodosoService>()
  private val settings = TodosoSettingsService.getInstance(project)
  val uiFont: Font = JBUI.Fonts.label()
  internal val handler = TodosoActionHandler(project, service, this)
  private val filterState = FilterState()
  private var currentTagFilter: String? = null
  private var currentSortOption: Set<SortOption> =
    settings.state.sortOption.split(",").mapNotNull { SortOption.fromKey(it.trim()) }.toSet()

  private val cardLayout = CardLayout()
  private val centerContainer = JPanel(cardLayout)

  private val instructionPane =
    JEditorPane(TodosoConstants.MIME_HTML, TodosoConstants.getInstructionHtml()).apply {
      isEditable = false
      isOpaque = false
      isFocusable = false
      highlighter = null
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

  private val noMatchPane =
    JEditorPane(TodosoConstants.MIME_HTML, "").apply {
      isEditable = false
      isOpaque = false
      isFocusable = false
      highlighter = null
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

  internal val toolbarPanel: TodosoToolbar =
    TodosoToolbar(
      settings = settings,
      targetComponent = this,
      onRefreshUI = ::refreshUiState,
      onRefreshTasks = {
        inputPanel.hideOverlay()
        handler.refreshTasks()
      },
      onRandomTask = handler::handleRandomTask,
      onErrorHandler = handler::handleErrorNotification,
      onSortChanged = ::setCurrentSortOption,
      filterState = filterState,
      onFilterChanged = ::onFilterChanged,
    )

  internal val inputPanel: TodosoInputPanel =
    TodosoInputPanel(
      onCancelEdit = { handler.handleCancelEdit() },
      onConfirmCancel = { note ->
        hideSearchPanel()
        handler.handleConfirmCancel(note)
      },
      onCreateNote = { note ->
        hideSearchPanel()
        handler.handleUpdateNote(note)
      },
      onNewTask = { text ->
        hideSearchPanel()
        handler.handleAddTask(text)
      },
      onUpdateTask = { text ->
        hideSearchPanel()
        handler.handleUpdateTask(text)
      },
      fontInput = uiFont,
      onSuggestionProvider = { prefix ->
        val currentText = inputPanel.inputTextArea.text.trim()
        if (currentText.isEmpty()) {
          Priority.entries
            .filter { it != Priority.NONE }
            .map {
              SuggestionItem(
                text = "[${it.code}]",
                category = TodosoBundle.message("todo.suggestion.priority"),
                icon = it.icon,
                type = SuggestionType.PRIORITY,
                tagDisplay = it.displayName,
              )
            }
        } else {
          getSuggestions(prefix, TagParser.getPopularTags(service.loadTask()), service.loadTask())
        }
      },
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
          SuggestionNav.UP -> suggestionOverlay.moveUp()
          SuggestionNav.DOWN -> suggestionOverlay.moveDown()
          SuggestionNav.ENTER -> suggestionOverlay.confirmSelection()
        }
      },
      onTabPressed = {
        taskListView.getSelectedTask()?.let { task ->
          taskListView.setSelectedTask(task, requestFocus = true)
        }
          ?: run {
            if (taskListView.taskComponents.isNotEmpty()) {
              val firstTask = taskListView.taskComponents.first().task
              handleTaskSelection(firstTask, forceSelect = true)
            }
          }
      },
      onAttachFileRequest = { handleAttachFile() },
      onTextValidator = { text -> TodoValidator.isContentValid(text) },
    )

  private fun handleAttachFile() {
    if (project.isDisposed) return

    val descriptor =
      FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
        .withTitle(TodosoBundle.message("todo.insert.file"))
        .withDescription(TodosoBundle.message("todo.insert.file.desc"))

    val selectedFile = FileChooser.chooseFile(descriptor, project, null) ?: return
    insertMarkdownAttachment(selectedFile)
  }

  private fun insertMarkdownAttachment(file: VirtualFile) {
    val projectDir = project.guessProjectDir()
    val relativePath =
      if (projectDir != null) {
        VfsUtilCore.getRelativePath(file, projectDir) ?: file.path
      } else {
        file.path
      }

    val isImage = listOf("jpg", "jpeg", "png", "gif", "svg", "webp").any {
      file.name.lowercase().endsWith(".$it")
    }

    val markdownSnippet =
      if (isImage) {
        "![${file.name}]($relativePath)"
      } else {
        "[${file.name}]($relativePath)"
      }

    SwingUtilities.invokeLater {
      val currentText = inputPanel.inputTextArea.text
      val newText = if (currentText.contains("//")) {
        "$currentText $markdownSnippet"
      } else {
        "$currentText // $markdownSnippet"
      }
      inputPanel.inputTextArea.text = newText
      inputPanel.requestFocusToInput()
    }
  }

  private fun getSuggestions(
    prefix: String,
    popularTags: List<String>,
    allTasks: List<TodoTask>,
  ): List<SuggestionItem> {
    val suggestions = mutableListOf<SuggestionItem>()

    val tagItems: List<SuggestionItem> =
      if (prefix.isEmpty()) {
        if (popularTags.isEmpty()) {
          TodosoConstants.DEFAULT_QUICK_TAGS.map {
            SuggestionItem(
              it,
              TodosoBundle.message("todo.suggestion.quick.tags"),
              tagDisplay = it,
              type = SuggestionType.TAGS
            )
          }
        } else {
          popularTags.map {
            SuggestionItem(
              it,
              TodosoBundle.message("todo.suggestion.popular.tags"),
              tagDisplay = TagParser.formatTagWithCount(it, allTasks),
              type = SuggestionType.TAGS
            )
          }
        }
      } else {
        val rawTags = (allTasks.flatMap { it.tags } + popularTags + TodosoConstants.DEFAULT_QUICK_TAGS)
          .map { it.removePrefix("#") }

        val allAvailableTags = rawTags
          .groupBy { it }
          .keys
          .groupBy { it.lowercase() }
          .map { it.value.first() }

        allAvailableTags
          .filter { it.startsWith(prefix, ignoreCase = true) }
          .map { tag ->
            val isPopular = popularTags.contains(tag)
            val isExclusive = TodosoConstants.DEFAULT_QUICK_TAGS.contains(tag)
            val isVersion = tag.matches(TagParser.VERSION_REGEX)

            SuggestionItem(
              tag,
              when {
                isVersion -> TodosoBundle.message("todo.filter.group.versions")
                isPopular -> TodosoBundle.message("todo.suggestion.popular.tags")
                isExclusive -> TodosoBundle.message("todo.suggestion.quick.tags")
                else -> TodosoBundle.message("todo.suggestion.all.tags")
              },
              tagDisplay = TagParser.formatTagWithCount(tag, allTasks) ,
              type = SuggestionType.TAGS
            )
          }
      }
    suggestions.addAll(tagItems)

    if (prefix.isNotEmpty()) {
      val relatedTasks =
        allTasks
          .filter { task -> task.tags.any { it.equals(prefix, ignoreCase = true) } }
          .sortedByDescending { it.metadata.createdDate ?: "" }

      suggestions.addAll(
        relatedTasks.map { task ->
          SuggestionItem(
            text = task.description.take(50) + (if (task.description.length > 50) "..." else ""),
            category = TodosoBundle.message("todo.suggestion.related.tags"),
            isTask = true,
            taskId = task.id,
            type = SuggestionType.TASK
          )
        }
      )
    }
    return suggestions
  }

  internal val taskListView: TodosoTaskListView =
    TodosoTaskListView(
      service = service,
      settings = settings,
      onTaskSelected = { task, force -> handleTaskSelection(task, force) },
      onDelete = { handler.handleDeleteAction() },
      onContextMenu = { task, e -> showContextMenu(task, e) },
      onTabPressed = { inputPanel.requestFocusToInput() },
    )

  internal val searchPanel: TodosoSearchPanel =
    TodosoSearchPanel(onQueryChanged = { query -> onSearchQueryChanged(query) })

  internal val suggestionOverlay: SuggestionOverlayPanel = SuggestionOverlayPanel { item ->
    val caretPos = inputPanel.inputTextArea.caretPosition
    val text = inputPanel.inputTextArea.text
    val prefix = inputPanel.getActivePrefix(text, caretPos) ?: ""

    when (item.type) {
      SuggestionType.PRIORITY -> {
        inputPanel.insertItemAtCaret("${item.text} ", false)
      }
      SuggestionType.TAGS -> {
        if (item.text.equals(prefix, ignoreCase = true)) {
          inputPanel.insertItemAtCaret("${item.text} ", false)
        } else {
          inputPanel.insertItemAtCaret(item.text, false)
        }
      }
      SuggestionType.TASK -> {
        inputPanel.insertItemAtCaret("🆔 ${item.taskId}", true)
      }
    }
  }

  private val layeredPane = JLayeredPane()
  private val mainContent = JPanel(BorderLayout())

  init {
    setupLayout()
    setupShortcuts()
    setupListeners()
    service.injectInstructionsIfNeeded()
    subsChange()
  }

  private fun setupLayout() {
    val northPanel =
      JPanel(BorderLayout()).apply {
        add(toolbarPanel.createComponent(), BorderLayout.NORTH)
        add(searchPanel, BorderLayout.SOUTH)
      }

    centerContainer.add(
      JBScrollPane(instructionPane).apply { border = BorderFactory.createEmptyBorder() },
      TodosoConstants.CARD_INSTRUCTION,
    )
    centerContainer.add(taskListView, TodosoConstants.CARD_TASK_LIST)
    centerContainer.add(
      JBScrollPane(noMatchPane).apply { border = BorderFactory.createEmptyBorder() },
      TodosoConstants.CARD_NO_MATCH,
    )

    mainContent.add(northPanel, BorderLayout.NORTH)
    mainContent.add(centerContainer, BorderLayout.CENTER)
    mainContent.add(inputPanel, BorderLayout.SOUTH)

    layeredPane.add(mainContent, JLayeredPane.DEFAULT_LAYER as Any)
    layeredPane.add(suggestionOverlay, JLayeredPane.POPUP_LAYER as Any)
    add(layeredPane, BorderLayout.CENTER)
  }

  private fun setupShortcuts() {
    val searchAction =
      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) = toggleSearch()
      }
    searchAction.registerCustomShortcutSet(CommonShortcuts.getFind(), this)
  }

  private fun setupListeners() {
    layeredPane.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          mainContent.bounds = layeredPane.bounds
          updateOverlayPosition()
        }
      }
    )

    refreshTasks()
  }

  private fun subsChange() {
    project.messageBus
      .connect()
      .subscribe(
        TodosoDataChangeListener.TOPIC,
        TodosoDataChangeListener {
          ApplicationManager.getApplication().invokeLater { refreshUiState() }
        },
      )
  }

  fun refreshUiState() {
    val allTasks = service.loadTask()
    if (allTasks.isEmpty()) {
      taskListView.clear()
      cardLayout.show(centerContainer, TodosoConstants.CARD_INSTRUCTION)
      return
    }

    val filteredAndSorted = TodoTaskFilterer.filterAndSort(allTasks, filterState, currentTagFilter, currentSortOption)

    if (filteredAndSorted.isEmpty()) {
      taskListView.clear()
      noMatchPane.text = TodosoConstants.getNoMatchHtml()
      cardLayout.show(centerContainer, TodosoConstants.CARD_NO_MATCH)
      return
    }

    taskListView.updateTasks(filteredAndSorted)
    cardLayout.show(centerContainer, TodosoConstants.CARD_TASK_LIST)
  }

  override fun refreshTasks() {
    service.markCacheDirty()
    refreshUiState()
  }

  override fun setEditMode(enabled: Boolean, text: String) = inputPanel.setEditMode(enabled, text)

  override fun setNoteMode(enabled: Boolean, text: String) = inputPanel.setNoteMode(enabled, text)

  override fun setCancelMode(enabled: Boolean) = inputPanel.setCancelMode(enabled)

  override fun getCurrentMode() = inputPanel.currentMode

  override fun getSelectedTask(): TodoTask? = taskListView.getSelectedTask()

  override fun getInputText(): String = inputPanel.inputTextArea.text

  override fun clearInputText() = inputPanel.clearInputText()

  override fun requestFocusToInput() = inputPanel.requestFocusToInput()

  override fun setSelectedTask(task: TodoTask?) = taskListView.setSelectedTask(task, requestFocus = false)

  override fun toggleSearch() = searchPanel.toggle()

  fun onSearchQueryChanged(query: String) {
    filterState.query = query
    refreshUiState()
  }

  fun onFilterChanged(type: FilterType, value: Any?) {
    when (type) {
      FilterType.PRIORITY -> filterState.priority = value as Priority?
      FilterType.STATUS -> filterState.status = value as TaskStatus?
      FilterType.DATE -> filterState.date = value as DateFilter?
      FilterType.TAG -> filterState.tag = value as String?
      FilterType.SEARCH -> {
        if (value == "TOGGLE") toggleSearch() else onSearchQueryChanged(value as String)
        return
      }
      FilterType.RESET_ALL -> {
        filterState.priority = null
        filterState.status = null
        filterState.date = null
        filterState.tag = null
        filterState.query = null
        searchPanel.clear()
      }
    }
    refreshUiState()
  }

  fun handleTaskSelection(task: TodoTask, forceSelect: Boolean = false) {
    if (!forceSelect && taskListView.getSelectedTask()?.id == task.id) {
      taskListView.setSelectedTask(null)
    } else {
      taskListView.setSelectedTask(task, requestFocus = true)
    }
    updateButtonStates()
  }

  fun showContextMenu(task: TodoTask, e: MouseEvent) {
    handleTaskSelection(task, forceSelect = true)
    val menuElements = TodosoContextMenu(service, settings, handler).build()
    val actionGroup = menuElements.toActionGroup(this)
    ActionManager.getInstance()
      .createActionPopupMenu("TodosoContextMenu", actionGroup)
      .component
      .show(e.component, e.x, e.y)
  }

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
    filterState.date = filter?.let {
      try {
        DateFilter.valueOf(it)
      } catch (_: Exception) {
        null
      }
    }
    refreshUiState()
  }

  override fun updateButtonStates() {}

  fun updateOverlayPosition() {
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

  fun hideSearchPanel() = searchPanel.hidePanel()

  fun getFilterState() = filterState

  fun getCurrentSortOption() = currentSortOption

  fun setCurrentSortOption(options: Set<SortOption>) {
    currentSortOption = options
    refreshTasks()
  }

  fun getSuggestionOverlay() = suggestionOverlay

  fun getInputPanel() = inputPanel
}
