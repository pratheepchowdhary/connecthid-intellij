package com.connecthid.intellij.ui

import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.services.DatabaseService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel
import java.awt.event.ActionEvent
import java.awt.event.ActionListener

class DatabasePanel(private val project: Project) : JPanel() {
    private val databaseService = DatabaseService(project)
    private val connectionService = ServerConnectionService()
    private val hostField = JTextField(20)
    private val dbTypeCombo = JComboBox(arrayOf("MySQL", "PostgreSQL", "MongoDB"))
    private val dbNameField = JTextField(20)
    private val usernameField = JTextField(20)
    private val passwordField = JPasswordField(20)
    private val createButton = JButton("Create")
    private val deleteButton = JButton("Delete")
    private val backupButton = JButton("Backup")
    private val restoreButton = JButton("Restore")
    private val databasesTable = JTable()
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

        // Database type
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Database Type:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(dbTypeCombo, gbc)

        // Database name
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Database Name:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(dbNameField, gbc)

        // Username
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Username:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(usernameField, gbc)

        // Password
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Password:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(passwordField, gbc)

        // Action buttons
        gbc.gridx = 0
        gbc.gridy = 5
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        val buttonPanel = JPanel()
        buttonPanel.add(createButton)
        buttonPanel.add(deleteButton)
        buttonPanel.add(backupButton)
        buttonPanel.add(restoreButton)
        add(buttonPanel, gbc)

        // Databases table
        gbc.gridx = 0
        gbc.gridy = 6
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        val scrollPane = JScrollPane(databasesTable)
        add(scrollPane, gbc)

        // Status label
        gbc.gridx = 0
        gbc.gridy = 7
        gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.CENTER
        add(statusLabel, gbc)

        // Initialize table
        val tableModel = DefaultTableModel(arrayOf("Name", "Type", "Size", "Created"), 0)
        databasesTable.model = tableModel

        // Add button actions
        createButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                createDatabase()
            }
        })

        deleteButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                deleteDatabase()
            }
        })

        backupButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                backupDatabase()
            }
        })

        restoreButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                restoreDatabase()
            }
        })

        // Add table selection listener
        databasesTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = databasesTable.selectedRow
                if (selectedRow >= 0) {
                    val dbName = databasesTable.getValueAt(selectedRow, 0) as String
                    val dbType = databasesTable.getValueAt(selectedRow, 1) as String
                    dbNameField.text = dbName
                    dbTypeCombo.selectedItem = dbType
                }
            }
        }

        // Initial refresh
        refreshDatabases()
    }

    private fun createDatabase() {
        try {
            val host = hostField.text
            val dbType = dbTypeCombo.selectedItem as String
            val dbName = dbNameField.text
            val username = usernameField.text
            val password = String(passwordField.password)

            if (host.isBlank() || dbName.isBlank() || username.isBlank() || password.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Creating database..."
            databaseService.createDatabase(host, dbType, dbName, username, password)
            refreshDatabases()
            clearFields()
            statusLabel.text = "Database created"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to create database"
            Messages.showErrorDialog(project, "Failed to create database: ${ex.message}", "Error")
        }
    }

    private fun deleteDatabase() {
        try {
            val host = hostField.text
            val dbType = dbTypeCombo.selectedItem as String
            val dbName = dbNameField.text

            if (host.isBlank() || dbName.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Deleting database..."
            databaseService.deleteDatabase(host, dbType, dbName)
            refreshDatabases()
            clearFields()
            statusLabel.text = "Database deleted"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to delete database"
            Messages.showErrorDialog(project, "Failed to delete database: ${ex.message}", "Error")
        }
    }

    private fun backupDatabase() {
        try {
            val host = hostField.text
            val dbType = dbTypeCombo.selectedItem as String
            val dbName = dbNameField.text
            val username = usernameField.text
            val password = String(passwordField.password)

            if (host.isBlank() || dbName.isBlank() || username.isBlank() || password.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Backing up database..."
            val backupPath = databaseService.backupDatabase(host, dbType, dbName, username, password)
            statusLabel.text = "Backup completed: $backupPath"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to backup database"
            Messages.showErrorDialog(project, "Failed to backup database: ${ex.message}", "Error")
        }
    }

    private fun restoreDatabase() {
        try {
            val host = hostField.text
            val dbType = dbTypeCombo.selectedItem as String
            val dbName = dbNameField.text
            val username = usernameField.text
            val password = String(passwordField.password)

            if (host.isBlank() || dbName.isBlank() || username.isBlank() || password.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Restoring database..."
            databaseService.restoreDatabase(host, dbType, dbName, username, password)
            refreshDatabases()
            statusLabel.text = "Database restored"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to restore database"
            Messages.showErrorDialog(project, "Failed to restore database: ${ex.message}", "Error")
        }
    }

    private fun refreshDatabases() {
        try {
            val host = hostField.text
            if (host.isBlank()) {
                return
            }

            if (!connectionService.isConnected(host)) {
                return
            }

            statusLabel.text = "Refreshing databases..."
            val databases = databaseService.listDatabases(host)
            val tableModel = databasesTable.model as DefaultTableModel
            tableModel.rowCount = 0

            for (db in databases) {
                tableModel.addRow(arrayOf(
                    db.name,
                    db.type,
                    db.size,
                    db.created
                ))
            }

            statusLabel.text = "Ready"
        } catch (ex: Exception) {
            statusLabel.text = "Refresh failed"
            Messages.showErrorDialog(project, "Failed to refresh databases: ${ex.message}", "Error")
        }
    }

    private fun clearFields() {
        dbNameField.text = ""
        usernameField.text = ""
        passwordField.text = ""
    }
} 