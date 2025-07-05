package com.connecthid.intellij

import com.connecthid.intellij.services.ConnectHidServiceImpl
import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.ui.rsync.FileSyncPanel
import com.connecthid.intellij.ui.ScriptPanel
import com.connecthid.intellij.ui.servers.ServerListPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

val service by lazy { ApplicationManager.getApplication().getService(ConnectHidServiceImpl::class.java) }
val sshService by lazy { ApplicationManager.getApplication().getService(ServerConnectionService::class.java) }
class ConnectHIDPlugin : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        // Create main tabbed pane
        val tabbedPane = JBTabbedPane()
        // Add panels to tabs with required services
        tabbedPane.addTab("Servers", ServerListPanel(project))
        tabbedPane.addTab("Code Syncing", FileSyncPanel(project))
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
fun Project.addWindow(component: JComponent){
    val manager = ToolWindowManager.getInstance(this)
    if (manager.getToolWindow(component.javaClass.name) == null) {
        val toolWindow = manager.registerToolWindow(
            RegisterToolWindowTask(
                id = component.javaClass.name, anchor = ToolWindowAnchor.RIGHT, canCloseContent = true
            )
        )
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(component, "", false)
        toolWindow.contentManager.addContent(content)
        toolWindow.show()
    }

}
fun Project.remove(component: JComponent){
    val manager = ToolWindowManager.getInstance(this)
    manager.unregisterToolWindow(component.javaClass.name)

}


