package com.connecthid.intellij.ui

import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.services.DeploymentService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

class DeploymentPanel(
    private val project: Project
) : JPanel() {
    private val deploymentService = DeploymentService(project)
    private val connectionService = ServerConnectionService(project)
    private val hostField = JTextField(20)
    private val appNameField = JTextField(20)
    private val versionField = JTextField(20)
    private val artifactPathField = JTextField(20)
    private val browseButton = JButton("Browse")
    private val deployButton = JButton("Deploy")
    private val statusLabel = JLabel("Status: Ready")
    private val envTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val envTable = JTable(envTableModel)
    private val addEnvButton = JButton("Add")
    private val removeEnvButton = JButton("Remove")

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

        // App name
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("App Name:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(appNameField, gbc)

        // Version
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Version:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(versionField, gbc)

        // Artifact path
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Artifact Path:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(artifactPathField, gbc)

        gbc.gridx = 2
        gbc.fill = GridBagConstraints.NONE
        add(browseButton, gbc)

        // Environment variables
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 3
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(JLabel("Environment Variables:"), gbc)

        gbc.gridy = 5
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        add(JScrollPane(envTable), gbc)

        // Environment variable buttons
        gbc.gridy = 6
        gbc.fill = GridBagConstraints.NONE
        gbc.weighty = 0.0
        val envButtonPanel = JPanel()
        envButtonPanel.add(addEnvButton)
        envButtonPanel.add(removeEnvButton)
        add(envButtonPanel, gbc)

        // Deploy button
        gbc.gridy = 7
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(deployButton, gbc)

        // Status label
        gbc.gridy = 8
        add(statusLabel, gbc)

        // Action listeners
        browseButton.addActionListener {
            val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val files = chooser.choose(project)
            if (files.isNotEmpty()) {
                artifactPathField.text = files[0].path
            }
        }

        addEnvButton.addActionListener {
            val key = JOptionPane.showInputDialog(this, "Enter environment variable key:")
            if (key != null && key.isNotBlank()) {
                val value = JOptionPane.showInputDialog(this, "Enter environment variable value:")
                if (value != null) {
                    envTableModel.addRow(arrayOf(key, value))
                }
            }
        }

        removeEnvButton.addActionListener {
            val selectedRow = envTable.selectedRow
            if (selectedRow != -1) {
                envTableModel.removeRow(selectedRow)
            }
        }

        deployButton.addActionListener {
            val host = hostField.text
            val appName = appNameField.text
            val version = versionField.text
            val artifactPath = artifactPathField.text

            if (host.isBlank() || appName.isBlank() || version.isBlank() || artifactPath.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return@addActionListener
            }

            if (!connectionService.checkConnection(host)) {
                Messages.showErrorDialog(project, "Cannot connect to host", "Error")
                return@addActionListener
            }

            // Collect environment variables
            val envVars = mutableMapOf<String, String>()
            for (i in 0 until envTableModel.rowCount) {
                val key = envTableModel.getValueAt(i, 0) as String
                val value = envTableModel.getValueAt(i, 1) as String
                envVars[key] = value
            }

            statusLabel.text = "Status: Deploying..."
            try {
                // Create config map with artifact path and environment variables
                val config = mutableMapOf<String, String>()
                config["artifact_path"] = artifactPath
                envVars.forEach { (key, value) ->
                    config[key] = value
                }
                deploymentService.deploy(host, appName, version, config)
                statusLabel.text = "Status: Deployment successful"
            } catch (e: Exception) {
                statusLabel.text = "Status: Deployment failed"
                Messages.showErrorDialog(project, "Failed to deploy: ${e.message}", "Error")
            }
        }
    }
} 