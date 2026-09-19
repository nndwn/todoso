package com.github.nndwn.todoso.toolWindow.search

import com.github.nndwn.todoso.TodosoBundle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class TodosoSearchPanel(private val onQueryChanged: (String) -> Unit) : JPanel(BorderLayout()) {

  private val searchField =
    SearchTextField().apply {
      textEditor.emptyText.text = TodosoBundle.message("todo.filter.search")
      addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            onQueryChanged(text)
          }
        }
      )
    }

  init {
    add(searchField, BorderLayout.CENTER)
    border = JBUI.Borders.empty(2, 5)
    isVisible = false
  }

  fun toggle() {
    isVisible = !isVisible
    if (isVisible) {
      searchField.requestFocusInWindow()
    } else {
      clear()
    }
  }

  fun clear() {
    searchField.text = ""
    onQueryChanged("")
  }

  fun hidePanel() {
    if (isVisible) {
      isVisible = false
      clear()
    }
  }

  fun setSearchText(query: String) {
    isVisible = true
    searchField.text = query
    onQueryChanged(query)
    searchField.requestFocusInWindow()
    revalidate()
    repaint()
  }

  fun requestSearchFocus() {
    searchField.requestFocusInWindow()
  }
}
