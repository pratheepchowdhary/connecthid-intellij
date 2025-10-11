package com.connecthid.intellij

import com.connecthid.intellij.services.ConnectHIDConfigService
import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.ui.tasks.TasksPanel
import com.connecthid.intellij.ui.servers.ServerListPanel
import com.connecthid.intellij.ui.servers.ServerStatusDashboardPanel
import com.connecthid.intellij.ui.workspaces.WorkSpacesPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory

class ConnectHIDPlugin : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Create main tabbed pane
        val tabbedPane = JBTabbedPane()
        // Add panels to tabs with required services
        tabbedPane.addTab("Servers", ServerListPanel(project))
        tabbedPane.addTab("Workspaces", WorkSpacesPanel(project))
        tabbedPane.addTab("Tasks", TasksPanel(project))
        tabbedPane.addTab("Server Status", ServerStatusDashboardPanel().createComponent())
        // Add content to tool window
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(tabbedPane, "", false)
        toolWindow.contentManager.addContent(content)
        val ex = toolWindow as ToolWindowEx
        ex.stretchWidth(toolWindow.component.preferredSize.width)
    }
}

fun getSSHService(): ServerConnectionService =
    ApplicationManager.getApplication().getService(ServerConnectionService::class.java)

fun Project.getProjectService(): ConnectHIDConfigService {
    return this.getService(ConnectHIDConfigService::class.java)
}

