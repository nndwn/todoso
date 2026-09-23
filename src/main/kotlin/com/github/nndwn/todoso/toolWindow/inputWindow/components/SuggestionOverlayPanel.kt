package com.github.nndwn.todoso.toolWindow.inputWindow.components

import com.github.nndwn.todoso.toolWindow.inputWindow.SuggestionItem
import com.github.nndwn.todoso.toolWindow.inputWindow.SuggestionType
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * Modern overlay panel for suggestions, integrated directly into the main panel hierarchy.
 * Uses a custom scrollable container to support multiline task descriptions alongside single-line tags.
 */
class SuggestionOverlayPanel(private val onItemSelected: (SuggestionItem) -> Unit) :
  JBPanel<SuggestionOverlayPanel>(BorderLayout()) {

  private var isSelectionActive = false
  private var selectedIndex = 0
  private val itemsList = mutableListOf<SuggestionItem>()
  private val itemPanels = mutableListOf<JPanel>()

  private val itemsContainer = JBPanel<JBPanel<*>>(null).apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
  }

  private val scrollPane = JBScrollPane(itemsContainer).apply {
    border = BorderFactory.createEmptyBorder()
    viewport.isOpaque = false
    isOpaque = false
  }

  init {
    isOpaque = false
    isVisible = false
    border = JBUI.Borders.empty(4)
    add(scrollPane, BorderLayout.CENTER)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val arc = 12
    g2.color = UIUtil.getListBackground()
    g2.fillRoundRect(0, 0, width, height, arc, arc)

    g2.color = JBUI.CurrentTheme.Popup.borderColor(true)
    g2.stroke = BasicStroke(1.0f)
    g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
    g2.dispose()
  }

  fun updateItems(items: List<SuggestionItem>) {
    itemsList.clear()
    itemsList.addAll(items)
    itemsContainer.removeAll()
    itemPanels.clear()

    if (items.isNotEmpty()) {
      isSelectionActive = false
      selectedIndex = 0
      isVisible = true

      items.forEachIndexed { index, item ->
        val rootPanel = JPanel(GridBagLayout()).apply {
          background = UIUtil.getListBackground()
        }
        val gbc = GridBagConstraints().apply {
          fill = GridBagConstraints.HORIZONTAL
          weightx = 1.0
          gridx = 0
        }

        // Header Logic
        val showHeader = index == 0 || items[index - 1].category != item.category
        if (showHeader) {
          val headerLabel = JBLabel(item.category).apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, 10f)
            foreground = UIUtil.getLabelDisabledForeground()
            border = JBUI.Borders.empty(10, 10, 4, 0)
          }
          gbc.gridy = 0
          rootPanel.add(headerLabel, gbc)
        }

        // Item Content
        val itemPanel = createItemPanel(item, index)
        gbc.gridy = 1
        rootPanel.add(itemPanel, gbc)

        itemsContainer.add(rootPanel)
        itemPanels.add(itemPanel)
      }

      updateSelection()
      itemsContainer.revalidate()
      itemsContainer.repaint()

      val containerHeight = itemsContainer.preferredSize.height
      val maxHeight = JBUI.scale(400)
      preferredSize = Dimension(width, (containerHeight + JBUI.scale(10)).coerceAtMost(maxHeight))

      SwingUtilities.invokeLater {
        scrollPane.verticalScrollBar.value = 0
      }
    } else {
      hideOverlay()
    }
    revalidate()
    repaint()
  }

  private fun createItemPanel(item: SuggestionItem, index: Int): JPanel {
    val itemPanel = JPanel(BorderLayout(8, 0)).apply {
      isOpaque = true
      border = JBUI.Borders.empty(4, 8)
    }

    if (item.icon != null) {
      itemPanel.add(JLabel(item.icon), BorderLayout.WEST)
    }

    if (item.isTask || item.type == SuggestionType.TASK) {
      val taskArea = JTextArea(item.text).apply {
        font = JBUI.Fonts.label()
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(2, 4)
        caretPosition = 0
      }
      itemPanel.add(taskArea, BorderLayout.CENTER)
    } else {
      val textContainer = SimpleColoredComponent().apply {
        isOpaque = false
        val title = item.tagDisplay ?: item.text
        append(title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        if (!item.subText.isNullOrBlank()) {
          append("  ${item.subText}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
      }
      itemPanel.add(textContainer, BorderLayout.CENTER)
    }

    val mouseAdapter = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        if (selectedIndex != index || !isSelectionActive) {
          isSelectionActive = true
          selectedIndex = index
          updateSelection()
        }
      }

      override fun mousePressed(e: MouseEvent) {
        isSelectionActive = true
        selectedIndex = index
        confirmSelection()
      }
    }

    itemPanel.addMouseListener(mouseAdapter)
    for (comp in itemPanel.components) {
      comp.addMouseListener(mouseAdapter)
    }

    return itemPanel
  }

  private fun updateSelection() {
    itemPanels.forEachIndexed { index, panel ->
      val isSelected = index == selectedIndex
      val bg = when {
        isSelected && isSelectionActive -> UIUtil.getListSelectionBackground(true)
        isSelected && !isSelectionActive -> JBColor.namedColor("List.hoverBackground", JBColor(0xDFE1E5, 0x4E5157))
        else -> UIUtil.getListBackground()
      }
      val fg = if (isSelected && isSelectionActive) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()

      panel.background = bg
      val item = itemsList[index]

      for (comp in panel.components) {
        if (comp is JTextArea) {
          comp.foreground = fg
        } else if (comp is SimpleColoredComponent) {
          comp.clear()
          val title = item.tagDisplay ?: item.text
          val baseAttr = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fg)
          comp.append(title, baseAttr)
          if (!item.subText.isNullOrBlank()) {
            comp.append("  ${item.subText}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
        }
      }
    }

    if (selectedIndex in itemPanels.indices) {
      val activePanel = itemPanels[selectedIndex]
      itemsContainer.scrollRectToVisible(activePanel.parent.bounds)
    }
  }

  fun hideOverlay() {
    isVisible = false
    itemsList.clear()
    itemPanels.clear()
    itemsContainer.removeAll()
    scrollPane.verticalScrollBar.value = 0
  }

  fun moveUp() {
    if (itemsList.isEmpty()) return
    isSelectionActive = true
    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
    updateSelection()
  }

  fun moveDown() {
    if (itemsList.isEmpty()) return
    isSelectionActive = true
    selectedIndex = (selectedIndex + 1).coerceAtMost(itemsList.size - 1)
    updateSelection()
  }

  fun getSelected(): SuggestionItem? {
    if (selectedIndex in itemsList.indices) {
      return itemsList[selectedIndex]
    }
    return null
  }

  fun confirmSelection() {
    getSelected()?.let { onItemSelected(it) }
    hideOverlay()
  }
}
