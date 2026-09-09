package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.toolWindow.inputWindow.SuggestionItem
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

@Service(Service.Level.PROJECT)
class TodosoSuggestionService(private val project: Project) {

    /**
     * Mengambil daftar saran gabungan (Tags + Related Tasks) berdasarkan prefix.
     */
    fun getSuggestions(
        prefix: String,
        popularTags: List<String>,
        allTasks: List<TodoTask>
    ): List<SuggestionItem> {
        val suggestions = mutableListOf<SuggestionItem>()

        // 1. Tag Suggestions
        suggestions.addAll(
            popularTags
                .filter { it.startsWith(prefix, ignoreCase = true) }
                .map {
                    SuggestionItem(
                        it,
                        "Popular Tags",
                        IconLoader.getIcon("/actions/checked.png", javaClass)
                    )
                }
        )

        // 2. Task Suggestions jika tag lengkap
        if (prefix.isNotEmpty()) {
            val relatedTasks = allTasks.filter { task ->
                task.tags.any { it.equals(prefix, ignoreCase = true) }
            }.sortedByDescending { it.metadata.createdDate ?: "" }

            suggestions.addAll(
                relatedTasks.map { task ->
                    SuggestionItem(
                        text = task.description.take(40) + (if (task.description.length > 40) "..." else ""),
                        category = "Related Tasks",
                        icon = IconLoader.getIcon("/nodes/variable.png", javaClass),
                        isTask = true,
                        taskId = task.id
                    )
                }
            )
        }
        return suggestions
    }

    /**
     * Mencari prefix tag (#...) terakhir dari posisi kursor.
     */
    fun getActivePrefix(text: String, caretPos: Int): String? {
        if (caretPos <= 0) return null

        val lastHash = text.substring(0, caretPos).lastIndexOf('#')
        if (lastHash == -1) return null

        val sub = text.substring(lastHash + 1, caretPos)
        return if (sub.contains(" ")) null else sub
    }
}