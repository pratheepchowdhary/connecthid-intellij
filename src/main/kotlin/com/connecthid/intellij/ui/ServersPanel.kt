package com.connecthid.intellij.ui

import com.connecthid.intellij.services.AuthenticationMethod
import com.connecthid.intellij.services.ServerConnection
import com.connecthid.intellij.services.ServerConnectionService
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
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.border.CompoundBorder

class ServersPanel(private val project: Project) : JPanel() {
    private val connectionService = ServerConnectionService(project)
    private val addButton = JButton("New Connection").apply {
        font = font.deriveFont(Font.BOLD)
    }
    private val statusLabel = JLabel("Status: Ready")
    private val serverListModel = DefaultListModel<ServerConnection>()
    private val serverList = JBList(serverListModel).apply {
        cellRenderer = ServerListCellRenderer()
    }

    // Cache for OS icons
    private val osIcons = mutableMapOf<String, ImageIcon>()

    init {
        // Load OS icons
        loadOsIcons()

        layout = BorderLayout(10, 10)
        border = EmptyBorder(10, 10, 10, 10)

        // Top panel with add button aligned to right
        val topPanel = JPanel(BorderLayout()).apply {
            add(statusLabel, BorderLayout.WEST)
            add(addButton, BorderLayout.EAST)
        }
        add(topPanel, BorderLayout.NORTH)

        // Server list in scroll pane
        val scrollPane = JBScrollPane(serverList)
        add(scrollPane, BorderLayout.CENTER)

        // Add mouse listener for list item actions
        serverList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                @Suppress("UNCHECKED_CAST")
                val list = e.source as JBList<ServerConnection>
                val index = list.locationToIndex(e.point)
                if (index >= 0) {
                    val server = list.model.getElementAt(index)
                    val cellBounds = list.getCellBounds(index, index)
                    
                    // Get the renderer component to find button bounds
                    val renderer = (list.cellRenderer as ServerListCellRenderer).apply {
                        getListCellRendererComponent(list, server, index, false, false)
                    }
                    
                    // Calculate button positions relative to cell
                    val moreButtonBounds = renderer.moreButton.bounds
                    val consoleButtonBounds = renderer.consoleButton.bounds
                    
                    // Adjust bounds to cell position
                    moreButtonBounds.translate(cellBounds.width - moreButtonBounds.x - moreButtonBounds.width - 20, cellBounds.y)
                    consoleButtonBounds.translate(cellBounds.width - consoleButtonBounds.x - consoleButtonBounds.width - moreButtonBounds.width - 25, cellBounds.y)
                    
                    when {
                        moreButtonBounds.contains(e.point) -> {
                            val popup = renderer.createPopupMenu(server)
                            popup.show(list, e.x, e.y)
                        }
                        consoleButtonBounds.contains(e.point) -> {
                            // Handle console button click
                            // Implement console opening logic here
                        }
                    }
                }
            }
        })

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

    // Custom cell renderer for server list items
    private inner class ServerListCellRenderer : JPanel(), ListCellRenderer<ServerConnection> {
        private val osIconLabel = JLabel()
        private val nameLabel = JLabel()
        private val specsLabel = JLabel()
        val consoleButton = JButton().apply {
            icon = AllIcons.Actions.Execute
            isOpaque = true
            background = Color(240, 240, 240)
            foreground = Color(23, 92, 230)
            border = JBUI.Borders.empty(6, 12)
            preferredSize = Dimension(32, 32)
            toolTipText = "Open Console"
        }
        val moreButton = JButton().apply {
            icon = AllIcons.Actions.More
            isOpaque = false
            border = JBUI.Borders.empty(6, 12)
            toolTipText = "More Actions"
        }

        fun createPopupMenu(server: ServerConnection): JPopupMenu {
            return JPopupMenu().apply {
                val isConnected = connectionService.isConnected(server.host)
                
                // Connect/Disconnect option
                add(JMenuItem(if (isConnected) "Disconnect" else "Connect").apply {
                    icon = if (isConnected) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
                    addActionListener {
                        if (isConnected) {
                            connectionService.disconnect(server.host)
                            updateServerList()
                            statusLabel.text = "Disconnected from ${server.host}"
                        } else {
                            showConnectDialog(server.host, server.username, server.port, server.authMethod)
                        }
                    }
                })

                // Edit option
                add(JMenuItem("Edit").apply {
                    icon = AllIcons.Actions.Edit
                    addActionListener {
                        showServerDialog(server)
                    }
                })

                // Refresh connection option
                add(JMenuItem("Refresh Connection").apply {
                    icon = AllIcons.Actions.Refresh
                    isEnabled = isConnected
                    addActionListener {
                        // Implement refresh logic here
                        connectionService.disconnect(server.host)
                        showConnectDialog(server.host, server.username, server.port, server.authMethod)
                    }
                })

                // Separator before delete
                addSeparator()

                // Delete option
                add(JMenuItem("Delete").apply {
                    icon = AllIcons.Actions.GC
                    foreground = Color(200, 50, 50) // Red color for delete
                    addActionListener {
                        if (confirmDelete(server)) {
                            connectionService.disconnect(server.host)
                            connectionService.removeServerConnection(server.host)
                            updateServerList()
                            statusLabel.text = "Removed ${server.host}"
                        }
                    }
                })
            }
        }

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

    // Helper class for circular border
    private class CircularBorder(private val size: Int) : Border {
        override fun getBorderInsets(c: Component?): Insets = Insets(2, 2, 2, 2)
        
        override fun isBorderOpaque(): Boolean = true
        
        override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
            if (g == null) return
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = Color(230, 230, 230)
            g2.fillOval(x, y, size, size)
            g2.dispose()
        }
    }

    private fun updateServerList() {
        serverListModel.clear()
        connectionService.getSavedConnections().forEach { server ->
            serverListModel.addElement(server)
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