package com.connecthid.intellij.ui.workspaces

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.models.Workspace
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPopupMenu

class WorkspaceItem(val workspace: Workspace) : JBPanel<WorkspaceItem>(GridBagLayout()) {
    var listener: Listener? = null

    private val hoverListener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            background = HOVER_COLOR
            repaint()
        }
        override fun mouseExited(e: MouseEvent) {
            background = JBColor.background()
            repaint()
        }
    }

    init {
        background = JBColor.background()
        minimumSize = Dimension(0, LIST_ITEM_HEIGHT)
        maximumSize = Dimension(Int.MAX_VALUE, LIST_ITEM_HEIGHT)
        preferredSize = Dimension(0, LIST_ITEM_HEIGHT)

        val iconLabel = JBLabel(AllIcons.Nodes.Workspace).apply {
            preferredSize = Dimension(80, 80)
        }
        add(iconLabel, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            gridwidth = 1
            gridheight = 2
            fill = GridBagConstraints.VERTICAL
            weighty = 1.0
            insets = JBUI.insets(0, 20)
        })

        val titleLabel = JBLabel(workspace.folderName)
        titleLabel.componentStyle = UIUtil.ComponentStyle.LARGE
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        add(titleLabel, GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            gridwidth = 1
            gridheight = 1
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            weightx = 1.0
            insets = JBUI.insetsTop(8)
        })

        val pathLabel = JBLabel("sftp://${workspace.server}${workspace.path}")
        pathLabel.componentStyle = UIUtil.ComponentStyle.SMALL
        add(pathLabel, GridBagConstraints().apply {
            gridx = 1
            gridy = 1
            gridwidth = 1
            gridheight = 1
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            weightx = 1.0
            insets = JBUI.insetsBottom(8)
        })

        val moreButton = JButton().apply {
            icon = AllIcons.Actions.More
            isOpaque = false
            border = JBUI.Borders.empty(6, 12)
            preferredSize = Dimension(32, 32)
            toolTipText = "More"
            isFocusable = false
            setContentAreaFilled(false)
        }
        add(moreButton, GridBagConstraints().apply {
            gridx = 2
            gridy = 0
            gridwidth = 1
            gridheight = 2
            weightx = 0.0
            weighty = 1.0
            insets = JBUI.insetsRight(20)
            anchor = GridBagConstraints.CENTER
        })
        moreButton.addActionListener { e ->
            showPopupMenu(moreButton)
        }

        addMouseListener(hoverListener)
    }

    private fun showPopupMenu(button: JButton) {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(object : AnAction({ PluginBundle.message("open_workspace") }, AllIcons.Nodes.WebFolder) {
            override fun actionPerformed(e: AnActionEvent) {
                listener?.onOpenWorkspaceClicked(workspace)
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("edit") }, AllIcons.Actions.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                // Optionally implement edit logic here
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("delete") }, AllIcons.Actions.DeleteTag) {
            override fun actionPerformed(e: AnActionEvent) {
                listener?.onDeleteWorkspaceClicked(workspace)
            }
        })
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("WorkspacePopup", actionGroup)
        popupMenu.component.show(button, button.width, button.height)
    }

    interface Listener {
        fun onOpenWorkspaceClicked(workspace: Workspace)
        fun onDeleteWorkspaceClicked(workspace: Workspace)
    }

    private companion object {
        private const val LIST_ITEM_HEIGHT = 64
        val HOVER_COLOR = JBColor.namedColor(
            "Plugins.lightSelectionBackground",
            JBColor(0xF5F9FF, 0x36393B)
        )
    }
}
