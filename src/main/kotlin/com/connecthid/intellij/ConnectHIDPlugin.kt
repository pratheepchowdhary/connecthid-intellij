package com.connecthid.intellij

import com.connecthid.intellij.services.ConnectHidServiceImpl
import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.ui.FileSyncPanel
import com.connecthid.intellij.ui.ScriptPanel
import com.connecthid.intellij.ui.servers.ServerListPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
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
        tabbedPane.addTab("Project/Code Syncing", FileSyncPanel(project))
        tabbedPane.addTab("Remote Build & Execution", FileSyncPanel(project))
        tabbedPane.addTab("Quick Commands / Snippets", ScriptPanel(project))
        // Add content to tool window
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(tabbedPane, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
fun Project.getAppService(): ConnectHidServiceImpl = service
fun Project.getSSHService(): ServerConnectionService = sshService


