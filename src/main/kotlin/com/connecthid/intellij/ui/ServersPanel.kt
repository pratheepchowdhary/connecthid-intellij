package com.connecthid.intellij.ui

import com.connecthid.intellij.services.AuthenticationMethod
import com.connecthid.intellij.services.ServerConnection
import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.services.SystemInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder


class ServersPanel(private val project: Project) : JPanel() {
    private val connectionService = ServerConnectionService(project)
    private val addButton = JButton("New Connection").apply {
        font = font.deriveFont(Font.BOLD)
    }
    private val statusLabel = JLabel("Status: Ready")
    private val serverListModel = DefaultListModel<ServerConnection>()
    private val serverList = ServerList()
    private val moreButtonBounds: MutableMap<Int, Rectangle> = HashMap()
    private val consoleButtonBounds: MutableMap<Int, Rectangle?> = HashMap()

    // Cache for OS icons
    private val osIcons = mutableMapOf<String, ImageIcon>()

    init {
        // Load OS icons
        loadOsIcons()

        layout = BorderLayout(10, 10)
        border = JBUI.Borders.empty(10)

        // Initialize the list with custom implementation
        serverList.model = serverListModel

        // Top panel with add button aligned to right
        val topPanel = JPanel(BorderLayout()).apply {
            add(statusLabel, BorderLayout.WEST)
            add(addButton, BorderLayout.EAST)
        }
        add(topPanel, BorderLayout.NORTH)

        // Server list in scroll pane
        val scrollPane = JBScrollPane(serverList)
        add(scrollPane, BorderLayout.CENTER)

        // Add action listener for add button
        addButton.addActionListener {
            showServerDialog()
        }

        // Initial list update
        updateServerList()
    }

