package com.connecthid.intellij.ui.servers

import com.connecthid.intellij.connection.terminal.openTerminal
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.AuthenticationMethod
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.SystemInfo
import com.connecthid.intellij.models.getPassword
import com.connecthid.intellij.ui.filemanager.sftp.openSFTP
import com.connecthid.intellij.utils.removeI
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.function.Supplier
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class ServerListPanel internal constructor(val project: Project): JBPanel<ServerListPanel>(), ServerItem.Listener  {
    // Use lazy initialization to defer service access until actually needed
    private val connectionService by lazy { getSSHService() }
    var devices: MutableList<Server> = emptyList<Server>().toMutableList()
        set(value) {
            field = value
            rebuildUi()
        }
    private var header: JPanel? = null
    private var headerLabel: JBLabel? = null
    private var newConnectionButton: JButton? = null

    init {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
        background = JBColor.background()
        buildHeader()
        rebuildUi()
        updateServerList()
    }

    private fun rebuildUi() {
        headerLabel?.text = "$title (${devices.size})"


        removeI { child -> child is ServerItem }
        for (device in devices) {
            val devicePanel = ServerItem(device,connectionService)
            devicePanel.listener = this
            add(devicePanel)
        }
        revalidate()
        repaint()
    }
    private fun buildHeader() {
        val header = OpaquePanel(GridBagLayout())
        this.header = header

        headerLabel = JBLabel()
        header.add(
            headerLabel!!,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsLeft(10)
            }
        )

        newConnectionButton = JButton("New Connection").apply {
           icon = AllIcons.General.Add
        }
        newConnectionButton!!.addActionListener {
            addServer()
        }


        header.add(
            newConnectionButton!!,
            GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                insets = JBUI.insetsRight(10)
            }
        )

        header.minimumSize = Dimension(0, HEADER_HEIGHT)
        header.maximumSize = Dimension(Int.MAX_VALUE, HEADER_HEIGHT)
        header.preferredSize = Dimension(0, HEADER_HEIGHT)
        add(header)

    }
    private fun addServer(server: Server?=null){
        val addServerDialog = if (server == null) AddServerDialog(project = project) else AddServerDialog(
            project = project,
            host = server.host,
            username = server.username,
            port = server.port,
            password = server.getPassword(),
            privateKeyPath = server.privateKeyPath
        )
        addServerDialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                updateServerList()
            }
            override fun windowClosing(e: java.awt.event.WindowEvent?) {

            }
        })
        addServerDialog.show()
    }

    override fun onConnectButtonClicked(server: Server) {
        connectionService.connect(server.host,server.username,server.getPassword(),server.port,server.privateKeyPath)
    }

    override fun onDisconnectButtonClicked(server: Server) {
        connectionService.disconnect(server.host,server.username)
    }

    override fun onOpenConsoleButtonClicked(server: Server) {
        // open terminal in side intellij ide  with ssh connection using TerminalWidget
        project.openTerminal(server = server)
        
    }

    override fun onRemoveDeviceClicked(server: Server) {
        connectionService.removeServerConnection(server.host,server.username)
        devices.remove(server)
        rebuildUi()
    }

    override fun onEditDeviceClicked(server: Server) {
        addServer(server)
    }

    override fun onOpenSFTPClicked(device: Server) {
        project.openSFTP(device)
    }

    override fun onServerClicked(server: Server) {
        project.openServerInfo(server)
    }

    private fun updateServerList() {

        var  devices: MutableList<Server> = connectionService.getSavedConnections().toMutableList()


        // Add mock data for demonstration
        val mockServers = listOf(
            Server(
                host = "ubuntu-server-01",
                username = "admin",
                port = 22,
                authMethod = AuthenticationMethod.PRIVATE_KEY,
                systemInfo = SystemInfo(
                    osName = "Ubuntu",
                    osVersion = "22.04 LTS",
                    cpuType = "AMD Ryzen 9 5950X",
                    totalRam = "32GB",
                    usedRam = "12GB",
                    totalStorage = "1TB",
                    usedStorage = "456GB",
                    hostName = "ubuntu-s-1vcpu-512mb-10gb-sgp1-01"
                )
            ),
            Server(
                host = "debian-web-server",
                username = "webadmin",
                port = 22,
                authMethod = AuthenticationMethod.PASSWORD,
                systemInfo = SystemInfo(
                    osName = "Debian",
                    osVersion = "11",
                    cpuType = "Intel Xeon E5-2680",
                    totalRam = "64GB",
                    usedRam = "48GB",
                    totalStorage = "2TB",
                    usedStorage = "1.2TB",
                    hostName = "debian-s-1vcpu-2gb-20gb-fra1-01"
                )
            ),
            Server(
                host = "fedora-dev-01",
                username = "developer",
                port = 22,
                authMethod = AuthenticationMethod.PRIVATE_KEY,
                systemInfo = SystemInfo(
                    osName = "Fedora",
                    osVersion = "39",
                    cpuType = "Intel i9-13900K",
                    totalRam = "128GB",
                    usedRam = "64GB",
                    totalStorage = "4TB",
                    usedStorage = "2.8TB",
                    hostName = "fedora-s-2vcpu-16gb-40gb-fra1-01"
                )
            ),
            Server(
                host = "windows-server-2022",
                username = "administrator",
                port = 22,
                authMethod = AuthenticationMethod.PASSWORD,
                systemInfo = SystemInfo(
                    osName = "Windows",
                    osVersion = "2022",
                    cpuType = "Intel Xeon Gold 6330",
                    totalRam = "256GB",
                    usedRam = "180GB",
                    totalStorage = "8TB",
                    usedStorage = "5.6TB",
                    hostName = "windows-s-2vcpu-16gb-40gb-fra1-01"
                )
            ),
            Server(
                host = "linux-db-server",
                username = "dbadmin",
                port = 22,
                authMethod = AuthenticationMethod.PRIVATE_KEY,
                systemInfo = SystemInfo(
                    osName = "Linux",
                    osVersion = "8",
                    cpuType = "AMD EPYC 7763",
                    totalRam = "512GB",
                    usedRam = "384GB",
                    totalStorage = "16TB",
                    usedStorage = "10.4TB",
                    hostName = "linux-s-4vcpu-16gb-100gb-fra1-01"
                )
            )
        )

        // Add mock servers if no real connections exist
        if (devices.isEmpty()) {
            //devices.addAll(mockServers)
        }
        this.devices = devices
    }



    private companion object {
        private const val HEADER_HEIGHT = 50
        private val title = "Servers"
    }

}


