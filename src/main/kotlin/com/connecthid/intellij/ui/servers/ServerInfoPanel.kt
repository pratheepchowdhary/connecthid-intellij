package com.connecthid.intellij.ui.servers

import com.connecthid.intellij.models.Server
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import javax.swing.JPanel

class ServerInfoPanel(val project: Project, val server: Server, val panelId: String) : JPanel(BorderLayout()) ,com.intellij.openapi.Disposable{
    val tabbedPane = JBTabbedPane()
    init {
        tabbedPane.addTab("Overview", ServerOverView().createComponent())
        tabbedPane.addTab("Resources", ResourceDashboardPanel().createComponent())
        tabbedPane.addTab("Process", ProcessDashboardPanel().createComponent())
        tabbedPane.addTab("Users", SSHPortsDashboardPanel().createComponent())
        tabbedPane.addTab("Logs", LogMonitorDashboardPanel().createComponent())
        add(tabbedPane)
    }

    private fun refreshServerInfo() {
        // TODO: Fetch new data from SSH or API
    }

    private fun reconnectServer() {
        // TODO: Handle reconnection
    }

    private fun openTerminal() {
        // TODO: Open terminal for this server
    }

    override fun dispose() {

    }

}