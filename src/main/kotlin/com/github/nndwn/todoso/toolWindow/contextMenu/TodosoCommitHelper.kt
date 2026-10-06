package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.TodoTask
import com.intellij.ide.DataManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.TextAccessor
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.StringSelection
import javax.swing.text.JTextComponent

object TodosoCommitHelper {

  fun setCommitMessage(task: TodoTask, project: Project, onError: (String) -> Unit) {
    val formattedMessage = formatTaskCommitMessage(task)

    val commitToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT)
      ?: ToolWindowManager.getInstance(project).getToolWindow("Commit")

    if (commitToolWindow == null) {
      onError(TodosoBundle.message("todo.action.commit.window_not_found"))
      return
    }

    commitToolWindow.activate {
      ApplicationManager.getApplication().invokeLater {
        val dataContext = DataManager.getInstance().getDataContext(commitToolWindow.component)

        var inserted = false

        // 1. Try VcsDataKeys.COMMIT_WORKFLOW_UI
        val workflowUi = VcsDataKeys.COMMIT_WORKFLOW_UI.getData(dataContext)
        if (workflowUi != null) {
          val commitMessageUi = workflowUi.commitMessageUi
          val currentText = commitMessageUi.getText()
          val newText = if (currentText.isBlank()) formattedMessage else "$currentText\n$formattedMessage"
          commitMessageUi.setText(newText)
          commitMessageUi.focus()
          inserted = true
        }

        // 2. Try VcsDataKeys.COMMIT_MESSAGE_DOCUMENT
        if (!inserted) {
          val document = VcsDataKeys.COMMIT_MESSAGE_DOCUMENT.getData(dataContext)
          if (document != null) {
            val currentText = document.text
            val newText = if (currentText.isBlank()) formattedMessage else "$currentText\n$formattedMessage"
            WriteCommandAction.runWriteCommandAction(project) {
              document.setText(newText)
            }
            inserted = true
          }
        }

        // 3. Try VcsDataKeys.COMMIT_MESSAGE_CONTROL
        if (!inserted) {
          val commitMessageI = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(dataContext)
          if (commitMessageI != null) {
            commitMessageI.setCommitMessage(formattedMessage)
            inserted = true
          }
        }

        // 4. Try Swing Component hierarchy search
        if (!inserted) {
          inserted = appendToCommitComponent(commitToolWindow.component, formattedMessage)
        }

        // 5. Fallback: Copy to clipboard & notify
        if (!inserted) {
          CopyPasteManager.getInstance().setContents(StringSelection(formattedMessage))
          NotificationGroupManager.getInstance()
            .getNotificationGroup("com.github.nndwn.todoso.notifications")
            .createNotification(
              TodosoConstants.PLUGIN_NAME,
              TodosoBundle.message("todo.action.commit.copied_fallback"),
              NotificationType.INFORMATION,
            )
            .notify(project)
        }
      }
    }
  }

  fun formatTaskCommitMessage(task: TodoTask): String {
    return if (task.id.isNotBlank() && !task.description.contains("🆔 ${task.id}")) {
      "- ${task.description} 🆔 ${task.id}"
    } else {
      "- ${task.description}"
    }
  }

  private fun appendToCommitComponent(component: Component, textToInsert: String): Boolean {
    if (component is TextAccessor) {
      val currentText = component.text
      val newText = if (currentText.isBlank()) textToInsert else "$currentText\n$textToInsert"
      component.text = newText
      return true
    }

    if (component is CommitMessageI) {
      component.setCommitMessage(textToInsert)
      return true
    }

    if (component is JTextComponent) {
      val currentText = component.text
      val newText = if (currentText.isBlank()) textToInsert else "$currentText\n$textToInsert"
      component.text = newText
      return true
    }

    if (component is Container) {
      for (child in component.components) {
        if (appendToCommitComponent(child, textToInsert)) {
          return true
        }
      }
    }
    return false
  }
}