fun Project.closeServerInfo(panel: ServerInfoPanel) {
    val manager = ToolWindowManager.getInstance(this)
    val toolWindow = manager.getToolWindow(panel.panelId)
    toolWindow?.let { window ->
        // Find and remove all contents
        val existingPanel = toolWindow.contentManager.contents.firstOrNull { content ->
            (content.component as? ServerInfoPanel)?.server?.host == panel.server.host &&
                    (content.component as? ServerInfoPanel)?.server?.username == panel.server.username
        }
        if (existingPanel != null) {
            toolWindow.contentManager.removeContent(existingPanel, true)
        }
        if (window.contentManager.contentCount == 0) {
            window.hide()
        }
    }
}

fun Project.openServerInfo(server: Server)  {

    val manager = ToolWindowManager.getInstance(this)
    val panelId = "ServerInfo"+server.stmpName
    val toolWindow = manager.getToolWindow(panelId)
    if (toolWindow == null) {
        val window = manager.registerToolWindow(panelId) {
            icon = AllIcons.Webreferences.Server
            canCloseContent = false
            anchor = ToolWindowAnchor.RIGHT
            stripeTitle = Supplier{server.stmpName}
        }
        val severPanel = ServerInfoPanel(this, server,panelId)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(severPanel, "", true).apply {
            isCloseable = false
            setDisposer(severPanel)  // Ensure proper cleanup
        }
        window.setTitleActions(listOf(object : AnAction({ "Stop" }, AllIcons.Actions.Suspend) {
            override fun actionPerformed(e: AnActionEvent) {
                severPanel.project.closeServerInfo(severPanel)
            }
        }))
        window.contentManager.addContent(content)
        window.activate { window.show() }
    } else {
        // Check if panel for this server already exists
        val existingPanel = toolWindow.contentManager.contents.firstOrNull { content ->
            (content.component as? ServerInfoPanel)?.server?.host == server.host &&
                    (content.component as? ServerInfoPanel)?.server?.username == server.username
        }

        if (existingPanel != null) {
            toolWindow.activate { toolWindow.show() }
        } else {
            val severPanel = ServerInfoPanel(this, server, panelId)
            val contentFactory = ContentFactory.getInstance()
            val content = contentFactory.createContent(severPanel, "", true).apply {
                isCloseable = false
                setDisposer(severPanel)  // Ensure proper cleanup
            }
            toolWindow.setTitleActions(listOf(object : AnAction({ "Stop" }, AllIcons.Actions.Suspend) {
                override fun actionPerformed(e: AnActionEvent) {
                    severPanel.project.closeServerInfo(severPanel)
                }
            }))
            toolWindow.contentManager.addContent(content)
            toolWindow.activate { toolWindow.show() }
        }
    }
    }