package com.connecthid.intellij.ui

import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class FileSyncPanel(
    private val project: Project
) : JPanel() {
//    private val connectionService = ServerConnectionService(project)
    private val hostField = JTextField(20)
    private val localPathField = JTextField(20)
    private val remotePathField = JTextField(20)
    private val syncDirectionCombo = JComboBox(arrayOf("Local to Remote", "Remote to Local"))
    private val browseButton = JButton("Browse")
    private val syncButton = JButton("Sync")
    private val statusLabel = JLabel("Status: Ready")

    init {
        layout = GridBagLayout()
        border = EmptyBorder(10, 10, 10, 10)
        val gbc = GridBagConstraints()

        // Host input
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        add(JLabel("Host:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(hostField, gbc)

        // Local path
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Local Path:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(localPathField, gbc)

        gbc.gridx = 2
        gbc.fill = GridBagConstraints.NONE
        add(browseButton, gbc)

        // Remote path
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Remote Path:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(remotePathField, gbc)

        // Sync direction
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Sync Direction:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(syncDirectionCombo, gbc)

        // Sync button
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(syncButton, gbc)

        // Status label
        gbc.gridy = 5
        add(statusLabel, gbc)

        // Action listeners
        browseButton.addActionListener {
            val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val files = chooser.choose(project)
            if (files.isNotEmpty()) {
                localPathField.text = files[0].path
            }
        }

        syncButton.addActionListener {
            val host = hostField.text
            val localPath = localPathField.text
            val remotePath = remotePathField.text

            if (host.isBlank() || localPath.isBlank() || remotePath.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return@addActionListener
            }

//            if (!connectionService.checkConnection(host)) {
//                Messages.showErrorDialog(project, "Cannot connect to host", "Error")
//                return@addActionListener
//            }

            val direction = syncDirectionCombo.selectedItem as String
            statusLabel.text = "Status: Syncing ${if (direction == "Local to Remote") "to" else "from"} remote..."
            // TODO: Implement file sync logic
            statusLabel.text = "Status: Sync complete"
        }
    }
} 