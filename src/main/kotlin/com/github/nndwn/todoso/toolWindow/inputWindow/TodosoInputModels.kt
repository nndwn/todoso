package com.github.nndwn.todoso.toolWindow.inputWindow

import javax.swing.Icon

/** State visual & mode input untuk TodosoInputPanel */
sealed class InputMode {
  object Normal : InputMode()

  data class Edit(val originalText: String) : InputMode()

  object Cancel : InputMode()

  object Note : InputMode()
}

enum class SuggestionType{
  PRIORITY, TAGS , TASK
}

/** Representasi item dalam popup saran (Tags atau Tasks) */
data class SuggestionItem(
  val text: String,
  val category: String,
  val icon: Icon? = null,
  val type : SuggestionType,
  val subText: String? = null,
  val isTask: Boolean = false,
  val taskId: String? = null,
  val tagDisplay: String? = null,
)

/** Navigasi aksi untuk suggestion popup */
enum class SuggestionNav {
  UP, DOWN, ENTER
}
