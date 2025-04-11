package com.connecthid.intellij.ui

import com.connecthid.intellij.services.ServerConnectionService
import com.connecthid.intellij.services.ScriptService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel
import java.awt.event.ActionEvent
import java.awt.event.ActionListener

class ScriptPanel(private val project: Project) : JPanel() {
    private val scriptService = ScriptService(project)
    lateinit var  connectionService : ServerConnectionService
    private val hostField = JTextField(20)
    private val scriptNameField = JTextField(20)
    private val scriptContentArea = JTextArea(10, 40)
    private val createButton = JButton("Create")
    private val updateButton = JButton("Update")
    private val deleteButton = JButton("Delete")
    private val executeButton = JButton("Execute")
    private val scriptsTable = JTable()
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

        // Script name field
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        add(JLabel("Script Name:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(scriptNameField, gbc)

        // Script content area
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        val scrollPane = JScrollPane(scriptContentArea)
        add(scrollPane, gbc)

        // Action buttons
        gbc.gridy = 3
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        add(createButton, gbc)

        gbc.gridx = 1
        add(updateButton, gbc)

        gbc.gridx = 2
        add(deleteButton, gbc)

        gbc.gridx = 3
        add(executeButton, gbc)

        // Scripts table
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 4
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        val tableScrollPane = JScrollPane(scriptsTable)
        add(tableScrollPane, gbc)

        // Status label
        gbc.gridx = 0
        gbc.gridy = 5
        gbc.gridwidth = 4
        gbc.anchor = GridBagConstraints.CENTER
        add(statusLabel, gbc)

        // Initialize table
        val tableModel = DefaultTableModel(arrayOf("Name", "Created", "Modified"), 0)
        scriptsTable.model = tableModel

        // Add button actions
        createButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                createScript()
            }
        })

        updateButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                updateScript()
            }
        })

        deleteButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                deleteScript()
            }
        })

        executeButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                executeScript()
            }
        })

        // Add table selection listener
        scriptsTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = scriptsTable.selectedRow
                if (selectedRow >= 0) {
                    val scriptName = scriptsTable.getValueAt(selectedRow, 0) as String
                    loadScript(scriptName)
                }
            }
        }

        // Initial refresh
        refreshScripts()
    }

    private fun createScript() {
        try {
            val host = hostField.text
            val name = scriptNameField.text
            val content = scriptContentArea.text

            if (host.isBlank() || name.isBlank() || content.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Creating script..."
            scriptService.createScript(host, name, content)
            refreshScripts()
            statusLabel.text = "Script created"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to create script"
            Messages.showErrorDialog(project, "Failed to create script: ${ex.message}", "Error")
        }
    }

    private fun updateScript() {
        try {
            val host = hostField.text
            val name = scriptNameField.text
            val content = scriptContentArea.text

            if (host.isBlank() || name.isBlank() || content.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Updating script..."
            scriptService.updateScript(host, name, content)
            refreshScripts()
            statusLabel.text = "Script updated"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to update script"
            Messages.showErrorDialog(project, "Failed to update script: ${ex.message}", "Error")
        }
    }

    private fun deleteScript() {
        try {
            val host = hostField.text
            val name = scriptNameField.text

            if (host.isBlank() || name.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Deleting script..."
            scriptService.deleteScript(host, name)
            refreshScripts()
            clearFields()
            statusLabel.text = "Script deleted"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to delete script"
            Messages.showErrorDialog(project, "Failed to delete script: ${ex.message}", "Error")
        }
    }

    private fun executeScript() {
        try {
            val host = hostField.text
            val name = scriptNameField.text

            if (host.isBlank() || name.isBlank()) {
                Messages.showErrorDialog(project, "Please fill in all fields", "Error")
                return
            }

            if (!connectionService.isConnected(host)) {
                Messages.showErrorDialog(project, "Not connected to host: $host", "Error")
                return
            }

            statusLabel.text = "Executing script..."
            val output = scriptService.executeScript(host, name)
            Messages.showInfoMessage(project, "Script output:\n$output", "Script Execution")
            statusLabel.text = "Script executed"
        } catch (ex: Exception) {
            statusLabel.text = "Failed to execute script"
            Messages.showErrorDialog(project, "Failed to execute script: ${ex.message}", "Error")
        }
    }

    private fun loadScript(name: String) {
        try {
            val host = hostField.text
            if (host.isBlank()) {
                return
            }

            if (!connectionService.isConnected(host)) {
                return
            }

            val script = scriptService.getScript(host, name)
            scriptNameField.text = script.name
            scriptContentArea.text = script.content
        } catch (ex: Exception) {
            statusLabel.text = "Failed to load script"
            Messages.showErrorDialog(project, "Failed to load script: ${ex.message}", "Error")
        }
    }

    private fun refreshScripts() {
        try {
            val host = hostField.text
            if (host.isBlank()) {
                return
            }

            if (!connectionService.isConnected(host)) {
                return
            }

            statusLabel.text = "Refreshing scripts..."
            val scripts = scriptService.listScripts(host)
            val tableModel = scriptsTable.model as DefaultTableModel
            tableModel.rowCount = 0

            for (script in scripts) {
                tableModel.addRow(arrayOf(
                    script.name,
                    script.created,
                    script.modified
                ))
            }

            statusLabel.text = "Ready"
        } catch (ex: Exception) {
            statusLabel.text = "Refresh failed"
            Messages.showErrorDialog(project, "Failed to refresh scripts: ${ex.message}", "Error")
        }
    }

    private fun clearFields() {
        scriptNameField.text = ""
        scriptContentArea.text = ""
    }
} 