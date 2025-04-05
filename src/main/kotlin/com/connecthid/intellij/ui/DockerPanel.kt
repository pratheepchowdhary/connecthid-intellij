package com.connecthid.intellij.ui

import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.services.DockerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

class DockerPanel(
    private val project: Project
) : JPanel() {
    private val dockerService = DockerService(project)
    private val connectionService = ServerConnectionService(project)
    private val hostField = JTextField(20)
    private val refreshButton = JButton("Refresh")
    private val startButton = JButton("Start")
    private val stopButton = JButton("Stop")
    private val removeButton = JButton("Remove")
    private val statusLabel = JLabel("Status: Ready")
    private val tableModel = DefaultTableModel(arrayOf("ID", "Name", "Image", "Status", "Ports"), 0)
    private val containersTable = JTable(tableModel)

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

        // Buttons
        gbc.gridx = 2
        gbc.fill = GridBagConstraints.NONE
        add(refreshButton, gbc)

        gbc.gridx = 3
        add(startButton, gbc)

        gbc.gridx = 4
        add(stopButton, gbc)

        gbc.gridx = 5
        add(removeButton, gbc)

        // Table
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 6
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        add(JScrollPane(containersTable), gbc)

        // Status label
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        add(statusLabel, gbc)

        // Action listeners
        refreshButton.addActionListener {
            refreshContainers()
        }

        startButton.addActionListener {
            val selectedRow = containersTable.selectedRow
            if (selectedRow == -1) {
                Messages.showErrorDialog(project, "Please select a container", "Error")
                return@addActionListener
            }

            val containerId = tableModel.getValueAt(selectedRow, 0) as String
            val host = hostField.text

            if (!connectionService.checkConnection(host)) {
                Messages.showErrorDialog(project, "Cannot connect to host", "Error")
                return@addActionListener
            }

            dockerService.startContainer(host, containerId)
            refreshContainers()
            statusLabel.text = "Status: Container started"
        }

        stopButton.addActionListener {
            val selectedRow = containersTable.selectedRow
            if (selectedRow == -1) {
                Messages.showErrorDialog(project, "Please select a container", "Error")
                return@addActionListener
            }

            val containerId = tableModel.getValueAt(selectedRow, 0) as String
            val host = hostField.text

            if (!connectionService.checkConnection(host)) {
                Messages.showErrorDialog(project, "Cannot connect to host", "Error")
                return@addActionListener
            }

            dockerService.stopContainer(host, containerId)
            refreshContainers()
            statusLabel.text = "Status: Container stopped"
        }

        removeButton.addActionListener {
            val selectedRow = containersTable.selectedRow
            if (selectedRow == -1) {
                Messages.showErrorDialog(project, "Please select a container", "Error")
                return@addActionListener
            }

            val containerId = tableModel.getValueAt(selectedRow, 0) as String
            val host = hostField.text

            if (!connectionService.checkConnection(host)) {
                Messages.showErrorDialog(project, "Cannot connect to host", "Error")
                return@addActionListener
            }

            dockerService.removeContainer(host, containerId)
            refreshContainers()
            statusLabel.text = "Status: Container removed"
        }
    }

    private fun refreshContainers() {
        val host = hostField.text
        if (host.isBlank()) {
            Messages.showErrorDialog(project, "Please enter a host", "Error")
            return
        }

        if (!connectionService.checkConnection(host)) {
            Messages.showErrorDialog(project, "Cannot connect to host", "Error")
            return
        }

        tableModel.rowCount = 0
        val containers = dockerService.listContainers(host)
        containers.forEach { container ->
            tableModel.addRow(arrayOf<Any>(
                container.id,
                container.name,
                container.image,
                container.status,
                container.ports.joinToString(", ")
            ))
        }
        statusLabel.text = "Status: Containers refreshed"
    }
} 