    private fun loadOsIcons() {
        val iconSize = 24
        try {
            // Load OS icons from resources and make them circular
            listOf("ubuntu", "debian", "fedora", "windows", "linux").forEach { os ->
                val resource = javaClass.getResourceAsStream("/icons/$os.png")
                if (resource != null) {
                    val originalIcon = ImageIO.read(resource)
                    
                    // Create circular icon
                    val circleImage = BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB)
                    val g2 = circleImage.createGraphics()
                    
                    // Enable antialiasing
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    
                    // Draw white circle background
                    g2.color = Color(245, 245, 245)
                    g2.fillOval(0, 0, iconSize, iconSize)
                    
                    // Scale and draw the icon
                    val scaled = originalIcon.getScaledInstance(iconSize - 8, iconSize - 8, Image.SCALE_SMOOTH)
                    g2.drawImage(scaled, 4, 4, null)
                    
                    g2.dispose()
                    osIcons[os] = ImageIcon(circleImage)
                }
            }
        } catch (e: Exception) {
            // Handle icon loading errors silently
        }
    }

    private fun confirmDelete(server: ServerConnection): Boolean {
        return Messages.showYesNoDialog(
            project,
            "Are you sure you want to remove ${server.host}?",
            "Confirm Server Removal",
            "Remove",
            "Cancel",
            Messages.getQuestionIcon()
        ) == Messages.YES
    }

    private fun getOsIcon(osName: String): ImageIcon {
        val normalizedOs = osName.toLowerCase()
        return when {
            normalizedOs.contains("ubuntu") -> osIcons["ubuntu"]
            normalizedOs.contains("debian") -> osIcons["debian"]
            normalizedOs.contains("fedora") -> osIcons["fedora"]
            normalizedOs.contains("windows") -> osIcons["windows"]
            else -> osIcons["linux"]
        } ?: ImageIcon() // Return empty icon if not found
    }

    // Custom list that handles button clicks
    private inner class ServerList : JBList<ServerConnection>() {
        init {
            cellRenderer = ServerListCellRenderer()
            
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val index = locationToIndex(e.point)
                    if (index >= 0) {
                        val server = model.getElementAt(index)
                        val cellBounds = getCellBounds(index, index)
                        
                        // Calculate relative click position within the cell
                        val relativeX = e.x - cellBounds.x
                        val relativeY = e.y - cellBounds.y
                        
                        // Define button areas (right side of the cell)
                        val buttonWidth = 32
                        val buttonSpacing = 5
                        val rightPadding = 12
                        val topPadding = 12
                        val buttonHeight = 32
                        
                        // Calculate button positions from right edge
                        val moreButtonX = cellBounds.width - buttonWidth - rightPadding
                        val consoleButtonX = moreButtonX - buttonWidth - buttonSpacing
                        
                        // Check if click is in button areas
                        if (relativeY >= topPadding && relativeY <= topPadding + buttonHeight) {
                            if (relativeX >= moreButtonX && relativeX <= moreButtonX + buttonWidth) {
                                // More button clicked
                                createServerPopupMenu(server).show(this@ServerList, e.x, e.y)
                            } else if (relativeX >= consoleButtonX && relativeX <= consoleButtonX + buttonWidth) {
                                // Console button clicked
                                // Handle console button click
                                // Implement console opening logic here
                            }
                        }
                    }
                }
            })
        }
    }

    private fun createServerPopupMenu(server: ServerConnection): JPopupMenu {
        return JPopupMenu().apply {
            val isConnected = connectionService.isConnected(server.host)
            
            // Connect/Disconnect option
            add(createMenuItem(
                if (isConnected) "Disconnect" else "Connect",
                if (isConnected) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
            ) {
                if (isConnected) {
                    connectionService.disconnect(server.host)
                    updateServerList()
                    statusLabel.text = "Disconnected from ${server.host}"
                } else {
                    showConnectDialog(server.host, server.username, server.port, server.authMethod)
                }
            })

            // Edit option
            add(createMenuItem("Edit", AllIcons.Actions.Edit) {
                showServerDialog(server)
            })

            // Refresh connection option
            add(createMenuItem("Refresh Connection", AllIcons.Actions.Refresh, isEnabled = isConnected) {
                connectionService.disconnect(server.host)
                showConnectDialog(server.host, server.username, server.port, server.authMethod)
            })

            // Separator before delete
            addSeparator()

            // Delete option
            add(createMenuItem("Delete", AllIcons.Actions.GC, foreground = Color(200, 50, 50)) {
                if (confirmDelete(server)) {
                    connectionService.disconnect(server.host)
                    connectionService.removeServerConnection(server.host)
                    updateServerList()
                    statusLabel.text = "Removed ${server.host}"
                }
            })
        }
    }

    private fun createMenuItem(
        text: String,
        icon: Icon,
        isEnabled: Boolean = true,
        foreground: Color? = null,
        action: () -> Unit
    ): JMenuItem {
        return JMenuItem(text).apply {
            this.icon = icon
            this.isEnabled = isEnabled
            foreground?.let { this.foreground = it }
            addActionListener { action() }
        }
    }

    // Custom cell renderer for server list items
    private inner class ServerListCellRenderer : JPanel(), ListCellRenderer<ServerConnection> {
        private val osIconLabel = JLabel()
        private val nameLabel = JLabel()
        private val specsLabel = JLabel()
        private val consoleButton = JButton().apply {
            icon = AllIcons.Actions.Execute
            isOpaque = true
            background = Color(240, 240, 240)
            foreground = Color(23, 92, 230)
            border = JBUI.Borders.empty(6, 12)
            preferredSize = Dimension(32, 32)
            toolTipText = "Open Console"
            isFocusable = false
        }
        private val moreButton = JButton().apply {
            icon = AllIcons.Actions.More
            isOpaque = false
            border = JBUI.Borders.empty(6, 12)
            toolTipText = "More Actions"
            isFocusable = false
        }
        
        private var currentServer: ServerConnection? = null

        init {
            layout = BorderLayout(JBUI.scale(10), 0)
            border = CompoundBorder(
                LineBorder(Color(230, 230, 230), 1),
                JBUI.Borders.empty(12)
            )

            // Left panel with icon
            val leftPanel = JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
                isOpaque = false
                add(osIconLabel, BorderLayout.WEST)
            }

            // Center panel with server info
            val infoPanel = JPanel(GridLayout(2, 1, 0, JBUI.scale(2))).apply {
                isOpaque = false
                add(nameLabel)
                add(specsLabel)
            }
            leftPanel.add(infoPanel, BorderLayout.CENTER)

            add(leftPanel, BorderLayout.CENTER)

            // Right panel with buttons
            val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(5), 0)).apply {
                isOpaque = false
                add(consoleButton)
                add(moreButton)
            }
            add(buttonsPanel, BorderLayout.EAST)

            // Make panel opaque for proper rendering
            isOpaque = true
        }

        override fun getListCellRendererComponent(
            list: JList<out ServerConnection>,
            server: ServerConnection,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            // Store the current server
            currentServer = server
            
            // Set OS icon
            osIconLabel.icon = getOsIcon(server.systemInfo.osName)
            
            // Update labels with modern styling
            nameLabel.text = "<html><span style='font-size: 13px;'><b>${server.host}</b></span></html>"
            
            // Format specs line
            val specs = StringBuilder()
            specs.append(server.systemInfo.totalRam)
            specs.append(" / ")
            specs.append(server.systemInfo.totalStorage)
            specs.append(" Disk / ")
            if (server.systemInfo.osName.isNotEmpty()) {
                specs.append(server.systemInfo.osName)
                if (server.systemInfo.osVersion.isNotEmpty()) {
                    specs.append(" ${server.systemInfo.osVersion}")
                }
            }
            specs.append(" x64")
            
            specsLabel.text = "<html><span style='color: #666666; font-size: 12px;'>$specs</span></html>"

            // Update colors based on selection
            if (isSelected) {
                background = Color(245, 245, 245)
            } else {
                background = Color.WHITE
            }

            return this
        }
    }

    private fun updateServerList() {
        serverListModel.clear()
        
        // Add actual connections
        connectionService.getSavedConnections().forEach { server ->
            serverListModel.addElement(server)
        }

        // Add mock data for demonstration
        val mockServers = listOf(
            ServerConnection(
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
                    usedStorage = "456GB"
                )
            ),
            ServerConnection(
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
                    usedStorage = "1.2TB"
                )
            ),
            ServerConnection(
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
                    usedStorage = "2.8TB"
                )
            ),
            ServerConnection(
                host = "windows-server-2022",
                username = "administrator",
                port = 22,
                authMethod = AuthenticationMethod.PASSWORD,
                systemInfo = SystemInfo(
                    osName = "Windows Server",
                    osVersion = "2022",
                    cpuType = "Intel Xeon Gold 6330",
                    totalRam = "256GB",
                    usedRam = "180GB",
                    totalStorage = "8TB",
                    usedStorage = "5.6TB"
                )
            ),
            ServerConnection(
                host = "linux-db-server",
                username = "dbadmin",
                port = 22,
                authMethod = AuthenticationMethod.PRIVATE_KEY,
                systemInfo = SystemInfo(
                    osName = "CentOS",
                    osVersion = "8",
                    cpuType = "AMD EPYC 7763",
                    totalRam = "512GB",
                    usedRam = "384GB",
                    totalStorage = "16TB",
                    usedStorage = "10.4TB"
                )
            )
        )

        // Add mock servers if no real connections exist
        if (serverListModel.isEmpty) {
            mockServers.forEach { server ->
                serverListModel.addElement(server)
            }
        }

        serverList.repaint()
    }

    private fun showServerDialog(server: ServerConnection? = null) {
        val dialog = JDialog()
        dialog.title = "Add Server"
        dialog.modalityType = Dialog.ModalityType.APPLICATION_MODAL
        dialog.preferredSize = Dimension(600, 400)
        
        val mainPanel = JPanel(GridBagLayout())
        mainPanel.border = JBUI.Borders.empty(100)
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(8)
            anchor = GridBagConstraints.WEST
        }

        val hostField = JTextField(30)
        val usernameField = JTextField(30)
        val portField = JTextField(10)
        val authMethodCombo = com.intellij.openapi.ui.ComboBox(AuthenticationMethod.entries.toTypedArray())
        val privateKeyField = JTextField(25)
        val browseButton = JButton("Browse")
        val passwordField = JPasswordField(30)

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.fill = GridBagConstraints.NONE
        mainPanel.add(JLabel("Host:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        mainPanel.add(hostField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        mainPanel.add(JLabel("Username:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        mainPanel.add(usernameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        mainPanel.add(JLabel("Port:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        portField.text = "22"
        mainPanel.add(portField, gbc)

        gbc.gridx = 0
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.NONE
        mainPanel.add(JLabel("Authentication:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        mainPanel.add(authMethodCombo, gbc)

        gbc.gridx = 0
        gbc.gridy = 4
        gbc.fill = GridBagConstraints.NONE
        mainPanel.add(JLabel("Private Key:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        val keyPanel = JPanel(BorderLayout(5, 0))
        keyPanel.add(privateKeyField, BorderLayout.CENTER)
        keyPanel.add(browseButton, BorderLayout.EAST)
        mainPanel.add(keyPanel, gbc)

        gbc.gridx = 0
        gbc.gridy = 5
        gbc.fill = GridBagConstraints.NONE
        mainPanel.add(JLabel("Password:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        mainPanel.add(passwordField, gbc)

        fun updateFields() {
            val isKeyAuth = authMethodCombo.selectedItem == AuthenticationMethod.PRIVATE_KEY
            privateKeyField.isEnabled = isKeyAuth
            browseButton.isEnabled = isKeyAuth
            passwordField.isEnabled = !isKeyAuth
        }

        authMethodCombo.addItemListener {
            updateFields()
        }

        browseButton.addActionListener {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter { file -> file.extension in listOf("pem", "ppk", "rsa") }
            val files = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val virtualFile = files.choose(project).firstOrNull()
            if (virtualFile != null) {
                privateKeyField.text = virtualFile.path
            }
        }

        val buttonPanel = JPanel()
        val okButton = JButton("OK").apply {
            preferredSize = Dimension(100, 30)
        }
        buttonPanel.add(okButton)

        okButton.addActionListener {
            val host = hostField.text
            val username = usernameField.text
            val port = portField.text.toIntOrNull() ?: 22
            val authMethod = authMethodCombo.selectedItem as AuthenticationMethod
            val privateKeyPath = if (authMethod == AuthenticationMethod.PRIVATE_KEY) privateKeyField.text else null
            val password = if (authMethod == AuthenticationMethod.PASSWORD) String(passwordField.password) else null

            if (host.isBlank() || username.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all required fields", "Error")
                return@addActionListener
            }

            if (authMethod == AuthenticationMethod.PRIVATE_KEY && privateKeyPath.isNullOrBlank()) {
                Messages.showErrorDialog(project, "Please select a private key file", "Error")
                return@addActionListener
            }

            if (authMethod == AuthenticationMethod.PASSWORD && password.isNullOrBlank()) {
                Messages.showErrorDialog(project, "Please enter password", "Error")
                return@addActionListener
            }

            if (connectionService.connect(host, username, password, port, privateKeyPath)) {
                statusLabel.text = "Connected to $host"
                updateServerList()
                dialog.dispose()
            } else {
                Messages.showErrorDialog(project, "Failed to connect to $host", "Connection Error")
            }
        }

        // Add panels to dialog
        dialog.contentPane.layout = BorderLayout(0, 10)  // Add vertical gap between components
        dialog.contentPane.add(mainPanel, BorderLayout.CENTER)
        dialog.contentPane.add(buttonPanel, BorderLayout.SOUTH)
        (dialog.contentPane as JComponent).border = JBUI.Borders.emptyBottom(10)  // Add bottom padding

        // Initial field state
        updateFields()

        dialog.pack()
        dialog.minimumSize = dialog.size

        // Center the dialog on screen
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val x = (screenSize.width - dialog.width) / 2
        val y = (screenSize.height - dialog.height) / 2
        dialog.setLocation(x, y)
        
        dialog.isVisible = true
    }

    private fun showConnectDialog(host: String, username: String, port: Int, authMethod: AuthenticationMethod) {
        val dialog = JDialog()
        dialog.title = "Connect to Server"
        dialog.modalityType = Dialog.ModalityType.APPLICATION_MODAL
        dialog.layout = GridBagLayout()
        val gbc = GridBagConstraints()

        val passwordField = JPasswordField(20)
        val privateKeyField = JTextField(20)
        val browseButton = JButton("Browse")

        // Password field
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        dialog.add(JLabel("Password:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        dialog.add(passwordField, gbc)

        // Private Key field
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        dialog.add(JLabel("Private Key:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        val keyPanel = JPanel()
        keyPanel.layout = BoxLayout(keyPanel, BoxLayout.X_AXIS)
        keyPanel.add(privateKeyField)
        keyPanel.add(browseButton)
        dialog.add(keyPanel, gbc)

        // Update visibility based on auth method
        fun updateFields() {
            val isKeyAuth = authMethod == AuthenticationMethod.PRIVATE_KEY
            privateKeyField.isEnabled = isKeyAuth
            browseButton.isEnabled = isKeyAuth
            passwordField.isEnabled = !isKeyAuth
        }

        browseButton.addActionListener {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter { file -> file.extension in listOf("pem", "ppk", "rsa") }
            val files = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val virtualFile = files.choose(project).firstOrNull()
            if (virtualFile != null) {
                privateKeyField.text = virtualFile.path
            }
        }

        val okButton = JButton("Connect")
        okButton.addActionListener {
            val password = if (authMethod == AuthenticationMethod.PASSWORD) String(passwordField.password) else null
            val privateKeyPath = if (authMethod == AuthenticationMethod.PRIVATE_KEY) privateKeyField.text else null

            if (authMethod == AuthenticationMethod.PRIVATE_KEY && privateKeyPath.isNullOrBlank()) {
                Messages.showErrorDialog(project, "Please select a private key file", "Error")
                return@addActionListener
            }

            if (authMethod == AuthenticationMethod.PASSWORD && password.isNullOrBlank()) {
                Messages.showErrorDialog(project, "Please enter password", "Error")
                return@addActionListener
            }

            if (connectionService.connect(host, username, password, port, privateKeyPath)) {
                statusLabel.text = "Connected to $host"
                updateServerList()
                dialog.dispose()
            } else {
                Messages.showErrorDialog(project, "Failed to connect to $host", "Connection Error")
            }
        }

        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        dialog.add(okButton, gbc)

        // Initial field state
        updateFields()

        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }
} 