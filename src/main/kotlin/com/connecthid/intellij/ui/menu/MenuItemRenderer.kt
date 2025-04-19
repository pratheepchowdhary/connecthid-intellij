package com.connecthid.intellij.ui.menu

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

 class MenuItemRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>, value: Any?, index: Int,
        isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val panel = JPanel(BorderLayout())
        panel.setBorder(JBUI.Borders.empty(8, 12))

        if (value is MenuItem) {
            // Icon and text
            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
            leftPanel.setOpaque(false)
            val iconLabel = JLabel(value.icon)
            val textLabel = JLabel(value.text)
            leftPanel.add(iconLabel)
            leftPanel.add(textLabel)


            // Shortcut
            val shortcutLabel = JLabel(value.shortcut)
            shortcutLabel.setForeground(JBColor.GRAY)

            panel.add(leftPanel, BorderLayout.WEST)
            panel.add(shortcutLabel, BorderLayout.EAST)

            if (isSelected) {
                panel.setBackground(Color(209, 220, 245))
            } else {
                panel.setBackground(list.getBackground())
            }
        }

        return panel
    }
}