package com.connecthid.intellij

import com.connecthid.intellij.services.ConnectHidServiceImpl
import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.ui.rsync.FileSyncPanel
import com.connecthid.intellij.ui.servers.ServerListPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory


val service by lazy { ApplicationManager.getApplication().getService(ConnectHidServiceImpl::class.java) }
val sshService by lazy { ApplicationManager.getApplication().getService(ServerConnectionService::class.java) }
class ConnectHIDPlugin : ToolWindowFactory {


    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        // Create main tabbed pane
        val tabbedPane = JBTabbedPane()
        // Add panels to tabs with required services
        tabbedPane.addTab("Servers", ServerListPanel(project))
        tabbedPane.addTab("Workspaces", FileSyncPanel(project))
        tabbedPane.addTab("Code Syncing", FileSyncPanel(project))
        // Add content to tool window
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(tabbedPane, "", false)
        toolWindow.contentManager.addContent(content)
        val ex = toolWindow as ToolWindowEx
        ex.stretchWidth(toolWindow.component.preferredSize.width)

    }
}
fun Project.getSSHService(): ServerConnectionService = sshService
