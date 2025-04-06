package com.connecthid.intellij.ui.servers

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.services.ServerConnection
import com.connecthid.intellij.ui.views.Button
import com.connecthid.intellij.ui.views.IconButton
import com.connecthid.intellij.utils.flowPanel
import com.connecthid.intellij.utils.makeBold
import com.connecthid.intellij.utils.panel
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
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.SwingConstants

class DevicePanel(device: ServerConnection) : JBPanel<DevicePanel>(GridBagLayout()) {

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

        val iconLabel = JBLabel(device.icon)
        add(
            iconLabel,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                gridwidth = 1
                gridheight = 2
                fill = GridBagConstraints.VERTICAL
                weighty = 1.0
                insets = JBUI.insets(0, 10)
            }
        )

        val titleLabel = JBLabel(device.host)
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
                fill = GridBagConstraints.BOTH
                anchor = GridBagConstraints.PAGE_START
                weightx = 1.0
                insets = JBUI.insetsTop(5)
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
                    gridx = 2
                    gridy = 0
                    gridwidth = 2
                    weighty = 1.0
                    insets = JBUI.insets(vInset, 10, vInset, 10)
                }
            )
        } else {
            val button = when (device.buttonType) {
                1 -> Button.connectButton()
                2 -> Button.connectButton(false)
                3-> Button.disconnectButton()
                else -> {Button.disconnectButton()}
            }
            val vInset = (BUTTON_CELL_HEIGHT - button.preferredSize.height) / 2
            button.addActionListener {
                when (device.buttonType) {
                    1, 2 -> listener?.onConnectButtonClicked(device)
                    3 -> listener?.onDisconnectButtonClicked(device)
                }
            }
            button.addMouseListener(hoverListener)
            add(
                button,
                GridBagConstraints().apply {
                    gridx = 2
                    gridy = 0
                    gridwidth = 2
                    weighty = 1.0
                    insets = JBUI.insets(vInset, 10, vInset, 10)
                }
            )
        }

        val actionButtons = arrayListOf<JComponent>()

//        if (device.isShareScreenButtonVisible) {
//            val shareScreenButton = IconButton(Icons.SHARE_SCREEN, PluginBundle.message("shareScreenTooltip"))
//            shareScreenButton.onClickedListener = {
//                listener?.onShareScreenClicked(device)
//                shareScreenButton.showProgressFor(2000)
//            }
//            shareScreenButton.addMouseListener(hoverListener)
//            actionButtons.add(shareScreenButton)
//        }
//
//        if (device.isRemoveButtonVisible) {
//            val removeButton = IconButton(Icons.DELETE, PluginBundle.message("removeDeviceTooltip"))
//            removeButton.onClickedListener = {
//                listener?.onRemoveDeviceClicked(device)
//            }
//            removeButton.addMouseListener(hoverListener)
//            actionButtons.add(removeButton)
//        }

        val menuButton = IconButton(AllIcons.Actions.More)
        menuButton.onClickedListener = { event ->
            openDeviceMenu(device, event)
        }
        menuButton.addMouseListener(hoverListener)
        actionButtons.add(menuButton)

        val actionsPanel = flowPanel(*actionButtons.toTypedArray(), menuButton, hgap = 10)
        actionsPanel.isOpaque = false
        add(
            actionsPanel,
            GridBagConstraints().apply {
                gridx = 3
                gridy = 1
                gridwidth = 1
                gridheight = 1
                anchor = GridBagConstraints.LINE_END
            }
        )

        val subtitleLabel = JBLabel(device.host)
        subtitleLabel.icon = AllIcons.Actions.More
        subtitleLabel.horizontalTextPosition = SwingConstants.LEFT
        subtitleLabel.componentStyle = UIUtil.ComponentStyle.REGULAR
        subtitleLabel.fontColor = UIUtil.FontColor.BRIGHTER
        add(
            panel(top = subtitleLabel),
            GridBagConstraints().apply {
                gridx = 1
                gridy = 1
                gridwidth = 3
                fill = GridBagConstraints.BOTH
                anchor = GridBagConstraints.PAGE_END
                weightx = 1.0
                weighty = 1.0
                insets = JBUI.insetsRight(actionsPanel.preferredSize.width)
            }
        )

        addMouseListener(hoverListener)
        actionsPanel.addMouseListener(hoverListener)
    }

    private fun openDeviceMenu(device: ServerConnection, event: MouseEvent) {
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
        fun onConnectButtonClicked(device: ServerConnection)
        fun onDisconnectButtonClicked(device: ServerConnection)
        fun onOpenConsoleButtonClicked(device: ServerConnection)
        fun onRemoveDeviceClicked(device: ServerConnection)
        fun onEditDeviceClicked(device: ServerConnection)
    }

    private companion object {
        private const val LIST_ITEM_HEIGHT = 71
        private const val BUTTON_CELL_HEIGHT = 32
        private val HOVER_COLOR = JBColor.namedColor(
            "Plugins.lightSelectionBackground",
            JBColor(0xF5F9FF, 0x36393B)
        )
    }
}