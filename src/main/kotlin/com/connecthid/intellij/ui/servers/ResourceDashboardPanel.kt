package com.connecthid.intellij.ui.servers

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import kotlin.math.min

class ResourceDashboardPanel {
    private val titleLabel = JBLabel("Resources usage in real-time").apply {
        font = JBUI.Fonts.label(14F).deriveFont(Font.BOLD)
        foreground = JBColor.foreground()
    }

    // Use mutableListOf for CPU usage data to allow modification
    private val cpuData = mutableListOf(20, 35, 50, 70, 45, 30)
    private val timeLabels = listOf("10:00", "10:02", "10:04", "10:06", "10:08", "10:10")

    // Memory usage (64% used)
    private val memoryValue = JBLabel("64%").apply { font = JBUI.Fonts.label(16F) }
    private val memoryUsedLabel = JBLabel("Used").apply { foreground = JBColor.GRAY }

    // Network I/O
    private val incomingLabel = JBLabel("Incoming").apply { foreground = JBColor.GRAY }
    private val incomingValue = JBLabel("120 KB/s")
    private val outgoingLabel = JBLabel("Outgoing").apply { foreground = JBColor.GRAY }
    private val outgoingValue = JBLabel("80 KB/s")
    private val refreshButton = JButton("Refresh Stats").apply {
        addActionListener { handleRefresh() }
    }

    // Disk usage
    private val diskTitle = JBLabel("Disk Usage").apply { font = JBUI.Fonts.label().deriveFont(Font.BOLD) }
    private val partition1Value = JBLabel("37%")
    private val partition1Bar = JProgressBar(0, 100).apply {
        value = 37
        foreground = JBColor(0x2196F3, 0x2196F3) // Blue
        border = JBUI.Borders.empty()
        isStringPainted = true
    }
    private val partition2Value = JBLabel("50%")
    private val partition2Bar = JProgressBar(0, 100).apply {
        value = 50
        foreground = JBColor(0x2196F3, 0x2196F3) // Blue
        border = JBUI.Borders.empty()
        isStringPainted = true
    }

    fun createComponent(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(10)
        }

        // Top: Title
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        // Center: Grid layout for sections
        val centerPanel = JPanel(GridBagLayout()).apply {
            background = JBColor.PanelBackground
        }
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(10, 10, 10, 10)
            fill = GridBagConstraints.BOTH
            weightx = 1.0
            weighty = 1.0
        }

        // CPU Usage Chart
        val cpuPanel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = JBColor(0x2196F3, 0x2196F3) // Blue line
                val width = width - 20
                val height = height - 20
                val xStep = width / (cpuData.size - 1)
                val yScale = height / 100.0 // Assuming max 100% usage

                val points = cpuData.mapIndexed { i, value ->
                    Point(i * xStep + 10, height - (value * yScale).toInt() + 10)
                }
                g2d.drawPolyline(
                    points.map { it.x }.toIntArray(),
                    points.map { it.y }.toIntArray(),
                    points.size
                )

                // Draw time labels
                g2d.color = JBColor.GRAY
                timeLabels.forEachIndexed { i, label ->
                    val x = i * xStep + 10
                    g2d.drawString(label, x - 10, height + 15)
                }
            }
        }.apply {
            preferredSize = Dimension(300, 150)
            border = JBUI.Borders.empty(5)
            add(JBLabel("CPU Usage (Last 10 Minutes)").apply { font = JBUI.Fonts.label().deriveFont(Font.BOLD) })
        }
        gbc.gridx = 0
        gbc.gridy = 0
        centerPanel.add(cpuPanel, gbc)

        // Memory Usage
        val memoryPanel = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val centerX = width / 2
                val centerY = height / 2
                val radius = min(width, height) / 2 - 20
                val usage = memoryValue.text.replace("%", "").toIntOrNull() ?: 64
                g2d.color = JBColor(0x2196F3, 0x2196F3)
                g2d.fillArc(centerX - radius, centerY - radius, 2 * radius, 2 * radius, 90, -usage * 360 / 100)
                g2d.color = JBColor.GRAY
                g2d.fillArc(centerX - radius, centerY - radius, 2 * radius, 2 * radius, 90 - usage * 360 / 100, -(100 - usage) * 360 / 100)
            }
        }.apply {
            preferredSize = Dimension(150, 150)
            border = JBUI.Borders.empty(5)
            add(memoryValue, BorderLayout.CENTER)
            add(memoryUsedLabel, BorderLayout.SOUTH)
            add(JBLabel("Memory Usage").apply { font = JBUI.Fonts.label().deriveFont(Font.BOLD) }, BorderLayout.NORTH)
        }
        gbc.gridx = 1
        centerPanel.add(memoryPanel, gbc)

        // Network I/O
        val networkPanel = JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(5)
            add(JBLabel("Network I/O").apply { font = JBUI.Fonts.label().deriveFont(Font.BOLD) }, BorderLayout.NORTH)
            val statsPanel = JPanel(GridLayout(2, 2, 5, 5)).apply {
                add(incomingLabel)
                add(incomingValue)
                add(outgoingLabel)
                add(outgoingValue)
            }
            add(statsPanel, BorderLayout.CENTER)
            add(refreshButton, BorderLayout.SOUTH)
        }
        gbc.gridx = 1
        gbc.gridy = 1
        centerPanel.add(networkPanel, gbc)

        // Disk Usage
        val diskPanel = JPanel(BorderLayout()).apply {
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(5)
            add(diskTitle, BorderLayout.NORTH)
            val diskStatsPanel = JPanel(GridBagLayout()).apply {
                val diskGbc = GridBagConstraints().apply {
                    insets = JBUI.insets(2, 0, 2, 0)
                    anchor = GridBagConstraints.WEST
                }
                listOf(
                    "/dev/sda2" to Pair(partition1Value, partition1Bar),
                    "/dev/sdc3" to Pair(partition2Value, partition2Bar)
                ).forEachIndexed { i, (partition, pair) ->
                    diskGbc.gridx = 0
                    add(JBLabel(partition), diskGbc)
                    diskGbc.gridx = 1
                    add(pair.first, diskGbc)
                    diskGbc.gridx = 2
                    add(pair.second, diskGbc)
                    diskGbc.gridy = i + 1
                }
            }
            add(diskStatsPanel, BorderLayout.CENTER)
        }
        gbc.gridx = 0
        gbc.gridy = 1
        centerPanel.add(diskPanel, gbc)

        mainPanel.add(centerPanel, BorderLayout.CENTER)

        Timer(5000) { updateMetrics() }.start()

        return mainPanel
    }

    private fun handleRefresh() {
        println("Refreshing stats...")
        // Implement refresh logic here
    }

    private fun updateMetrics() {
        cpuData.indices.forEach { i ->
            cpuData[i] = (Math.random() * 100).toInt() // Now works with mutableListOf
        }
        memoryValue.text = "${(Math.random() * 100).toInt()}%"
        partition1Bar.value = (Math.random() * 100).toInt()
        partition1Value.text = "${partition1Bar.value}%"
        partition2Bar.value = (Math.random() * 100).toInt()
        partition2Value.text = "${partition2Bar.value}%"
        incomingValue.text = "${(Math.random() * 200).toInt()} KB/s"
        outgoingValue.text = "${(Math.random() * 150).toInt()} KB/s"
        // Repaint to update custom panels
        (createComponent() as JBPanel<*>).revalidate()
        (createComponent() as JBPanel<*>).repaint()
    }
}