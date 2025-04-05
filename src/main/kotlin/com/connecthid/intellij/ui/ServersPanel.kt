package com.connecthid.intellij.ui

import com.connecthid.intellij.services.AuthenticationMethod
import com.connecthid.intellij.services.ServerConnection
import com.connecthid.intellij.services.ServerConnectionService
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
import javax.swing.border.EmptyBorder

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

    init {
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
                val list = e.source as JBList<*>
                val index = list.locationToIndex(e.point)
                if (index >= 0) {
                    val server = serverListModel.getElementAt(index) as ServerConnection
                    val cellBounds = list.getCellBounds(index, index)
                    
                    // Check if click is in the buttons area (right side)
                    if (e.x > cellBounds.width - 200) { // Adjust based on button width
                        val isConnected = connectionService.isConnected(server.host)
                        if (e.x > cellBounds.width - 100) { // Edit button
                            showServerDialog(server)
                        } else if (isConnected) { // Disconnect button
                            connectionService.disconnect(server.host)
                            updateServerList()
                            statusLabel.text = "Disconnected from ${server.host}"
                        } else { // Connect button
                            showConnectDialog(server.host, server.username, server.port, server.authMethod)
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

    private fun updateServerList() {
        serverListModel.clear()
        connectionService.getSavedConnections().forEach { server ->
            serverListModel.addElement(server)
        }
        serverList.repaint()
    }

    // Custom cell renderer for server list items
    private inner class ServerListCellRenderer : JPanel(), ListCellRenderer<ServerConnection> {
        private val hostLabel = JLabel()
        private val usernameLabel = JLabel()
        private val statusLabel = JLabel()
        private val connectButton = JButton("Connect")
        private val disconnectButton = JButton("Disconnect")
        private val editButton = JButton("Edit")

        init {
            layout = BorderLayout(10, 0)
            border = JBUI.Borders.empty(10)

            val infoPanel = JPanel(GridLayout(2, 1, 0, 2)).apply {
                add(hostLabel)
                add(usernameLabel)
            }
            add(infoPanel, BorderLayout.CENTER)

            val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
            buttonsPanel.add(connectButton)
            buttonsPanel.add(disconnectButton)
            buttonsPanel.add(editButton)
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
            val isConnected = connectionService.isConnected(server.host)
            
            // Update labels
            hostLabel.text = server.host
            hostLabel.font = hostLabel.font.deriveFont(Font.BOLD)
            usernameLabel.text = "${server.username}@${server.host}:${server.port}"
            
            // Update button visibility
            connectButton.isVisible = !isConnected
            disconnectButton.isVisible = isConnected

            // Update colors based on selection
            if (isSelected) {
                background = list.selectionBackground
                foreground = list.selectionForeground
            } else {
                background = list.background
                foreground = list.foreground
            }

            return this
        }
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