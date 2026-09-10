package com.github.nndwn.todoso.toolWindow.contextMenu

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.actionSystem.ToggleAction
import javax.swing.Icon
import javax.swing.JComponent

sealed interface TodosoMenuElement {
  data class Action(
    val text: String,
    val icon: Icon? = null,
    val iconProvider: (() -> Icon)? = null,
    val shortcut: ShortcutSet? = null,
    val isEnabled: () -> Boolean = { true },
    val onAction: () -> Unit,
  ) : TodosoMenuElement

  data class SubMenu(
    val text: String,
    val icon: Icon? = null,
    val isEnabled: () -> Boolean = { true },
    val children: List<TodosoMenuElement>,
  ) : TodosoMenuElement

  object Separator : TodosoMenuElement

  data class Toggle(
    val text: String,
    val icon: Icon? = null,
    val isSelected: () -> Boolean,
    val onToggle: (Boolean) -> Unit,
  ) : TodosoMenuElement
}

class TodoMenuBuilder {
  private val element = mutableListOf<TodosoMenuElement>()

  fun item(
    text: String,
    icon: Icon? = null,
    iconProvider: (() -> Icon)? = null,
    shortcut: ShortcutSet? = null,
    isEnabled: () -> Boolean = { true },
    onAction: () -> Unit,
  ) {
    element.add(TodosoMenuElement.Action(text, icon, iconProvider, shortcut, isEnabled, onAction))
  }

  fun subMenu(
    text: String,
    icon: Icon? = null,
    isEnabled: () -> Boolean = { true },
    init: TodoMenuBuilder.() -> Unit,
  ) {
    val builder = TodoMenuBuilder()
    builder.init()
    element.add(TodosoMenuElement.SubMenu(text, icon, isEnabled, builder.build()))
  }

  fun separator() {
    element.add(TodosoMenuElement.Separator)
  }

  fun toggle(text: String, icon: Icon? = null, isSelected: () -> Boolean, onToggle: (Boolean) -> Unit) {
    element.add(TodosoMenuElement.Toggle(text, icon, isSelected, onToggle))
  }

  fun build(): List<TodosoMenuElement> = element
}

fun buildTodosoMenu(init: TodoMenuBuilder.() -> Unit): List<TodosoMenuElement> {
  val builder = TodoMenuBuilder()
  builder.init()
  return builder.build()
}

fun List<TodosoMenuElement>.toActionGroup(targetComponent: JComponent): DefaultActionGroup {
  val group = DefaultActionGroup()
  fillActionGroup(this, group, targetComponent)
  return group
}

private fun fillActionGroup(
  elements: List<TodosoMenuElement>,
  group: DefaultActionGroup,
  targetComponent: JComponent,
) {
  elements.forEach { element ->
    when (element) {
      is TodosoMenuElement.Action -> {
        val action =
          object : AnAction(element.text, null, element.icon) {
            override fun actionPerformed(e: AnActionEvent) = element.onAction()

            override fun update(e: AnActionEvent) {
              e.presentation.isEnabled = element.isEnabled()
              element.iconProvider?.let { e.presentation.icon = it() }
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
          }
        element.shortcut?.let { action.registerCustomShortcutSet(it, targetComponent) }
        group.add(action)
      }
      is TodosoMenuElement.SubMenu -> {
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
      TodosoMenuElement.Separator -> group.addSeparator()
      is TodosoMenuElement.Toggle -> {
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
