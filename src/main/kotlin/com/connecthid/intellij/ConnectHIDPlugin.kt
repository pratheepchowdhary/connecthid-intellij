package com.connecthid.intellij

import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.ui.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBTabbedPane

class ConnectHIDPlugin : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Initialize shared services
        val connectionService = project.getService(ServerConnectionService::class.java)

        // Create main tabbed pane
        val tabbedPane = JBTabbedPane()

        // Add panels to tabs with required services
        tabbedPane.addTab("Servers", ServersPanel(project))
        tabbedPane.addTab("File Sync", FileSyncPanel(project))
        //tabbedPane.addTab("Docker", DockerPanel(project))
       // tabbedPane.addTab("Monitoring", MonitoringPanel(project))
        tabbedPane.addTab("Scripts", ScriptPanel(project))
        //tabbedPane.addTab("Cron", CronPanel(project))
       // tabbedPane.addTab("Database", DatabasePanel(project))
       // tabbedPane.addTab("Deployment", DeploymentPanel(project))

        // Add content to tool window
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(tabbedPane, "", false)
        toolWindow.contentManager.addContent(content)
    }
} 