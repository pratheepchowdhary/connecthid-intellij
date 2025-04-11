package com.connecthid.intellij

import com.connecthid.intellij.services.ConnectHidServiceImpl
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
class ConnectHIDPlugin : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        // Create main tabbed pane
        val tabbedPane = JBTabbedPane()
        // Add panels to tabs with required services
        tabbedPane.addTab("Servers", ServerListPanel(project))
        tabbedPane.addTab("File Sync", FileSyncPanel(project))
        tabbedPane.addTab("Scripts", ScriptPanel(project))
        // Add content to tool window
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(tabbedPane, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
fun Project.getAppService(): ConnectHidServiceImpl = service
