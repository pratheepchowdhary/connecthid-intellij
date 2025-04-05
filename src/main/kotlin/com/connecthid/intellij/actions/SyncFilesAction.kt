package com.connecthid.intellij.actions

import com.connecthid.intellij.services.FileSyncService
import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JLabel
import javax.swing.JButton
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JOptionPane

class SyncFilesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val syncService = FileSyncService(project)
        val connectionService = ServerConnectionService(project)

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = java.awt.Insets(5, 5, 5, 5)

        val hostField = JTextField(20)
        val localPathField = JTextField(20)
        val remotePathField = JTextField(20)
        val browseButton = JButton("Browse...")

        // Host field
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JLabel("Host:"), gbc)
        gbc.gridx = 1
        panel.add(hostField, gbc)

        // Local path field
        gbc.gridx = 0
        gbc.gridy = 1
        panel.add(JLabel("Local Path:"), gbc)
        gbc.gridx = 1
        panel.add(localPathField, gbc)
        gbc.gridx = 2
        panel.add(browseButton, gbc)

        // Remote path field
        gbc.gridx = 0
        gbc.gridy = 2
        panel.add(JLabel("Remote Path:"), gbc)
        gbc.gridx = 1
        gbc.gridwidth = 2
        panel.add(remotePathField, gbc)

        // Add browse button action
        browseButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
                val file = FileChooser.chooseFile(descriptor, project, null)
                if (file != null) {
                    localPathField.text = file.path
                }
            }
        })

        val result = JOptionPane.showConfirmDialog(
            null,
            panel,
            "Sync Files",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            try {
                val host = hostField.text
                val localPath = localPathField.text
                val remotePath = remotePathField.text

                if (host.isBlank() || localPath.isBlank() || remotePath.isBlank()) {
                    Messages.showErrorDialog(project, "Please fill in all fields", "Sync Error")
                    return
                }

                if (!connectionService.isConnected(host)) {
                    Messages.showErrorDialog(project, "Not connected to host: $host", "Sync Error")
                    return
                }

                // Show sync direction dialog
                val options = arrayOf("To Remote", "From Remote", "Cancel")
                val syncDirection = JOptionPane.showOptionDialog(
                    null,
                    "Choose sync direction",
                    "Sync Direction",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
                )

                when (syncDirection) {
                    0 -> syncService.syncToRemote(host, localPath, remotePath)
                    1 -> syncService.syncFromRemote(host, remotePath, localPath)
                    else -> return
                }

                Messages.showInfoMessage(project, "File sync completed successfully", "Sync Complete")
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to sync files: ${ex.message}", "Sync Error")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
} 