package com.connecthid.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants

class ToolsGridPanel : JBPanel<ToolsGridPanel>() {
    init {
        layout = GridLayout(0, 3, 10, 10) // 3 columns, flexible rows
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // Example: adding items with IntelliJ icons
        add(createIconItem("Project", AllIcons.Actions.SwapPanels))
        add(createIconItem("Settings", AllIcons.Actions.SwapPanels))
        add(createIconItem("Add", AllIcons.Actions.SwapPanels))
        add(createIconItem("Delete", AllIcons.Actions.SwapPanels))
        // Add more as needed
    }

    private fun createIconItem(text: String, icon: Icon): JPanel {
        val panel: JBPanel<*> = JBPanel<JBPanel<*>>()
        panel.layout = BorderLayout()
        panel.isOpaque = false

        val iconLabel = JBLabel(icon)
        iconLabel.horizontalAlignment = SwingConstants.CENTER

        val textLabel = JBLabel(text)
        textLabel.horizontalAlignment = SwingConstants.CENTER

        panel.add(iconLabel, BorderLayout.CENTER)
        panel.add(textLabel, BorderLayout.SOUTH)

        return panel
    }
}