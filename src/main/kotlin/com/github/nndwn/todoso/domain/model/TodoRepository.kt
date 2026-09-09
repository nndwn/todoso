package com.github.nndwn.todoso.domain.model


data class TodoTask(
    val id : String,
    val isPersistentId : Boolean,
    val rawText : String,
    val description : String,
    val status : TaskStatus,
    val priority : Priority,
    val tags : List<String>,
    val lineNumber : Int,
    val metadata : Metadata
)

data class Metadata(
    val startDate: String? = null,
    val dueDate: String? = null,
    val endDate: String? = null,
    val cancelDate: String? = null,
    val createdDate: String? = null,
    val editedDate: String? = null,
    val notes: String = ""
){
    companion object {
        const val ICON_START = "🛫"
        const val ICON_DUE = "📅"
        const val ICON_DONE = "✅"
        const val ICON_CANCEL = "❌"
        const val ICON_CREATED = "➕"
        const val ICON_EDITED = "📝"

        val DATE_EMOJIS = listOf(ICON_START, ICON_DUE, ICON_DONE, ICON_CANCEL, ICON_CREATED, ICON_EDITED)
    }

    fun toEmojiTokens(): List<String> = listOfNotNull(
        startDate?.let { "$ICON_START $it" },
        dueDate?.let { "$ICON_DUE $it" },
        endDate?.let { "$ICON_DONE $it" },
        cancelDate?.let { "$ICON_CANCEL $it" },
        createdDate?.let { "$ICON_CREATED $it" },
        editedDate?.let { "$ICON_EDITED $it" }
    )
}

data class ExtractedId(
    val id: String,
    val isPersistentId: Boolean
)