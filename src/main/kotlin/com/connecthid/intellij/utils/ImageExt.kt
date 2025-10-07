package com.connecthid.intellij.utils

import com.intellij.util.ui.JBUI.CurrentTheme.TabbedPane.HOVER_COLOR
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.ImageIcon


object ImageExt{
    val osImages = mutableMapOf<String, ImageIcon>()

    init {
        loadOsIcons()
    }
    private fun loadOsIcons() {
        val iconSize = 48
        try {
            // Load OS icons from resources and make them circular
            listOf("ubuntu", "debian", "fedora", "windows", "linux").forEach { os ->
                val resource = javaClass.getResourceAsStream("/icons/$os.png")
                if (resource != null) {
                    val originalIcon = ImageIO.read(resource)

                    // Create circular icon
                    val circleImage = BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB)
                    val g2 = circleImage.createGraphics()

                    // Enable antialiasing
                   // g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                    // Draw white circle background
                    g2.color = HOVER_COLOR
                    //g2.fillOval(0, 0, iconSize, iconSize)

                    // Scale and draw the icon
                    val scaled = originalIcon.getScaledInstance(iconSize - 16, iconSize - 16, Image.SCALE_SMOOTH)
                    g2.drawImage(scaled, 8, 8, null)

                    g2.dispose()
                    osImages[os] = ImageIcon(circleImage)
                }
            }
        } catch (e: Exception) {
            // Handle icon loading errors silently
        }
    }
}

fun String.toImageIcon(): ImageIcon {
    return ImageExt.osImages[this.lowercase()] ?: ImageIcon()
}
fun String.isWindows(): Boolean {
    return this.lowercase().contains("windows")
}