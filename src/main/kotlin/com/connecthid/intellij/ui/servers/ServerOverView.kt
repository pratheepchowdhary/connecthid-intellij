package com.connecthid.intellij.ui.servers

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class ServerOverView {
    private val serverName = JBLabel("Server-Alpha").apply { font = JBUI.Fonts.label(14F) }
    private val ipAddress = JBLabel("192.168.1.100")
    private val region = JBLabel("East Coast")
    private val status = JBLabel("<html><span style='color:green'>●</span> Online</html>")
    private val os = JBLabel("Ubuntu 20.04")
    private val uptime = JBLabel("30 days")

    // Metrics with JProgressBar
    private val cpuValue = JBLabel("65%")
    private val cpuBar = JProgressBar(0, 100).apply {
        value = 65
        foreground = JBColor(0x4CAF50, 0x4CAF50) // Green for CPU
        border = JBUI.Borders.empty()
        isStringPainted = true // Added to show percentage
    }
    private val memoryValue = JBLabel("78%")
    private val memoryBar = JProgressBar(0, 100).apply {
        value = 78
        foreground = JBColor(0x2196F3, 0x2196F3) // Blue
        border = JBUI.Borders.empty()
        isStringPainted = true // Added to show percentage
    }
    private val diskValue = JBLabel("45%")
    private val diskBar = JProgressBar(0, 100).apply {
        value = 45
        foreground = JBColor(0xFF9800, 0xFF9800) // Orange
        border = JBUI.Borders.empty()
        isStringPainted = true // Added to show percentage
    }
    private val networkValue = JBLabel("120 Mbps")
    private val networkBar = JProgressBar(0, 100).apply {
        value = 60
        foreground = JBColor(0x9C27B0, 0x9C27B0) // Purple
        border = JBUI.Borders.empty()
        isStringPainted = true // Added to show percentage
    }

    fun createComponent(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(10)
        }

        // Left: Server Info Panel
        val infoPanel = JPanel(GridBagLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(0, 10)
        }
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2)
            gridwidth = 1
        }

        listOf(
            "Server Name" to serverName,
            "IP Address" to ipAddress,
            "Region" to region,
            "Status" to status,
            "OS" to os,
            "Uptime" to uptime
        ).forEach { (key, value) ->
            infoPanel.add(JBLabel(key).apply { font = JBUI.Fonts.label().deriveFont(Font.BOLD) }, gbc.apply { gridx = 0 })
            gbc.gridx = 1
            infoPanel.add(value, gbc.apply { gridx = 1; weightx = 1.0 })
            gbc.gridy++
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Connect via SSH").apply { addActionListener { handleSSH() } })
            add(JButton("Open Terminal").apply { addActionListener { handleTerminal() } })
        }
        infoPanel.add(buttonPanel, gbc.apply { gridx = 0; gridwidth = 2; fill = GridBagConstraints.HORIZONTAL })

        // Right: Metrics Panels (corrected BoxLayout usage)
        val metricsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS) // Correctly associate with metricsPanel
            border = JBUI.Borders.empty(0, 10)
        }
        listOf(
            createMetricPanel("CPU Usage", cpuValue, cpuBar, JBColor(0x4CAF50, 0x4CAF50)),
            createMetricPanel("Memory Usage", memoryValue, memoryBar, JBColor(0x2196F3, 0x2196F3)),
            createMetricPanel("Disk Usage", diskValue, diskBar, JBColor(0xFF9800, 0xFF9800)),
            createMetricPanel("Network Throughput", networkValue, networkBar, JBColor(0x9C27B0, 0x9C27B0))
        ).forEach { metricsPanel.add(it) }

        mainPanel.add(infoPanel, BorderLayout.WEST)
        mainPanel.add(metricsPanel, BorderLayout.CENTER)

        Timer(5000) { updateMetrics() }.start()

        return mainPanel
    }

    private fun createMetricPanel(title: String, valueLabel: JBLabel, progressBar: JProgressBar, accentColor: JBColor): JPanel {
        return JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.emptyBottom(10)
            add(JBLabel(title).apply { font = JBUI.Fonts.label().deriveFont(Font.BOLD) }, BorderLayout.NORTH)
            val innerPanel = JPanel(BorderLayout()).apply {
                add(valueLabel.apply { horizontalAlignment = SwingConstants.RIGHT; font = JBUI.Fonts.label(16F) }, BorderLayout.EAST)
                progressBar.foreground = accentColor
                add(progressBar, BorderLayout.CENTER)
            }
            add(innerPanel, BorderLayout.SOUTH)
        }
    }

    private fun handleSSH() {
        println("Connecting via SSH to 192.168.1.100")
    }

    private fun handleTerminal() {
        println("Opening terminal")
    }

    private fun updateMetrics() {
        cpuBar.value = (Math.random() * 100).toInt()
        cpuValue.text = "${cpuBar.value}%"
        memoryBar.value = (Math.random() * 100).toInt()
        memoryValue.text = "${memoryBar.value}%"
        diskBar.value = (Math.random() * 100).toInt()
        diskValue.text = "${diskBar.value}%"
        networkBar.value = (Math.random() * 100).toInt()
        networkValue.text = "${networkBar.value} Mbps" // Adjust format if needed
    }
}