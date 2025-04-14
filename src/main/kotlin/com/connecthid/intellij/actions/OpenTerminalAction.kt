package com.connecthid.intellij.actions

import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.TerminalExecutionConsole
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

class OpenTerminalAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val connectionService = ServerConnectionService()

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = java.awt.Insets(5, 5, 5, 5)

        val hostField = JTextField(20)
        val usernameField = JTextField(20)
        val passwordField = JTextField(20)
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
            "Open Terminal",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            try {
                val host = hostField.text
                val username = usernameField.text
                val password = passwordField.text
                val port = portField.text.toIntOrNull() ?: 22

                if (host.isBlank() || username.isBlank() || password.isBlank()) {
                    Messages.showErrorDialog(project, "Please fill in all fields", "Connection Error")
                    return
                }

                // Create SSH command
                val commandLine = GeneralCommandLine("ssh")
                    .withParameters("-p", port.toString(), "$username@$host")
                    .withEnvironment("SSH_PASSWORD", password)

                // Create process handler
                val processHandler = OSProcessHandler(commandLine)
                processHandler.startNotify()

                // Create terminal console
                val console = TerminalExecutionConsole(project, processHandler)
                console.attachToProcess(processHandler)

                // Show in tool window
                val toolWindowManager = ToolWindowManager.getInstance(project)
                val toolWindow = toolWindowManager.getToolWindow("Terminal")
                toolWindow?.show {
                    toolWindow.contentManager.addContent(
                        com.intellij.ui.content.ContentFactory.getInstance().createContent(
                            console.component,
                            "SSH: $username@$host",
                            false
                        )
                    )
                }

            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to open terminal: ${ex.message}", "Connection Error")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
} 