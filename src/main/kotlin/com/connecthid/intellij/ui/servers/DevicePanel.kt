package com.connecthid.intellij.ui.servers

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.utils.makeBold
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JProgressBar


class DevicePanel(val device: Server) : JBPanel<DevicePanel>(GridBagLayout()) {

    var listener: Listener? = null

    private val hoverListener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            background = HOVER_COLOR
        }

        override fun mouseExited(e: MouseEvent) {
            background = JBColor.background()
        }
    }

    init {
        background = JBColor.background()

        minimumSize = Dimension(0, LIST_ITEM_HEIGHT)
        maximumSize = Dimension(Int.MAX_VALUE, LIST_ITEM_HEIGHT)
        preferredSize = Dimension(0, LIST_ITEM_HEIGHT)



        val iconLabel = JBLabel(device.icon).apply {
            preferredSize = Dimension(50, 50) // Set desired size for the icon
        }
        add(
            iconLabel,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                gridwidth = 1
                gridheight = 2
                fill = GridBagConstraints.VERTICAL
                weighty = 1.0
                insets = JBUI.insets(0, 20)
            }
        )

        val titleLabel = JBLabel(device.systemInfo.hostName)
        titleLabel.componentStyle = UIUtil.ComponentStyle.LARGE
        titleLabel.makeBold()
        titleLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    listener?.onEditDeviceClicked(device)
                }
            }
        })
        add(
            titleLabel,
            GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                gridwidth = 1
                gridheight = 2
                fill = GridBagConstraints.BOTH
                anchor = GridBagConstraints.CENTER
                weightx = 1.0
                weighty = 1.0
                insets = JBUI.insetsBottom(20)
            }
        )
        val descriptionLabel = JBLabel(hostDescription())
        descriptionLabel.componentStyle = UIUtil.ComponentStyle.SMALL
        descriptionLabel.addMouseListener(hoverListener)
        add(
            descriptionLabel,
            GridBagConstraints().apply {
                gridx = 1
                gridy = 1
                gridwidth = 1
                gridheight = 2
                fill = GridBagConstraints.BOTH
                anchor = GridBagConstraints.CENTER
                weightx = 1.0
                weighty = 1.0
                insets = JBUI.insetsTop(20)
            }
        )

        // show host right side of the description label and title label and verticaly center

         val hostLabel = JBLabel(device.host)
         hostLabel.componentStyle = UIUtil.ComponentStyle.SMALL
         hostLabel.addMouseListener(hoverListener)
         add(
            hostLabel,
            GridBagConstraints().apply {
                gridx = 2
                gridy = 0
                gridwidth = 1
                gridheight = 2
                fill = GridBagConstraints.VERTICAL
                anchor = GridBagConstraints.CENTER
                weightx = 0.0
                weighty = 1.0
                insets = JBUI.insets(0, 10, 0, 0)
            }
        )   
        


        if (device.isInProgress) {
            val progressBar = JProgressBar()
            progressBar.isIndeterminate = true
            progressBar.preferredSize = Dimension(100, progressBar.preferredSize.height)
            progressBar.addMouseListener(hoverListener)
            val vInset = (BUTTON_CELL_HEIGHT - progressBar.preferredSize.height) / 2
            add(
                progressBar,
                GridBagConstraints().apply {
                    gridx = 3
                    gridy = 0
                    gridwidth = 1
                    gridheight = 2
                    weighty = 1.0
                    insets = JBUI.insets(vInset, 0, vInset, 20)
                    anchor = GridBagConstraints.LINE_END
                }
            )
        } else {
            val consoleButton = JButton().apply {
                icon = AllIcons.Debugger.Console
                isOpaque = false
                border = JBUI.Borders.empty(6, 12)
                preferredSize = Dimension(32, 32)
                toolTipText = "Open Console"
                isFocusable = false
                setContentAreaFilled(false)
            }
            
            consoleButton.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    // open terminal in side intellij ide  with ssh connection
                    listener?.onOpenConsoleButtonClicked(device)
                }
            })
            add(
                consoleButton,
                GridBagConstraints().apply {
                    gridx = 3
                    gridy = 0
                    gridwidth = 1
                    gridheight = 2
                    weightx = 0.0
                    weighty = 1.0
                    insets = JBUI.insets(0, 0, 0, 5)
                    anchor = GridBagConstraints.CENTER
                }
            )
        }

        val moreButton = JButton().apply {
            icon = AllIcons.Actions.More
            isOpaque = false
            border = JBUI.Borders.empty(6, 12)
            preferredSize = Dimension(32, 32)
            toolTipText = "More"
            isFocusable = false
            setContentAreaFilled(false)
        }
        add(
            moreButton,
            GridBagConstraints().apply {
                gridx = 4
                gridy = 0
                gridwidth = 1
                gridheight = 2
                weightx = 0.0
                weighty = 1.0
                insets = JBUI.insets(0, 0, 0, 20)
                anchor = GridBagConstraints.CENTER
            }
        )
        moreButton.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    openDeviceMenu(device, e)
                }
            }
        })



        addMouseListener(hoverListener)
    }

    private fun hostDescription(): String{

        //need this formate  512 MB Memory / 10 GB Disk / SGP1 - Ubuntu 24.10 x64
        return "${device.systemInfo.totalRam} / ${device.systemInfo.totalStorage} - ${device.systemInfo.osName} ${device.systemInfo.osVersion} ${device.systemInfo.architecture}"
    }

    private fun openDeviceMenu(device: Server, event: MouseEvent) {
        val menu = JBPopupMenu()

        val editDevice = JBMenuItem(PluginBundle.message("edit"), AllIcons.Actions.Edit)
        editDevice.addActionListener {
            listener?.onEditDeviceClicked(device)
        }
        menu.add(editDevice)

        val connectDevice = JBMenuItem(PluginBundle.message("connect"), AllIcons.Actions.Copy)
        connectDevice.addActionListener {
            listener?.onConnectButtonClicked(device)
        }
        menu.add(connectDevice)

        val disconnectDeviceItem = JBMenuItem(PluginBundle.message("disconnect"), AllIcons.Actions.Copy)
        disconnectDeviceItem.addActionListener {
            listener?.onDisconnectButtonClicked(device)
        }
        menu.add(disconnectDeviceItem)

        val deleteDeviceItem = JBMenuItem(PluginBundle.message("delete"), AllIcons.Actions.Copy)
        deleteDeviceItem.addActionListener {
            listener?.onDisconnectButtonClicked(device)
        }
        menu.add(disconnectDeviceItem)



        menu.show(event.component, event.x, event.y)
    }

    interface Listener {
        fun onConnectButtonClicked(device: Server)
        fun onDisconnectButtonClicked(device: Server)
        fun onOpenConsoleButtonClicked(device: Server)
        fun onRemoveDeviceClicked(device: Server)
        fun onEditDeviceClicked(device: Server)
    }

    private companion object {
        private const val LIST_ITEM_HEIGHT = 71
        private const val BUTTON_CELL_HEIGHT = 32
        val HOVER_COLOR = JBColor.namedColor(
            "Plugins.lightSelectionBackground",
            JBColor(0xF5F9FF, 0x36393B)
        )
    }
}