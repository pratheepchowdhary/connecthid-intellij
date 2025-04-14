package com.connecthid.intellij.ui.servers

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.AuthenticationMethod
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.SystemInfo
import com.connecthid.intellij.ui.dialog.AddServerDialog
import com.connecthid.intellij.utils.removeI
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class ServerListPanel internal constructor(val project: Project): JBPanel<ServerListPanel>(), DevicePanel.Listener  {
    private val connectionService = project.getSSHService()
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
        header?.border = HEADER_BORDER_EXPANDED
        headerLabel?.text = "$title (${devices.size})"


        removeI { child -> child is DevicePanel }
        for (device in devices) {
            val devicePanel = DevicePanel(device)
            devicePanel.listener = this
            add(devicePanel)
        }
        revalidate()
        repaint()
    }
    private fun buildHeader() {
        val header = OpaquePanel(GridBagLayout())
        this.header = header

        headerLabel = JBLabel().apply {
            foreground = HEADER_FOREGROUND_COLOR
        }
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
            val addServerDialog =  AddServerDialog(project = project)
            addServerDialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent?) {
                    updateServerList()
                }
                override fun windowClosing(e: java.awt.event.WindowEvent?) {

                }
            })
            addServerDialog.show()
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
        header.background = HEADER_BACKGROUND_COLOR
        add(header)

    }

    override fun onConnectButtonClicked(device: Server) {
        TODO("Not yet implemented")
    }

    override fun onDisconnectButtonClicked(device: Server) {
        TODO("Not yet implemented")
    }

    override fun onOpenConsoleButtonClicked(device: Server) {
        TODO("Not yet implemented")
    }

    override fun onRemoveDeviceClicked(device: Server) {
        TODO("Not yet implemented")
    }

    override fun onEditDeviceClicked(device: Server) {
        TODO("Not yet implemented")
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
        private val HEADER_BACKGROUND_COLOR = JBColor.namedColor(
            "Plugins.lightSelectionBackground",
            JBColor(0xF5F9FF, 0x36393B)
        )
        private val HEADER_FOREGROUND_COLOR = JBColor(0x787878, 0xBBBBBB)
        private val HEADER_BORDER_EXPANDED = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
        private val title = "Servers"
    }

}