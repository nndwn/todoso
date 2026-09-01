package com.github.nndwn.todoso.toolWindow

import com.intellij.openapi.actionSystem.ShortcutSet
import javax.swing.Icon

sealed interface TodoMenuElement {
  data class Action(
    val text: String,
    val icon: Icon? = null,
    val shortcut: ShortcutSet? = null,
    val isEnabled: () -> Boolean = { true },
    val onAction: () -> Unit,
  ) : TodoMenuElement

  data class SubMenu(
    val text: String,
    val icon: Icon? = null,
    val children: List<TodoMenuElement>,
  ) : TodoMenuElement

  object Separator : TodoMenuElement

  data class Toggle(
    val text: String,
    val icon: Icon? = null,
    val isSelected: () -> Boolean,
    val onToggle: (Boolean) -> Unit,
  ) : TodoMenuElement
}

class TodoMenuBuilder {
  private val elements = mutableListOf<TodoMenuElement>()

  fun item(
    text: String,
    icon: Icon? = null,
    shortcut: ShortcutSet? = null,
    isEnabled: () -> Boolean = { true },
    onAction: () -> Unit,
  ) {
    elements.add(TodoMenuElement.Action(text, icon, shortcut, isEnabled, onAction))
  }

  fun subMenu(text: String, icon: Icon? = null, init: TodoMenuBuilder.() -> Unit) {
    val builder = TodoMenuBuilder()
    builder.init()
    elements.add(TodoMenuElement.SubMenu(text, icon, builder.build()))
  }

  fun separator() {
    elements.add(TodoMenuElement.Separator)
  }

  fun toggle(text: String, icon: Icon? = null, isSelected: () -> Boolean, onToggle: (Boolean) -> Unit) {
    elements.add(TodoMenuElement.Toggle(text, icon, isSelected, onToggle))
  }

  fun build(): List<TodoMenuElement> = elements
}

fun buildTodoMenu(init: TodoMenuBuilder.() -> Unit): List<TodoMenuElement> {
  val builder = TodoMenuBuilder()
  builder.init()
  return builder.build()
}
