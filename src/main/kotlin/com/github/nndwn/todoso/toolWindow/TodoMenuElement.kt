package com.github.nndwn.todoso.toolWindow

import com.intellij.openapi.actionSystem.*
import javax.swing.Icon
import javax.swing.JComponent

sealed interface TodoMenuElement {
  data class Action(
    val text: String,
    val icon: Icon? = null,
    val iconProvider: (() -> Icon?)? = null,
    val shortcut: ShortcutSet? = null,
    val isEnabled: () -> Boolean = { true },
    val onAction: () -> Unit,
  ) : TodoMenuElement

  data class SubMenu(
    val text: String,
    val icon: Icon? = null,
    val isEnabled: () -> Boolean = { true },
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
    iconProvider: (() -> Icon?)? = null,
    shortcut: ShortcutSet? = null,
    isEnabled: () -> Boolean = { true },
    onAction: () -> Unit,
  ) {
    elements.add(TodoMenuElement.Action(text, icon, iconProvider, shortcut, isEnabled, onAction))
  }

  fun subMenu(
    text: String,
    icon: Icon? = null,
    isEnabled: () -> Boolean = { true },
    init: TodoMenuBuilder.() -> Unit,
  ) {
    val builder = TodoMenuBuilder()
    builder.init()
    elements.add(TodoMenuElement.SubMenu(text, icon, isEnabled, builder.build()))
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

fun List<TodoMenuElement>.toActionGroup(targetComponent: JComponent): DefaultActionGroup {
  val group = DefaultActionGroup()
  fillActionGroup(this, group, targetComponent)
  return group
}

private fun fillActionGroup(
  elements: List<TodoMenuElement>,
  group: DefaultActionGroup,
  targetComponent: JComponent,
) {
  elements.forEach { element ->
    when (element) {
      is TodoMenuElement.Action -> {
        val action =
          object : AnAction(element.text, null, element.icon) {
            override fun actionPerformed(e: AnActionEvent) = element.onAction()

            override fun update(e: AnActionEvent) {
              e.presentation.isEnabled = element.isEnabled()
              element.iconProvider?.let { e.presentation.icon = it() }
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
          }
        element.shortcut?.let { action.registerCustomShortcutSet(it, targetComponent) }
        group.add(action)
      }
      is TodoMenuElement.SubMenu -> {
        val subGroup =
          object : DefaultActionGroup() {
            override fun update(e: AnActionEvent) {
              e.presentation.isEnabled = element.isEnabled()
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
          }
        subGroup.isPopup = true
        subGroup.templatePresentation.text = element.text
        subGroup.templatePresentation.icon = element.icon

        fillActionGroup(element.children, subGroup, targetComponent)
        group.add(subGroup)
      }
      TodoMenuElement.Separator -> group.addSeparator()
      is TodoMenuElement.Toggle -> {
        group.add(
          object : ToggleAction(element.text, null, element.icon) {
            override fun isSelected(e: AnActionEvent) = element.isSelected()

            override fun setSelected(e: AnActionEvent, state: Boolean) = element.onToggle(state)

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
          }
        )
      }
    }
  }
}
