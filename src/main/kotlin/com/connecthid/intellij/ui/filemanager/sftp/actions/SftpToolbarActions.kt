package com.connecthid.intellij.ui.filemanager.sftp.actions

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.ui.filemanager.sftp.SftpExplorerPanel
import com.connecthid.intellij.ui.filemanager.sftp.closeSFTP
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.SearchAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBPanel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.tree.DefaultMutableTreeNode

class SftpToolbarActions(private val panel: SftpExplorerPanel,val serverItem: Server) {

    fun createToolbar(): JBPanel<*> {
        val actionGroup = DefaultActionGroup().apply {
            add(createUploadAction())
            add(createNewFileAction())
            add(createNewFolderAction())
            add(SearchAction(panel.project,serverItem))
            add(createRefreshAction())
            addSeparator()
            add(createStopAction())
        }

        val actionManager = ActionManager.getInstance()
        val actionToolbar: ActionToolbar = actionManager.createActionToolbar("SftpToolbar", actionGroup, true)
        actionToolbar.targetComponent = panel

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(actionToolbar.component, BorderLayout.EAST)
        }
    }

    private fun createUploadAction() = object : AnAction({ "Upload" }, AllIcons.Actions.Upload) {
        override fun actionPerformed(e: AnActionEvent) {
            println("Upload action triggered")
            // TODO: Implement upload functionality
        }
    }

    private fun createNewFileAction() = object : AnAction({ "Create File" }, AllIcons.Actions.AddFile) {
        override fun actionPerformed(e: AnActionEvent) {
            println("Create file action triggered")
            // TODO: Implement file creation
        }
    }

    private fun createNewFolderAction() = object : AnAction({ "New Folder" }, AllIcons.Actions.NewFolder) {
        override fun actionPerformed(e: AnActionEvent) {
            println("New folder action triggered")
            // TODO: Implement folder creation
        }
    }

    private fun createRefreshAction() = object : AnAction({ "Refresh" }, AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            with(panel) {
                rootNode.removeAllChildren()
                rootNode.add(DefaultMutableTreeNode("Loading..."))
                fileSystem.refresh(true)
                coroutineScope.launch {
                    loadChildren(rootNode, fileSystem.findFileByPath(rootPath))
                }
            }
        }
    }

    private fun createStopAction() = object : AnAction({ "Stop" }, AllIcons.Actions.Suspend) {
        override fun actionPerformed(e: AnActionEvent) {
            panel.project.closeSFTP(panel)
        }

        override fun update(e: AnActionEvent) {
            // Enable the button only if there are active operations
            e.presentation.isEnabled = panel.loadingStates.isNotEmpty() ||
                    panel.fileSystem.getConnection()?.isConnected() == true
        }
    }
}
