package com.github.nndwn.todoso.toolWindow.inputWindow.components

import com.github.nndwn.todoso.toolWindow.inputWindow.SuggestionItem
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

/**
 * Modern overlay panel for suggestions, integrated directly into the main panel hierarchy. Uses a fully custom renderer
 * to avoid deprecated APIs and achieve modern look.
 */
class SuggestionOverlayPanel(private val onItemSelected: (SuggestionItem) -> Unit) :
  JBPanel<SuggestionOverlayPanel>(BorderLayout()) {

  private val listModel = CollectionListModel<SuggestionItem>()
  private val list =
    JBList(listModel).apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      isFocusable = false

      cellRenderer = ListCellRenderer { list, value, index, isSelected, _ ->
        val rootPanel = JPanel(GridBagLayout())
        val gbc =
          GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
          }

        // 1. Header Logic (Show only if category changes)
        val showHeader = index == 0 || listModel.items[index - 1].category != value.category
        if (showHeader) {
          val headerLabel =
            JBLabel(value.category).apply {
              font = JBUI.Fonts.label().deriveFont(Font.BOLD, 10f)
              foreground = UIUtil.getLabelDisabledForeground()
              border = JBUI.Borders.empty(10, 10, 4, 0)
            }
          gbc.gridy = 0
          rootPanel.add(headerLabel, gbc)
        }

        // 2. Item Content
        val itemPanel =
          JPanel(BorderLayout(8, 0)).apply {
            isOpaque = true
            background = if (isSelected) list.selectionBackground else list.background
            border = JBUI.Borders.empty(4, 8)

            // Icon
            if (value.icon != null) {
              add(JLabel(value.icon), BorderLayout.WEST)
            }

            // Text + Subtext
            val textContainer =
              SimpleColoredComponent().apply {
                val title = if (value.isTask) value.text else "#${value.text}"
                append(title, SimpleTextAttributes.REGULAR_ATTRIBUTES)

                if (!value.subText.isNullOrBlank()) {
                  append("  ${value.subText}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
              }
            add(textContainer, BorderLayout.CENTER)
          }

        gbc.gridy = 1
        rootPanel.add(itemPanel, gbc)

        rootPanel.background = list.background
        rootPanel
      }
    }

  init {
    isOpaque = false
    isVisible = false
    // Berikan sedikit padding internal agar isi tidak mepet ke border bulat
    border = JBUI.Borders.empty(4)

    val scrollPane =
      JBScrollPane(list).apply {
        border = BorderFactory.createEmptyBorder()
        viewport.isOpaque = false
        isOpaque = false
      }

    add(scrollPane, BorderLayout.CENTER)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val arc = 12
    // Background dengan 4 sudut bulat
    g2.color = list.background
    g2.fillRoundRect(0, 0, width, height, arc, arc)

    // Border keliling dengan 4 sudut bulat
    g2.color = JBUI.CurrentTheme.Popup.borderColor(true)
    g2.stroke = BasicStroke(1.0f)
    g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
    g2.dispose()
  }

  fun updateItems(items: List<SuggestionItem>) {
    listModel.replaceAll(items)
    if (items.isNotEmpty()) {
      list.selectedIndex = 0
      isVisible = true

      // Calculate height based on headers and items with more accuracy
      var totalHeight = JBUI.scale(12) // Initial padding (top + bottom buffer)
      var currentCategory: String? = null
      for (item in items) {
        if (item.category != currentCategory) {
          totalHeight += JBUI.scale(38) // Header height (font + padding)
          currentCategory = item.category
        }
        totalHeight += JBUI.scale(30) // Item height (font + padding)
      }

      val maxHeight = JBUI.scale(400)
      // Tambahkan sedikit buffer (misal 5px) untuk mencegah scrollbar akibat pembulatan pixel
      preferredSize = Dimension(width, (totalHeight + JBUI.scale(5)).coerceAtMost(maxHeight))
    } else {
      hideOverlay()
    }
    revalidate()
    repaint()
  }

  fun hideOverlay() {
    isVisible = false
    listModel.removeAll()
  }

  fun moveUp() {
    if (listModel.size == 0) return
    val newIndex = (list.selectedIndex - 1).coerceAtLeast(0)
    list.selectedIndex = newIndex
    list.ensureIndexIsVisible(newIndex)
  }

  fun moveDown() {
    if (listModel.size == 0) return
    val newIndex = (list.selectedIndex + 1).coerceAtMost(listModel.size - 1)
    list.selectedIndex = newIndex
    list.ensureIndexIsVisible(newIndex)
  }

  fun getSelected(): SuggestionItem? = list.selectedValue

  fun confirmSelection() {
    getSelected()?.let { onItemSelected(it) }
    hideOverlay()
  }
}
