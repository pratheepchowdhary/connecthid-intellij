package com.connecthid.intellij.ui.filemanager.sftp.actions

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.ui.filemanager.sftp.SftpExplorerPanel
import com.connecthid.intellij.ui.filemanager.sftp.closeSFTP
import com.connecthid.intellij.ui.filemanager.sftp.createFile
import com.connecthid.intellij.ui.filemanager.sftp.createFolder
import com.connecthid.intellij.ui.filemanager.sftp.deleteSelectedNode
import com.connecthid.intellij.ui.filemanager.sftp.downloadFiles
import com.connecthid.intellij.ui.filemanager.sftp.refreshSelectedNode
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.SearchAction
import com.connecthid.intellij.ui.filemanager.sftp.uploadFiles
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout

class SftpToolbarActions(private val panel: SftpExplorerPanel,val serverItem: Server) {

    fun createToolbar(): JBPanel<*> {
        val actionGroup = DefaultActionGroup().apply {
            add(createUploadAction())
            add(createDownloadAction())
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
            panel.tree.uploadFiles()
        }
    }

    private fun createDownloadAction() = object : AnAction({ "Download" }, AllIcons.Actions.Download) {
        override fun actionPerformed(e: AnActionEvent) {
            println("Download action triggered")
            panel.tree.downloadFiles()
        }
    }

    private fun createNewFileAction() = object : AnAction({ "Create File" }, AllIcons.Actions.AddFile) {
        override fun actionPerformed(e: AnActionEvent) {
            println("Create file action triggered")
            panel.tree.createFile(panel.project)
        }
    }

    private fun createNewFolderAction() = object : AnAction({ "New Folder" }, AllIcons.Actions.NewFolder) {
        override fun actionPerformed(e: AnActionEvent) {
            println("New folder action triggered")
            panel.tree.createFolder()
        }
    }

    private fun createRefreshAction() = object : AnAction({ "Refresh" }, AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            panel.tree.refreshSelectedNode()
        }
    }

    private fun createStopAction() = object : AnAction({ "Stop" }, AllIcons.Actions.Suspend) {
        override fun actionPerformed(e: AnActionEvent) {
            panel.project.closeSFTP(panel)
        }

        override fun update(e: AnActionEvent) {
            // Enable the button only if there are active operations
            e.presentation.isEnabled = panel.loadingStates.isNotEmpty() ||
                    panel.fileSystem.isConnected()
        }
    }
}
