package com.connecthid.intellij.ui.dialog

import com.connecthid.intellij.ui.menu.MenuItem
import com.connecthid.intellij.ui.menu.MenuItemRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.Gray
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JPanel

class SSHToolsWindowFactory : ToolWindowFactory {
    private var project: Project? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        this.project = project
        val mainPanel = JPanel(BorderLayout())
        val iconLabel = JLabel(AllIcons.General.GearPlain)
        iconLabel.setBorder(JBUI.Borders.empty(5))
        iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

        iconLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showPopupMenu(e.getComponent(), e.getPoint())
            }
        })

        mainPanel.add(iconLabel, BorderLayout.CENTER)
        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.getContentManager().addContent(content)
    }
   
    private fun showPopupMenu(component: Component, point: Point) {
        val listModel = DefaultListModel<MenuItem?>()
        listModel.addElement(MenuItem("Find", AllIcons.Actions.Find, "⌘3"))
        listModel.addElement(MenuItem("Debug", AllIcons.Actions.StartDebugger, "⌘5"))
        listModel.addElement(MenuItem("AI Assistant", AllIcons.Nodes.Plugin, ""))
        listModel.addElement(MenuItem("Coverage", AllIcons.General.Modified, ""))
        listModel.addElement(MenuItem("Hierarchy", AllIcons.Hierarchy.Class, ""))
        listModel.addElement(MenuItem("Learn", AllIcons.Actions.Help, ""))
        listModel.addElement(MenuItem("TODO", AllIcons.General.TodoDefault, ""))

        val list = JBList<MenuItem?>(listModel)
        list.setCellRenderer(MenuItemRenderer())
        list.setBackground(Gray._243)
        list.setBorder(null)

        val popup = JBPopupFactory.getInstance()
            .createListPopupBuilder<MenuItem?>(list)
            .setRequestFocus(true)
            .createPopup()

        popup.show(RelativePoint(component, point))
    }
}