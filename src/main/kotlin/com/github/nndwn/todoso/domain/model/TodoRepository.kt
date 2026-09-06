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
    val notes: String = ""
){
    fun toEmojiTokens(): List<String> = listOfNotNull(
        startDate?.let { "🛫 $it" },
        dueDate?.let { "📅 $it" },
        endDate?.let { "✅ $it" },
        cancelDate?.let { "❌ $it" },
        createdDate?.let { "➕ $it" }
    )
}

data class ExtractedId(
    val id: String,
    val isPersistentId: Boolean
)