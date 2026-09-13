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
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Scrollable
import javax.swing.SwingUtilities

class TodosoTaskListView(
    private val service: TodosoService,
    private val settings: TodosoSettingsService,
    private val onTaskSelected: (TodoTask, Boolean) -> Unit,
    private val onTaskEdit: (TodoTask) -> Unit,
    private val onContextMenu: (TodoTask, MouseEvent) -> Unit
) : JBPanel<TodosoTaskListView>(BorderLayout()) {

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

    private val scrollPane = JBScrollPane(tasksContainer, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
        border = BorderFactory.createEmptyBorder()
        viewport.isOpaque = false
        isOpaque = false
    }

    internal val taskComponents = mutableListOf<TodosoItemComponent>()
    private var selectedTask: TodoTask? = null

    init {
        isOpaque = false
        add(scrollPane, BorderLayout.CENTER)
    }

    fun updateTasks(sortedTasks: List<TodoTask>) {
        val currentComponents = taskComponents.associateBy { it.task.id }
        val newComponents = mutableListOf<TodosoItemComponent>()
        val currentVisualEnabled = settings.state.visualEnabled

        // 1. Update atau Buat Komponen Baru
        sortedTasks.forEach { task ->
            val existing = currentComponents[task.id]
            if (existing != null) {
                existing.updateData(task, currentVisualEnabled)
                existing.setSelected(isTaskSelected(task))
                newComponents.add(existing)
            } else {
                val newComp = TodosoItemComponent(
                    service, task, currentVisualEnabled,
                    onSelect = { t -> onTaskSelected(t, false) },
                    onEdit = { t -> onTaskEdit(t) },
                    onContextMenu = { t, e -> onContextMenu(t, e) }
                )
                newComp.setSelected(isTaskSelected(task))
                newComponents.add(newComp)
            }
        }

        // 2. Sinkronisasi Container tanpa memicu fluktuasi ukuran
        tasksContainer.apply {
            // Kunci ukuran saat ini agar scrollbar tidak melompat
            val currentSize = preferredSize
            preferredSize = currentSize
            
            try {
                // Hapus yang tidak ada di list baru
                val newIds = sortedTasks.map { it.id }.toSet()
                taskComponents.filter { it.task.id !in newIds }.forEach { remove(it) }

                // Pastikan urutan di UI sesuai dengan sortedTasks
                newComponents.forEachIndexed { index, comp ->
                    if (index >= componentCount || getComponent(index) != comp) {
                        add(comp, index)
                    }
                }
            } finally {
                // Lepaskan kunci ukuran agar layout bisa menyesuaikan secara alami
                preferredSize = null
            }
        }

        taskComponents.clear()
        taskComponents.addAll(newComponents)

        tasksContainer.revalidate()
        tasksContainer.repaint()
        
        // Kembalikan auto scroll karena sekarang sudah stabil
        SwingUtilities.invokeLater { scrollToSelected() }
    }

    private fun scrollToSelected() {
        val target = selectedTask ?: return
        val component = taskComponents.find { it.task.id == target.id } ?: return
        tasksContainer.scrollRectToVisible(component.bounds)
    }


    private fun isTaskSelected(task: TodoTask): Boolean {
        return task.id.isNotBlank() && task.id == selectedTask?.id
    }

    fun setSelectedTask(task: TodoTask?) {
        this.selectedTask = task
        taskComponents.forEach { it.setSelected(it.task.id == task?.id) }
    }
    
    fun getSelectedTask(): TodoTask? = selectedTask

    fun clear() {
        tasksContainer.removeAll()
        taskComponents.clear()
        tasksContainer.revalidate()
        tasksContainer.repaint()
    }
}
