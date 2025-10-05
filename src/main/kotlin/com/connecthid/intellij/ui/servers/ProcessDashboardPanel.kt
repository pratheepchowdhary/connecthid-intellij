package com.connecthid.intellij.ui.servers

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

class ProcessDashboardPanel {
    private val titleLabel = JBLabel("Processes").apply {
        font = JBUI.Fonts.label(14F).deriveFont(Font.BOLD)
        foreground = JBColor.foreground()
    }
    private val subtitleLabel = JBLabel("Monitor and manage processes running on your server.").apply {
        foreground = JBColor.GRAY
    }
    private val searchField = JTextField("Search by name...").apply {
        preferredSize = Dimension(200, 30)
    }
    private val killProcessButton = JButton("Kill Process").apply {
        background = JBColor.RED
        foreground = JBColor.WHITE
        isVisible = false // Visible only when a row is selected
    }

    // Table data (simulated)
    private val tableModel = DefaultTableModel(arrayOf("PID", "Name", "CPU %", "MEM %", "Uptime"), 0).apply {
        addRow(arrayOf("1234", "ServerProcessA", "15.2", "8.5", "2d 5h 30m"))
        addRow(arrayOf("5678", "DatabaseService", "22.1", "12.3", "1d 8h 15m"))
        addRow(arrayOf("9101", "NetworkManager", "5.8", "3.2", "3d 2h 45m"))
        addRow(arrayOf("1121", "SecurityAgent", "3.5", "2.1", "1d 10h 20m"))
        addRow(arrayOf("3141", "BackupService", "10.7", "6.8", "2d 8h 50m"))
        addRow(arrayOf("5161", "LoggingDaemon", "2.3", "1.5", "3d 5h 10m"))
        addRow(arrayOf("7181", "CacheManager", "8.9", "5.4", "1d 2h 30m"))
        addRow(arrayOf("9202", "MonitoringAgent", "4.1", "2.7", "2d 12h 15m"))
        addRow(arrayOf("2233", "WebServer", "18.5", "10.1", "1d 15h 40m"))
        addRow(arrayOf("4242", "MailService", "7.6", "4.3", "3d 1h 55m"))
    }
    private val processTable = JBTable(tableModel).apply {
        autoCreateRowSorter = true
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        selectionModel.addListSelectionListener {
            killProcessButton.isVisible = selectedRow != -1
        }
    }
    private val cpuUsageBar = JProgressBar(0, 100).apply {
        value = 34
        foreground = JBColor(0x2196F3, 0x2196F3) // Blue
        border = JBUI.Borders.empty()
        isStringPainted = true
    }
    private val memUsageBar = JProgressBar(0, 100).apply {
        value = 5
        foreground = JBColor(0x2196F3, 0x2196F3) // Blue
        border = JBUI.Borders.empty()
        isStringPainted = true
    }

    fun createComponent(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(10)
        }

        // Top: Title and Subtitle
        val topPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(titleLabel, BorderLayout.NORTH)
            add(subtitleLabel, BorderLayout.CENTER)
        }
        mainPanel.add(topPanel, BorderLayout.NORTH)

        // Center: Search and Table
        val centerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(searchField, BorderLayout.NORTH)
            val scrollPane = JBScrollPane(processTable).apply {
                border = JBUI.Borders.empty()
            }
            add(scrollPane, BorderLayout.CENTER)
        }
        mainPanel.add(centerPanel, BorderLayout.CENTER)

        // South: Usage Bars and Kill Button
        val southPanel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            background = JBColor.PanelBackground
            val gbc = GridBagConstraints().apply {
                insets = JBUI.insets(5, 0, 5, 0)
                anchor = GridBagConstraints.WEST
            }
            gbc.gridx = 0
            gbc.gridy = 0
            add(JBLabel("CPU Usage:"), gbc)
            gbc.gridy = 1
            add(JBLabel("Mem Usage:"), gbc)
            gbc.gridx = 1
            gbc.gridy = 0
            add(cpuUsageBar, gbc)
            gbc.gridy = 1
            add(memUsageBar, gbc)
            gbc.gridx = 2
            gbc.gridy = 0
            gbc.gridheight = 2
            add(killProcessButton.apply { border = JBUI.Borders.empty(5, 10) }, gbc)
        }
        mainPanel.add(southPanel, BorderLayout.SOUTH)

        // Add action listener for Kill Process
        killProcessButton.addActionListener {
            val selectedRow = processTable.selectedRow
            if (selectedRow != -1) {
                val pid = tableModel.getValueAt(selectedRow, 0).toString()
                println("Killing process with PID: $pid")
                // Implement kill logic here
                tableModel.removeRow(selectedRow)
            }
        }

        Timer(5000) { updateMetrics() }.start()

        return mainPanel
    }

    private fun updateMetrics() {
        cpuUsageBar.value = (Math.random() * 100).toInt()
        memUsageBar.value = (Math.random() * 100).toInt()
        // Optionally update table data
        // tableModel.addRow(arrayOf("NewPID", "NewProcess", "${Math.random() * 100}", "${Math.random() * 100}", "1d 0h 0m"))
    }
}