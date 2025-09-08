package com.connecthid.intellij.ui.workspaces

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Workspace
import com.connecthid.intellij.ui.servers.ServerListPanel
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class WorkSpacesPanel internal constructor(val project: Project) : JBPanel<ServerListPanel>() {
    private val connection = project.getSSHService()
    private var workspaces: MutableList<Workspace> = connection.getWorkspaces().toMutableList()
    private var header: JPanel? = null
    private var headerLabel: JBLabel? = null

    init {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
        background = JBColor.background()
        buildHeader()
        rebuildUi()
    }

    private fun buildHeader() {
        val header = JPanel()
        header.layout = BoxLayout(header, BoxLayout.X_AXIS)
        header.background = JBColor.background()
        header.minimumSize = Dimension(0, 50)
        header.maximumSize = Dimension(Int.MAX_VALUE, 50)
        header.preferredSize = Dimension(0, 50)
        headerLabel = JBLabel("Workspaces (${workspaces.size})")
        header.add(headerLabel)
        header.add(javax.swing.Box.createHorizontalGlue())
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener { refreshWorkspaces() }
        header.add(refreshButton)
        this.header = header
        add(header)
    }

    private fun rebuildUi() {
        // Remove all WorkspaceItem components
        components.filterIsInstance<WorkspaceItem>().forEach { remove(it) }
        headerLabel?.text = "Workspaces (${workspaces.size})"
        for (workspace in workspaces) {
            val item = WorkspaceItem(workspace)
            item.listener = object : WorkspaceItem.Listener {
                override fun onOpenWorkspaceClicked(workspace: Workspace) {
                    javax.swing.JOptionPane.showMessageDialog(this@WorkSpacesPanel, "Open workspace: ${'$'}{workspace.folderName}")
                }
                override fun onDeleteWorkspaceClicked(workspace: Workspace) {
                    javax.swing.JOptionPane.showMessageDialog(this@WorkSpacesPanel, "Delete workspace: ${'$'}{workspace.folderName}")
                    workspaces.remove(workspace)
                    rebuildUi()
                }
            }
            add(item)
        }
        revalidate()
        repaint()
    }

    private fun refreshWorkspaces() {
        workspaces = connection.getWorkspaces().toMutableList()
        rebuildUi()
    }
}
