package com.connecthid.intellij.ui.rsync

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.ui.servers.ServerItem
import com.connecthid.intellij.utils.removeI
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class FileSyncPanel(
    private val project: Project
) : JPanel() {
    // Use lazy initialization to defer service access until actually needed
    private val connectionService by lazy { project.getSSHService() }
    var devices: MutableList<Server> = emptyList<Server>().toMutableList()
        set(value) {
            field = value
            rebuildUi()
        }
    private var header: JPanel? = null
    private var headerLabel: JBLabel? = null
    private var newFolderButton: JButton? = null

    init {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
        background = JBColor.background()
        buildHeader()
        rebuildUi()
        updateFoldersList()
    }

    private fun rebuildUi() {
        headerLabel?.text = "$title (${devices.size})"


        removeI { child -> child is ServerItem }
        for (device in devices) {
            val devicePanel = ServerItem(device,connectionService)
            add(devicePanel)
        }
        revalidate()
        repaint()
    }
    private fun buildHeader() {
        val header = OpaquePanel(GridBagLayout())
        this.header = header

        headerLabel = JBLabel()
        header.add(
            headerLabel!!,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsLeft(10)
            }
        )

        newFolderButton = JButton(addButton).apply {
            icon = AllIcons.General.Add
        }
        newFolderButton!!.addActionListener {
            val addRsyncDialog =  AddRsyncDialog(project = project)
            addRsyncDialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent?) {
                    updateFoldersList()
                }
                override fun windowClosing(e: java.awt.event.WindowEvent?) {

                }
            })
            addRsyncDialog.show()
        }


        header.add(
            newFolderButton!!,
            GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                insets = JBUI.insetsRight(10)
            }
        )

        header.minimumSize = Dimension(0, HEADER_HEIGHT)
        header.maximumSize = Dimension(Int.MAX_VALUE, HEADER_HEIGHT)
        header.preferredSize = Dimension(0, HEADER_HEIGHT)
        add(header)

    }


    private fun updateFoldersList() {
        var  devices: MutableList<Server> = connectionService.getSavedConnections().toMutableList()
        this.devices = devices
    }



    private companion object {
        private const val HEADER_HEIGHT = 50
        private val title = "My Rsync List"
        private val addButton = "Create Rsync Mapping"
    }

}