package com.connecthid.intellij.ui.servers

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class ServerStatusDashboardPanel {
    private val titleLabel = JBLabel("Server Status").apply {
        font = JBUI.Fonts.label(14F).deriveFont(Font.BOLD)
        foreground = JBColor.foreground()
    }
    private val subtitleLabel = JBLabel("Monitor the health and performance of your servers in real-time.").apply {
        foreground = JBColor.GRAY
    }
    private val refreshButton = JButton("Refresh").apply {
        addActionListener { handleRefresh() }
    }

    // Server data model
    private data class Server(val name: String, var online: Boolean, var cpu: Int, var memory: Int, var disk: Int)

    private val servers = mutableListOf(
        Server("Production-Web01", true, 75, 60, 80),
        Server("Staging-DB01", false, 0, 0, 0),
        Server("Dev-App01", true, 45, 30, 50)
    )

    private val tableModel = object : AbstractTableModel() {
        private val columns = arrayOf("Server Name", "Status", "CPU Usage", "Memory Usage", "Disk Usage")

        override fun getRowCount() = servers.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val server = servers[rowIndex]
            return when (columnIndex) {
                0 -> server.name
                1 -> if (server.online) "Online" else "Offline"
                2 -> server.cpu
                3 -> server.memory
                4 -> server.disk
                else -> ""
            }
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                1 -> String::class.java
                2, 3, 4 -> Int::class.java
                else -> super.getColumnClass(columnIndex)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int) = false
    }

    private val serverTable = JBTable(tableModel).apply {
        autoCreateRowSorter = true
        rowHeight = 30
        tableHeader.foreground = JBColor.GRAY
        gridColor = JBColor.PanelBackground

        // Custom renderer for Int columns (CPU, Memory, Disk)
        setDefaultRenderer(Int::class.java, object : TableCellRenderer {
            private val progressBar = JProgressBar(0, 100).apply {
                isStringPainted = true
                foreground = JBColor(0x2196F3, 0x2196F3) // Blue
                background = JBColor.PanelBackground
                isOpaque = true
            }

            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                progressBar.value = (value as? Int) ?: 0
                progressBar.string = "${progressBar.value}%"
                return progressBar
            }
        })

        // Custom renderer for String column (Status)
        setDefaultRenderer(String::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                val online = value == "Online"
                label.text = "• $value"
                label.foreground = if (online) JBColor.GREEN else JBColor.RED
                return label
            }
        })
    }

    fun createComponent(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(10)
        }

        // Top: Title, Subtitle, Refresh
        val topPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(titleLabel, BorderLayout.NORTH)
            val subPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(subtitleLabel, BorderLayout.WEST)
                add(refreshButton, BorderLayout.EAST)
            }
            add(subPanel, BorderLayout.SOUTH)
        }
        mainPanel.add(topPanel, BorderLayout.NORTH)

        // Center: Table
        val scrollPane = JBScrollPane(serverTable).apply {
            border = JBUI.Borders.empty()
        }
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        Timer(5000) { updateMetrics() }.start()

        return mainPanel
    }

    private fun handleRefresh() {
        println("Refreshing server status...")
        updateMetrics()
    }

    private fun updateMetrics() {
        servers.forEachIndexed { index, server ->
            if (server.online) {
                server.cpu = (Math.random() * 100).toInt()
                server.memory = (Math.random() * 100).toInt()
                server.disk = (Math.random() * 100).toInt()
            }
            tableModel.fireTableRowsUpdated(index, index)
        }
    }
}