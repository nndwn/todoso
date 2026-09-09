package com.github.nndwn.todoso.toolWindow.inputWindow.components

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JComponent
import javax.swing.JTextArea

class RoundedInputPanel(private val textArea: JTextArea) : JBPanel<RoundedInputPanel>(BorderLayout()) {

    init {
        isOpaque = false
        border = JBUI.Borders.empty(2)
        
        val scrollPane = JBScrollPane(textArea).apply {
            border = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = false
        }
        add(scrollPane, BorderLayout.CENTER)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        val arc = 12
        val thickness = if (textArea.hasFocus()) 2.0f else 1.0f
        val offset = thickness / 2f

        val rectX = offset.toInt()
        val rectY = offset.toInt()
        val rectW = width - thickness.toInt() - 1
        val rectH = height - thickness.toInt() - 1

        g2.color = textArea.background
        g2.fillRoundRect(rectX, rectY, rectW, rectH, arc, arc)

        g2.color = if (textArea.hasFocus()) JBUI.CurrentTheme.Focus.focusColor() else JBColor.border()
        g2.stroke = BasicStroke(thickness)
        g2.drawRoundRect(rectX, rectY, rectW, rectH, arc, arc)
        g2.dispose()
    }
}
