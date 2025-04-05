package com.connecthid.intellij.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel
import javax.swing.JTabbedPane
import com.connecthid.intellij.ui.ServerConnectionPanel
import com.connecthid.intellij.ui.FileSyncPanel
import com.connecthid.intellij.ui.DockerPanel
import com.connecthid.intellij.ui.MonitoringPanel
import com.connecthid.intellij.ui.ScriptPanel
import com.connecthid.intellij.ui.CronPanel
import com.connecthid.intellij.ui.DatabasePanel
import com.connecthid.intellij.ui.DeploymentPanel

class ASOPToolsToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val mainPanel = JPanel()
        val tabbedPane = JTabbedPane()

        // Add all feature panels
        tabbedPane.addTab("Server Connection", ServerConnectionPanel(project))
        tabbedPane.addTab("File Sync", FileSyncPanel(project))
        tabbedPane.addTab("Docker", DockerPanel(project))
        tabbedPane.addTab("Monitoring", MonitoringPanel(project))
        tabbedPane.addTab("Scripts", ScriptPanel(project))
        tabbedPane.addTab("Cron Jobs", CronPanel(project))
        tabbedPane.addTab("Database", DatabasePanel(project))
        tabbedPane.addTab("Deployment", DeploymentPanel(project))

        mainPanel.add(tabbedPane)
        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
} 