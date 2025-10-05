package com.connecthid.intellij.ui.servers

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class SSHPortsDashboardPanel {
    private val titleLabel = JBLabel("SSH & Ports Monitor").apply {
        font = JBUI.Fonts.label(14F).deriveFont(Font.BOLD)
        foreground = JBColor.foreground()
    }
    private val statusLabel = JBLabel("Monitoring Active").apply {
        font = JBUI.Fonts.label(12F)
        foreground = JBColor.GREEN
    }
    private val refreshButton = JButton("Refresh").apply {
        addActionListener { handleRefresh() }
    }

    // SSH Connections Table
    private val sshTableModel = DefaultTableModel(
        arrayOf("User", "IP", "Status", "Duration"),
        0
    ).apply {
        addRow(arrayOf("user1", "192.168.1.10", "Connected", "5m 30s"))
        addRow(arrayOf("admin", "10.0.0.5", "Connected", "12m 15s"))
        addRow(arrayOf("guest", "172.16.0.100", "Disconnected", "0s"))
    }
    private val sshTable = JBTable(sshTableModel).apply {
        autoCreateRowSorter = true
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }

    // Open Ports Table
    private val portsTableModel = DefaultTableModel(
        arrayOf("Port", "Service", "Status"),
        0
    ).apply {
        addRow(arrayOf("22", "SSH", "Open"))
        addRow(arrayOf("80", "HTTP", "Open"))
        addRow(arrayOf("443", "HTTPS", "Open"))
        addRow(arrayOf("3306", "MySQL", "Closed"))
    }
    private val portsTable = JBTable(portsTableModel).apply {
        autoCreateRowSorter = true
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }

    fun createComponent(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(10)
        }

        // Top: Title, Status, and Refresh
        val topPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(titleLabel, BorderLayout.NORTH)
            val statusRefreshPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT)).apply {
                add(statusLabel)
                add(refreshButton)
            }
            add(statusRefreshPanel, BorderLayout.SOUTH)
        }
        mainPanel.add(topPanel, BorderLayout.NORTH)

        // Center: Tables Panel
        val centerPanel = JBPanel<JBPanel<*>>(GridLayout(2, 1, 0, 10)).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(5)
            add(JBScrollPane(sshTable).apply { border = JBUI.Borders.empty() })
            add(JBScrollPane(portsTable).apply { border = JBUI.Borders.empty() })
        }
        mainPanel.add(centerPanel, BorderLayout.CENTER)

        Timer(5000) { updateMetrics() }.start()

        return mainPanel
    }

    private fun handleRefresh() {
        println("Refreshing SSH and ports data...")
        updateMetrics() // Simulate refresh
    }

    private fun updateMetrics() {
        val randomStatus = when ((Math.random() * 3).toInt()) {
            0 -> "Monitoring Down" to JBColor.RED
            1 -> "Monitoring Starting" to JBColor.YELLOW
            else -> "Monitoring Active" to JBColor.GREEN
        }
        statusLabel.text = randomStatus.first
        statusLabel.foreground = randomStatus.second

        // Update SSH table (simulated)
        sshTableModel.setRowCount(0)
        sshTableModel.addRow(arrayOf("user1", "192.168.1.10", "Connected", "${(Math.random() * 15).toInt()}m ${(Math.random() * 60).toInt()}s"))
        sshTableModel.addRow(arrayOf("admin", "10.0.0.5", "Connected", "${(Math.random() * 15).toInt()}m ${(Math.random() * 60).toInt()}s"))
        sshTableModel.addRow(arrayOf("guest", "172.16.0.100", if (Math.random() > 0.3) "Connected" else "Disconnected", "0s"))

        // Update Ports table (simulated)
        portsTableModel.setRowCount(0)
        portsTableModel.addRow(arrayOf("22", "SSH", if (Math.random() > 0.1) "Open" else "Closed"))
        portsTableModel.addRow(arrayOf("80", "HTTP", if (Math.random() > 0.2) "Open" else "Closed"))
        portsTableModel.addRow(arrayOf("443", "HTTPS", if (Math.random() > 0.1) "Open" else "Closed"))
        portsTableModel.addRow(arrayOf("3306", "MySQL", if (Math.random() > 0.3) "Open" else "Closed"))
    }
}