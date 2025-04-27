package com.connecthid.intellij.ui.commons



import javax.swing.*
import javax.swing.plaf.basic.BasicProgressBarUI
import java.awt.*
import kotlin.math.cos
import kotlin.math.sin

// Custom UI for circular progress bar
class ProgressCircleUI : BasicProgressBarUI() {

    private val lineWidth = 10f  // Width of the circular progress bar line

    // Override paint method to draw circular progress
    override fun paint(g: Graphics, c: JComponent) {
        val progressBar = c as JProgressBar
        val width = progressBar.width
        val height = progressBar.height

        // Create graphics object with anti-aliasing for smooth rendering
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Set the color for the background circle
        g2d.color = Color.LIGHT_GRAY
        g2d.stroke = BasicStroke(lineWidth)

        // Draw the background circle
        g2d.drawOval(lineWidth.toInt(), lineWidth.toInt(), width - (2 * lineWidth.toInt()), height - (2 * lineWidth.toInt()))

        // Set the color for the progress circle
        g2d.color = Color.BLUE  // You can change this color to suit your design

        // Calculate the angle of the progress arc based on the progress
        val angle = 360 * progressBar.percentComplete

        // Draw the progress arc (foreground)
        g2d.stroke = BasicStroke(lineWidth)
        // The angle is drawn counter-clockwise (start at 90 degrees, then subtract the angle)
        g2d.drawArc(
            lineWidth.toInt(), lineWidth.toInt(),
            width - (2 * lineWidth.toInt()), height - (2 * lineWidth.toInt()),
            90, -angle.toInt()  // Start at 90 degrees and draw the arc counterclockwise
        )
    }
}

// Custom circular progress bar class
class CircularProgressBar : JProgressBar() {

    init {
        // Set indeterminate mode for spinning effect
        isIndeterminate = true
        // Set custom UI for circular progress
        ui = ProgressCircleUI()
        preferredSize = Dimension(150, 150)  // Set the size for the progress bar
    }
}


