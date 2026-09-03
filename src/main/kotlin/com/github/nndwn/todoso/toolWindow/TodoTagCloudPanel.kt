package com.github.nndwn.todoso.toolWindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ScrollPaneConstants

class TodoTagCloudPanel(private val onTagSelected: (String?) -> Unit) : JBPanel<TodoTagCloudPanel>(BorderLayout()) {

  private val chipsPanel =
    JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 5)).apply {
      isOpaque = false
    }

  init {
    isOpaque = false

    val scrollPane =
      JBScrollPane(chipsPanel).apply {
        border = JBUI.Borders.empty()
        isOpaque = false
        viewport.isOpaque = false
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        preferredSize = JBUI.size(-1, 42)
      }
    add(scrollPane, BorderLayout.CENTER)
  }

  fun setTags(tags: Map<String, Int>, selectedTag: String?) {
    chipsPanel.removeAll()

    chipsPanel.add(TagChip("all", null, selectedTag == null) { onTagSelected(null) })

    tags.forEach { (tag, count) ->
      chipsPanel.add(TagChip(tag, count, selectedTag == tag) { onTagSelected(tag) })
    }

    revalidate()
    repaint()
  }

  private class TagChip(
    val tagName: String,
    val count: Int?,
    val isSelected: Boolean,
    val onClick: () -> Unit,
  ) : JBLabel() {
    init {
      text = if (count != null) "#$tagName ($count)" else "#$tagName"
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      border = JBUI.Borders.empty(4, 10)

      val baseFg = if (isSelected) JBColor.WHITE else JBColor.namedColor("Label.foreground", JBColor.BLACK)
      foreground = baseFg

      addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            if (e.button == MouseEvent.BUTTON1) onClick()
          }
        }
      )
    }

    override fun paintComponent(g: Graphics) {
      val g2 = g.create() as Graphics2D
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      val bg =
        if (isSelected) {
          JBColor.namedColor("Label.infoForeground", JBColor(0x2675BF, 0x2675BF))
        } else {
          JBColor.namedColor("Todo.Tag.Background", JBColor(0xE8E8E8, 0x393939))
        }

      g2.color = bg
      g2.fillRoundRect(0, 0, width, height, height, height)
      g2.dispose()

      super.paintComponent(g)
    }
  }
}
