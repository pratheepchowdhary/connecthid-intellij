package com.connecthid.intellij.ui

import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.services.CronService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel
import java.awt.event.ActionEvent
import java.awt.event.ActionListener

class CronPanel(private val project: Project) : JPanel() {
    private val cronService = CronService(project)
    private val connectionService = ServerConnectionService()
    private val hostField = JTextField(20)
    private val scheduleField = JTextField(20)
    private val commandField = JTextField(40)
    private val createButton = JButton("Create")
    private val updateButton = JButton("Update")
    private val deleteButton = JButton("Delete")
    private val cronTable = JTable()
    private val statusLabel = JLabel("Ready")

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

        // Schedule field
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Schedule:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(scheduleField, gbc)

        // Command field
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Command:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(commandField, gbc)

        // Action buttons
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        val buttonPanel = JPanel()
        buttonPanel.add(createButton)
        buttonPanel.add(updateButton)
        buttonPanel.add(deleteButton)
        add(buttonPanel, gbc)

        // Cron table
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        val scrollPane = JScrollPane(cronTable)
        add(scrollPane, gbc)

        // Status label
        gbc.gridx = 0
        gbc.gridy = 5
        gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.CENTER
        add(statusLabel, gbc)

        // Initialize table
        val tableModel = DefaultTableModel(arrayOf("Schedule", "Command", "Status"), 0)
        cronTable.model = tableModel

        // Add button actions
        createButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                createCronJob()
            }
        })

        updateButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                updateCronJob()
            }
        })

        deleteButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                deleteCronJob()
            }
        })

        // Add table selection listener
        cronTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = cronTable.selectedRow
                if (selectedRow >= 0) {
                    val schedule = cronTable.getValueAt(selectedRow, 0) as String
                    val command = cronTable.getValueAt(selectedRow, 1) as String
                    scheduleField.text = schedule
                    commandField.text = command
                }
            }
        }

        // Initial refresh
        refreshCronJobs()
    }

    private fun createCronJob() {
        try {
            val host = hostField.text
            val schedule = scheduleField.text
            val command = commandField.text

            if (host.isBlank() || schedule.isBlank() || command.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Creating cron job..."
            cronService.createCronJob(host, schedule, command)
            refreshCronJobs()
            clearFields()
            statusLabel.text = "Cron job created"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to create cron job"
            Messages.showErrorDialog(project, "Failed to create cron job: ${ex.message}", "Error")
        }
    }

    private fun updateCronJob() {
        try {
            val host = hostField.text
            val schedule = scheduleField.text
            val command = commandField.text

            if (host.isBlank() || schedule.isBlank() || command.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Updating cron job..."
            cronService.updateCronJob(host, schedule, command)
            refreshCronJobs()
            statusLabel.text = "Cron job updated"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to update cron job"
            Messages.showErrorDialog(project, "Failed to update cron job: ${ex.message}", "Error")
        }
    }

    private fun deleteCronJob() {
        try {
            val host = hostField.text
            val schedule = scheduleField.text

            if (host.isBlank() || schedule.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Deleting cron job..."
            cronService.deleteCronJob(host, schedule)
            refreshCronJobs()
            clearFields()
            statusLabel.text = "Cron job deleted"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to delete cron job"
            Messages.showErrorDialog(project, "Failed to delete cron job: ${ex.message}", "Error")
        }
    }

    private fun refreshCronJobs() {
        try {
            val host = hostField.text
            if (host.isBlank()) {
                return
            }

            if (!connectionService.isConnected(host)) {
                return
            }

            statusLabel.text = "Refreshing cron jobs..."
            val jobs = cronService.listCronJobs(host)
            val tableModel = cronTable.model as DefaultTableModel
            tableModel.rowCount = 0

            for (job in jobs) {
                tableModel.addRow(arrayOf(
                    job.schedule,
                    job.command,
                    job.status
                ))
            }

            statusLabel.text = "Ready"
        } catch (ex: Exception) {
            statusLabel.text = "Refresh failed"
            Messages.showErrorDialog(project, "Failed to refresh cron jobs: ${ex.message}", "Error")
        }
    }

    private fun clearFields() {
        scheduleField.text = ""
        commandField.text = ""
    }
} 