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
        val description = task.description.trim()
        append(description)

        if (task.tags.isNotEmpty()) {
            val tagsToAppend = task.tags.filter { tag ->
                !description.contains("#$tag")
            }
            if (tagsToAppend.isNotEmpty()) {
                append(" ").append(tagsToAppend.joinToString(" ") { if (it.startsWith("#")) it else "#$it" })
            }
        }

        val dateTokens = task.metadata.toEmojiTokens()
        if (dateTokens.isNotEmpty()) {
            val tokensToAppend = dateTokens.filter { token ->
                !description.contains(token)
            }
            if (tokensToAppend.isNotEmpty()) {
                append(" ").append(tokensToAppend.joinToString(" "))
            }
        }
        
        if (task.isPersistentId || task.id.isNotBlank()) {
            if (!description.contains("🆔 ${task.id}") && !description.contains("🆔${task.id}")) {
                append(" 🆔 ").append(task.id)
            }
        }

        if (task.metadata.notes.isNotBlank()) {
            append(" // ").append(task.metadata.notes.trim())
        }
    }
}