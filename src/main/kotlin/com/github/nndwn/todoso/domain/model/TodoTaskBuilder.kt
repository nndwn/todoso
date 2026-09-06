package com.github.nndwn.todoso.domain.model


object TodoTaskBuilder {

    /**
     * Rebuilds a structured [TodoTask] into a valid, standard Markdown task line.
     */
    fun rebuildTaskLine(task: TodoTask): String = buildString {

        append("- [${task.status.code}] ")
        if (task.priority != Priority.NONE && task.priority.emoji.isNotEmpty()) {
            append("${task.priority.emoji} ")
        }
        append(task.description.trim())

        if (task.tags.isNotEmpty()) {
            append(" ").append(task.tags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" })
        }

        val dateTokens = task.metadata.toEmojiTokens()
        if (dateTokens.isNotEmpty()) {
            append(" ").append(dateTokens.joinToString(" "))
        }
        
        if (task.isPersistentId || task.id.isNotBlank()) {
            append(" 🆔 ").append(task.id)
        }

        if (task.metadata.notes.isNotBlank()) {
            append(" // ").append(task.metadata.notes.trim())
        }
    }
}