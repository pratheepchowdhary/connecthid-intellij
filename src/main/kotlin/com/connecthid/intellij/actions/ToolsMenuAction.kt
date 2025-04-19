package com.connecthid.intellij.actions

import com.connecthid.intellij.ui.menu.MenuItemRenderer
import com.connecthid.intellij.ui.menu.MenuItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.Gray
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import java.awt.*
import javax.swing.*

class ToolsMenuAction : AnAction() {


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


} 