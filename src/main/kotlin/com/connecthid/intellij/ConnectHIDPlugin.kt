package com.connecthid.intellij

import com.connecthid.intellij.services.ConnectHidServiceImpl
import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.ui.rsync.FileSyncPanel
import com.connecthid.intellij.ui.servers.ServerListPanel
import com.connecthid.intellij.ui.workspaces.WorkSpacesPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory

class ConnectHIDPlugin : ToolWindowFactory {
    // Defer service retrieval to when they're actually needed
    private fun getConnectHidService() = ApplicationManager.getApplication().getService(ConnectHidServiceImpl::class.java)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Create main tabbed pane
        val tabbedPane = JBTabbedPane()
        // Add panels to tabs with required services
        tabbedPane.addTab("Servers", ServerListPanel(project))
        tabbedPane.addTab("Workspaces", WorkSpacesPanel(project))
        tabbedPane.addTab("Code Syncing", FileSyncPanel(project))
        // Add content to tool window
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(tabbedPane, "", false)
        toolWindow.contentManager.addContent(content)
        val ex = toolWindow as ToolWindowEx
        ex.stretchWidth(toolWindow.component.preferredSize.width)
    }
}

fun Project.getSSHService(): ServerConnectionService =
    ApplicationManager.getApplication().getService(ServerConnectionService::class.java)

fun Project.getConnectHidService(): ConnectHidServiceImpl =
    ApplicationManager.getApplication().getService(ConnectHidServiceImpl::class.java)
