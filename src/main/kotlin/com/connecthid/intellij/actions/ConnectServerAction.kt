package com.connecthid.intellij.actions

import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JPasswordField
import javax.swing.JLabel
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import javax.swing.JOptionPane

class ConnectServerAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val connectionService = ServerConnectionService()

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = java.awt.Insets(5, 5, 5, 5)

        val hostField = JTextField(20)
        val usernameField = JTextField(20)
        val passwordField = JPasswordField(20)
        val portField = JTextField(5).apply { text = "22" }

        // Host field
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JLabel("Host:"), gbc)
        gbc.gridx = 1
        panel.add(hostField, gbc)

        // Username field
        gbc.gridx = 0
        gbc.gridy = 1
        panel.add(JLabel("Username:"), gbc)
        gbc.gridx = 1
        panel.add(usernameField, gbc)

        // Password field
        gbc.gridx = 0
        gbc.gridy = 2
        panel.add(JLabel("Password:"), gbc)
        gbc.gridx = 1
        panel.add(passwordField, gbc)

        // Port field
        gbc.gridx = 0
        gbc.gridy = 3
        panel.add(JLabel("Port:"), gbc)
        gbc.gridx = 1
        panel.add(portField, gbc)

        val result = JOptionPane.showConfirmDialog(
            null,
            panel,
            "Connect to Server",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            try {
                val host = hostField.text
                val username = usernameField.text
                val password = String(passwordField.password)
                val port = portField.text.toIntOrNull() ?: 22

                if (host.isBlank() || username.isBlank() || password.isBlank()) {
                    Messages.showErrorDialog(project, "Please fill in all fields", "Connection Error")
                    return
                }

                connectionService.connect(host, username, password, port)
                Messages.showInfoMessage(project, "Successfully connected to $host", "Connection Successful")
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to connect: ${ex.message}", "Connection Error")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
} 