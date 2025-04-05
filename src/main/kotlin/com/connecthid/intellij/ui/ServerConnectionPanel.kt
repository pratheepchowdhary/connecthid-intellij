package com.connecthid.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.connecthid.intellij.services.ServerConnectionService
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class ServerConnectionPanel(private val project: Project) : JPanel() {
    private val connectionService = ServerConnectionService(project)
    private val hostField = JTextField(20)
    private val usernameField = JTextField(20)
    private val passwordField = JPasswordField(20)
    private val portField = JTextField(5)
    private val connectButton = JButton("Connect")
    private val statusLabel = JLabel("Not connected")

    init {
        layout = GridBagLayout()
        border = EmptyBorder(10, 10, 10, 10)
        val gbc = GridBagConstraints()

        // Host field
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        add(JLabel("Host:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(hostField, gbc)

        // Username field
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Username:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(usernameField, gbc)

        // Password field
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Password:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(passwordField, gbc)

        // Port field
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Port:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        portField.text = "22"
        add(portField, gbc)

        // Connect button
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        add(connectButton, gbc)

        // Status label
        gbc.gridx = 0
        gbc.gridy = 5
        gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.CENTER
        add(statusLabel, gbc)

        // Add action listener to connect button
        connectButton.addActionListener {
            try {
                val host = hostField.text
                val username = usernameField.text
                val password = String(passwordField.password)
                val port = portField.text.toIntOrNull() ?: 22

                if (host.isBlank() || username.isBlank() || password.isBlank()) {
                    Messages.showErrorDialog(project, "Please fill in all fields", "Connection Error")
                    return@addActionListener
                }

                connectionService.connect(host, username, password, port)
                statusLabel.text = "Connected to $host"
                connectButton.text = "Disconnect"
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to connect: ${e.message}", "Connection Error")
            }
        }
    }
} 