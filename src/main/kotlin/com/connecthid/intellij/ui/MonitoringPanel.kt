package com.connecthid.intellij.ui

import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.services.ServerMonitoringService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel
import java.util.Timer

class MonitoringPanel(
    private val project: Project
) : JPanel() {
    private val monitoringService = ServerMonitoringService(project)
    private val connectionService = ServerConnectionService(project)
    private val hostField = JTextField(20)
    private val refreshButton = JButton("Refresh")
    private val startButton = JButton("Start Monitoring")
    private val stopButton = JButton("Stop Monitoring")
    private val statusLabel = JLabel("Status: Ready")
    private val tableModel = DefaultTableModel(arrayOf("Metric", "Value", "Unit", "Timestamp"), 0)
    private val metricsTable = JTable(tableModel)
    private var monitoringTimer: Timer? = null
    private var isMonitoring = false

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

        // Table
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 5
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        add(JScrollPane(metricsTable), gbc)

        // Status label
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        add(statusLabel, gbc)

        // Action listeners
        refreshButton.addActionListener {
            refreshMetrics()
        }

        startButton.addActionListener {
            val host = hostField.text
            if (host.isBlank()) {
                Messages.showErrorDialog(project, "Please enter a host", "Error")
                return@addActionListener
            }

            if (!connectionService.checkConnection(host)) {
                Messages.showErrorDialog(project, "Cannot connect to host", "Error")
                return@addActionListener
            }

            monitoringService.startMonitoring(host)
            statusLabel.text = "Status: Monitoring started"
        }

        stopButton.addActionListener {
            monitoringService.stopMonitoring()
            statusLabel.text = "Status: Monitoring stopped"
        }
    }

    private fun refreshMetrics() {
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
        val metrics = monitoringService.getCurrentMetrics(host)
        metrics.forEach { metric ->
            tableModel.addRow(arrayOf<Any>(
                metric.name,
                metric.value,
                metric.unit,
                metric.timestamp
            ))
        }
        statusLabel.text = "Status: Metrics refreshed"
    }
} 