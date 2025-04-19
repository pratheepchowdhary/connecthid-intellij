package com.connecthid.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.Gray
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import javax.swing.*
import java.awt.*
import javax.swing.border.EmptyBorder

class ToolsMenuAction : AnAction(AllIcons.General.GearPlain) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.icon = AllIcons.General.GearPlain
        e.presentation.text = ""  // No text, only icon
    }

    override fun actionPerformed(e: AnActionEvent) {
        val component = e.inputEvent?.component ?: return
        showPopupMenu(component, Point(0, component.height))
    }

    private fun showPopupMenu(component: Component, point: Point) {
        val listModel = DefaultListModel<MenuItem>()
        listModel.addElement(MenuItem("Find", AllIcons.Actions.Find, "⌘3"))
        listModel.addElement(MenuItem("Debug", AllIcons.Actions.StartDebugger, "⌘5"))
        listModel.addElement(MenuItem("AI Assistant", AllIcons.Nodes.Plugin, ""))
        listModel.addElement(MenuItem("Coverage", AllIcons.General.Modified, ""))
        listModel.addElement(MenuItem("Hierarchy", AllIcons.Hierarchy.Class, ""))
        listModel.addElement(MenuItem("Learn", AllIcons.Actions.Help, ""))
        listModel.addElement(MenuItem("TODO", AllIcons.General.TodoDefault, ""))

        val list = JBList(listModel)
        list.cellRenderer = MenuItemRenderer()
        list.background = Gray._243
        list.border = null

        val popup = JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setRequestFocus(true)
            .createPopup()

        popup.show(RelativePoint(component, point))
    }

    private data class MenuItem(
        val text: String,
        val icon: Icon,
        val shortcut: String
    )

    private inner class MenuItemRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val panel = JPanel(BorderLayout())
            panel.border = EmptyBorder(8, 12, 8, 12)
            
            if (value is MenuItem) {
                // Icon and text
                val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
                leftPanel.isOpaque = false
                val iconLabel = JLabel(value.icon)
                val textLabel = JLabel(value.text)
                leftPanel.add(iconLabel)
                leftPanel.add(textLabel)
                
                // Shortcut
                val shortcutLabel = JLabel(value.shortcut)
                shortcutLabel.foreground = Color.GRAY
                
                panel.add(leftPanel, BorderLayout.WEST)
                panel.add(shortcutLabel, BorderLayout.EAST)
                
                panel.background = if (isSelected) {
                    Color(209, 220, 245)
                } else {
                    list.background
                }
            }
            
            return panel
        }
    }
} 