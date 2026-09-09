package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.services.TodosoDataChangeListener
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.inputWindow.TodosoInputPanel
import com.github.nndwn.todoso.toolWindow.inputWindow.components.SuggestionOverlayPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.HierarchyEvent
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.SwingUtilities

class TodosoMainPanel(
    private val project: Project,
    private val service: TodosoService = project.service(),
    private val settings: TodosoSettingsService = TodosoSettingsService.getInstance(project)
) : JPanel(BorderLayout()) , TodosoActionHandler.TodoViewActions {

    companion object {
        private const val CARD_INSTRUCTION = "EMPTY_STATE"
        private const val CARD_TASK_LIST = "TASK_LIST"
        private const val HTML = "text/html"
    }

    val uiFont: Font = JBUI.Fonts.label()

    private val handler = TodosoActionHandler(project, service, this)

    private var currentSortOption: Set<TodosoToolbar.SortOption> = settings.state.sortOption
        .split(",")
        .mapNotNull { TodosoToolbar.SortOption.fromKey(it.trim()) }
        .toSet()

    private var currentTagFilter: String? = null
    private val cardLayout = CardLayout()
    private val centerContainer = JPanel(cardLayout)
    private val instructionPane = JEditorPane(HTML, TodosoConstants.getInstructionHtml()).apply {
        isEditable = false
        isOpaque = false
        isFocusable = false
        highlighter = null
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

    private val instructionScrollPane = JBScrollPane(instructionPane).apply {
        border = BorderFactory.createEmptyBorder()
        isFocusable = false
    }
    private val listModel = CollectionListModel<TodoTask>()
    private val list = JBList(listModel).apply {
        font = uiFont.deriveFont(Font.TRUETYPE_FONT,13f, )
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        emptyText.text = TodosoBundle.message("todo.list.empty")
        cellRenderer = TodosoCell(service, settings)

        accessibleContext.accessibleName = TodosoBundle.message("todo.list.accessible.name")
        accessibleContext.accessibleDescription = TodosoBundle.message("todo.list.accessible.desc")
    }

    private val toolbarPanel by lazy {
        TodosoToolbar(
            settings = settings,
            targetComponent = this,
            onRefreshUI = { refreshUiState() },
            onRandomTask = { handler.handleRandomTask() },
            onToggleVisualMode = { list.repaint() },
            onErrorHandler = { errorMessage ->
                handler.handleErrorNotification(errorMessage)
            },
            onRefreshTasks = {handler.refreshTasks()},
            onSortChanged = { sortOptions ->
                currentSortOption = sortOptions
                refreshTasks()
            }
        )
    }

    private val tagsNavigationPanel = TodosoTagsNavigation { selectedTag ->
        setTagFilter(selectedTag)
    }

    private val suggestionOverlay: SuggestionOverlayPanel = SuggestionOverlayPanel { item ->
        inputPanel.insertItemAtCaret(if (item.isTask) "🆔 ${item.taskId}" else item.text, item.isTask)
    }

    private val inputPanel by lazy {
        TodosoInputPanel(
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
            }
        )
    }

    private val layeredPane = JLayeredPane()
    private val mainContent = JPanel(BorderLayout())

    init {
        val toolbarComponent = toolbarPanel.createComponent()
        mainContent.add(toolbarComponent, BorderLayout.NORTH)

        centerContainer.add(instructionScrollPane, CARD_INSTRUCTION)
        centerContainer.add(JBScrollPane(list), CARD_TASK_LIST)
        mainContent.add(centerContainer, BorderLayout.CENTER)

        mainContent.add(inputPanel, BorderLayout.SOUTH)

        // Setup layered pane
        layeredPane.add(mainContent, JLayeredPane.DEFAULT_LAYER)
        layeredPane.add(suggestionOverlay, JLayeredPane.POPUP_LAYER)

        add(layeredPane, BorderLayout.CENTER)

        // Sync mainContent size with layeredPane
        layeredPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                mainContent.bounds = layeredPane.bounds
                updateOverlayPosition()
            }
        })

        addHierarchyListener { event ->
            if ((event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && isShowing) {
                refreshTasks()
            }
        }
        service.injectInstructionsIfNeeded()
        subsChange()
    }

    private fun subsChange(){
        project.messageBus.connect().subscribe(
            TodosoDataChangeListener.TOPIC,
            TodosoDataChangeListener {
                ApplicationManager.getApplication().invokeLater {
                    refreshUiState()
                }
            }
        )
    }

    fun refreshUiState() {
        val allTasks = service.loadTask()

        if (allTasks.isEmpty()) {
            listModel.removeAll()
            tagsNavigationPanel.isVisible = false
            cardLayout.show(centerContainer, CARD_INSTRUCTION)
        } else {
            val tagCounts = extractTagCounts(allTasks)
            tagsNavigationPanel.isVisible = tagCounts.isNotEmpty()
            tagsNavigationPanel.setTags(tagCounts, currentTagFilter)

            val filteredTasks = if (currentTagFilter == null) {
                allTasks
            } else {
                allTasks.filter { task -> task.tags.contains(currentTagFilter) }
            }

            val selected = list.selectedValue
            val selectedId = selected?.id
            val selectedLine = selected?.lineNumber
            val selectedText = selected?.rawText

            val sortedTasks = applySorting(filteredTasks, currentSortOption)
            listModel.replaceAll(sortedTasks)
            cardLayout.show(centerContainer, CARD_TASK_LIST)

            if (selectedId != null) {
                val index = sortedTasks.indexOfFirst {
                    it.id == selectedId || (it.lineNumber == selectedLine && it.rawText == selectedText)
                }
                if (index != -1) {
                    list.selectedIndex = index
                    list.ensureIndexIsVisible(index)
                }
            }

        }
    }

    private fun extractTagCounts(tasks: List<TodoTask>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        tasks.forEach { task ->
            task.tags.forEach { tag ->
                counts[tag] = counts.getOrDefault(tag, 0) + 1
            }
        }
        return counts
    }
    private fun applySorting(tasks: List<TodoTask>, options: Set<TodosoToolbar.SortOption>): List<TodoTask> {
        if (options.isEmpty()) return tasks

        val comparators = mutableListOf<Comparator<TodoTask>>()

        if (options.contains(TodosoToolbar.SortOption.STATUS)) {
            comparators.add(compareBy { it.status })
        }
        if (options.contains(TodosoToolbar.SortOption.DATE)) {
            comparators.add(compareBy { it.metadata.dueDate ?: it.metadata.startDate ?: "9999-99-99" })
        }
        if (options.contains(TodosoToolbar.SortOption.PRIORITY)) {
            comparators.add(compareBy { it.priority })
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

    override fun getSelectedTask(): TodoTask? = list.selectedValue

    override fun getInputText(): String = inputPanel.inputTextArea.text

    override fun clearInputText() {
        inputPanel.clearInputText()
    }

    override fun setTagFilter(tag: String?) {
        currentTagFilter = tag
        refreshUiState()
    }

    override fun updateButtonStates() {
        // Callback untuk update state tombol jika ada dependensi eksternal
    }

    override fun setPriorityFilter(priority: Priority?) {}
    override fun setStatusFilter(status: TaskStatus?) {}

    private fun updateOverlayPosition() {
        if (!suggestionOverlay.isVisible) return

        // Konversi koordinat inputPanel relatif terhadap layeredPane
        val relativeBounds = SwingUtilities.convertRectangle(inputPanel.parent, inputPanel.bounds, layeredPane)
        
        val overlayWidth = relativeBounds.width - JBUI.scale(16)
        val overlayHeight = suggestionOverlay.preferredSize.height.coerceAtMost(JBUI.scale(400))
        
        val x = relativeBounds.x + JBUI.scale(8)
        val y = relativeBounds.y - overlayHeight - JBUI.scale(4)
        
        suggestionOverlay.bounds = Rectangle(x, y, overlayWidth, overlayHeight)
        layeredPane.moveToFront(suggestionOverlay) // Jaminan overlay ada di depan
        suggestionOverlay.revalidate()
        suggestionOverlay.repaint()
    }
}