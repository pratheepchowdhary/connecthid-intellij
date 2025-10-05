package com.connecthid.intellij.ui.servers

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.text.BadLocationException
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class LogMonitorDashboardPanel {
    private val titleLabel = JBLabel("Logs").apply {
        font = JBUI.Fonts.label(14F).deriveFont(Font.BOLD)
        foreground = JBColor.foreground()
    }
    private val downloadButton = JButton("Download").apply {
        addActionListener { handleDownload() }
    }
    private val tailLogsToggle = JToggleButton("Tail logs").apply {
        isSelected = true
        addActionListener { handleTailToggle(it) }
    }
    private val logPane = JTextPane().apply {
        isEditable = false
        background = JBColor.PanelBackground
        font = Font("Monospaced", Font.PLAIN, 12)
    }
    private val paginationLabel = JBLabel("Page 1 of 280").apply {
        foreground = JBColor.GRAY
    }
    private val prevPageButton = JButton("<").apply {
        addActionListener { handlePrevPage() }
    }
    private val nextPageButton = JButton(">").apply {
        addActionListener { handleNextPage() }
    }

    // Styles for log levels
    private val infoStyle = logPane.addStyle("INFO", null).apply {
        StyleConstants.setForeground(this, JBColor.GREEN)
    }
    private val warnStyle = logPane.addStyle("WARN", null).apply {
        StyleConstants.setForeground(this, JBColor.YELLOW)
    }
    private val errorStyle = logPane.addStyle("ERROR", null).apply {
        StyleConstants.setForeground(this, JBColor.RED)
    }
    private val defaultStyle = logPane.addStyle("DEFAULT", null).apply {
        StyleConstants.setForeground(this, JBColor.foreground())
    }

    // Simulated log data
    private val logs = mutableListOf<Pair<String, String>>().apply {
        add("INFO" to "[2023-02-27 10:00:01] Server listening on port 8080.")
        add("INFO" to "[2023-02-27 10:01:23] Database connection successful.")
        add("WARN" to "[2023-02-27 10:02:45] High memory usage detected.")
        add("ERROR" to "[2023-02-27 10:03:12] Failed to process payment: Card declined.")
        add("INFO" to "[2023-02-27 10:04:56] New user session initiated.")
        add("WARN" to "[2023-02-27 10:05:34] Scheduled backup job started.")
        add("INFO" to "[2023-02-27 10:06:01] Request handled: POST /api/orders.")
        add("WARN" to "[2023-02-27 10:06:15] Disk space is running low: 15% remaining.")
        add("INFO" to "[2023-02-27 10:07:32] Cache cleared successfully.")
    }
    private var currentFilter = "ALL"
    private var currentPage = 1
    private val pageSize = 10
    private val totalPages = (logs.size / pageSize) + if (logs.size % pageSize > 0) 1 else 0

    fun createComponent(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(10)
        }

        // Top: Title, Download, Tail Toggle
        val topPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT)).apply {
            add(titleLabel)
            add(downloadButton)
            add(tailLogsToggle)
        }
        mainPanel.add(topPanel, BorderLayout.NORTH)

        // Center: Filter Tabs and Log Pane
        val tabbedPane = JTabbedPane().apply {
            addTab("INFO [3,456]", null)
            addTab("WARN [1,203]", null)
            addTab("ERROR [88]", null)
            addChangeListener {
                currentFilter = when (selectedIndex) {
                    0 -> "INFO"
                    1 -> "WARN"
                    2 -> "ERROR"
                    else -> "ALL"
                }
                updateLogDisplay()
            }
        }
        val centerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(tabbedPane, BorderLayout.NORTH)
            val scrollPane = JBScrollPane(logPane).apply {
                border = JBUI.Borders.empty()
            }
            add(scrollPane, BorderLayout.CENTER)
        }
        mainPanel.add(centerPanel, BorderLayout.CENTER)

        // Bottom: Pagination
        val bottomPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.CENTER)).apply {
            add(prevPageButton)
            add(paginationLabel)
            add(nextPageButton)
        }
        mainPanel.add(bottomPanel, BorderLayout.SOUTH)

        updateLogDisplay()

        Timer(5000) { if (tailLogsToggle.isSelected) updateLogs() }.start()

        return mainPanel
    }

    private fun handleDownload() {
        println("Downloading logs...")
        // Implement download logic here
    }

    private fun handleTailToggle(e: ActionEvent) {
        val toggle = e.source as JToggleButton
        println("Tail logs: ${toggle.isSelected}")
    }

    private fun handlePrevPage() {
        if (currentPage > 1) {
            currentPage--
            updateLogDisplay()
        }
    }

    private fun handleNextPage() {
        if (currentPage < totalPages) {
            currentPage++
            updateLogDisplay()
        }
    }

    private fun updateLogDisplay() {
        val doc = logPane.styledDocument
        doc.remove(0, doc.length)
        val filteredLogs = if (currentFilter == "ALL") logs else logs.filter { it.first == currentFilter }
        val startIndex = (currentPage - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, filteredLogs.size)
        filteredLogs.subList(startIndex, endIndex).forEach { (level, message) ->
            val style = when (level) {
                "INFO" -> infoStyle
                "WARN" -> warnStyle
                "ERROR" -> errorStyle
                else -> defaultStyle
            }
            appendStyledText(doc, "$level ", style)
            appendStyledText(doc, "$message\n", defaultStyle)
        }
        paginationLabel.text = "Page $currentPage of $totalPages"
    }

    private fun appendStyledText(doc: StyledDocument, text: String, style: Style?) {
        try {
            doc.insertString(doc.length, text, style)
        } catch (e: BadLocationException) {
            e.printStackTrace()
        }
    }

    private fun updateLogs() {
        val newLevel = listOf("INFO", "WARN", "ERROR").random()
        val newMessage = "[${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}] ${generateRandomMessage()}"
        logs.add(newLevel to newMessage)
        updateLogDisplay()
    }

    private fun generateRandomMessage(): String = listOf(
        "Server listening on port 8080.",
        "Database connection successful.",
        "High memory usage detected.",
        "Failed to process payment: Card declined.",
        "New user session initiated.",
        "Scheduled backup job started.",
        "Request handled: POST /api/orders.",
        "Disk space is running low: 15% remaining.",
        "Cache cleared successfully."
    ).random()
}