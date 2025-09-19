package com.connecthid.intellij.ui.rsync

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.ui.filemanager.sftp.SftpFolderPickerDialog
import com.connecthid.intellij.ui.servers.AddServerDialog
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NotNull
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class AddRsyncDialog(val project: Project,): DialogWrapper(true) {
    // Use lazy initialization to defer service access until actually needed
    private val connectionService by lazy { project.getSSHService() }
    val propertyGraph = PropertyGraph()
    private val selectedHost = propertyGraph.property("" ?: "")
    private val localPath = propertyGraph.property("" ?: "")
    private val remotePath = propertyGraph.property("" ?: "")
    val fileDescriptor = FileChooserDescriptor(false, true, false, false, false, false)
    val fileDescriptor1 = FileChooserDescriptor(false, false, false, false, false, false)

    init {
        title = "Add Rsync Task"
        init()
    }
    override fun createCenterPanel(): JComponent {
        val servers = DefaultComboBoxModel(getServersList().toTypedArray())
        val panel = panel {
            row("Host:") {
                comboBox(servers)
                    .applyToComponent {
                        // Add item listener if needed
                        addItemListener {
                            println("Selected: ${selectedItem}")
                        }
                    }
                button("+") {
                    val addServerDialog =  AddServerDialog(project = project)
                    addServerDialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
                        override fun windowClosed(e: java.awt.event.WindowEvent?) {
                           servers.removeAllElements()
                           servers.addAll(getServersList())
                        }
                        override fun windowClosing(e: java.awt.event.WindowEvent?) {

                        }
                    })
                    addServerDialog.show()
                }
            }
            row("Local Path:") {

                textFieldWithBrowseButton(fileChooserDescriptor = fileDescriptor, project) {
                    localPath.set(it.path)
                    return@textFieldWithBrowseButton it.path
                }.bindText(localPath)
            }
            row("Remote Path:") {
                textFieldWithBrowseButton(fileChooserDescriptor = fileDescriptor1,project) {
                    remotePath.set(it.path)
                    return@textFieldWithBrowseButton it.path
                }.bindText(remotePath).applyToComponent {
                    dispose()
                    addActionListener {
                        val picker = SftpFolderPickerDialog(project = project,serverItem = Server())
                        picker.show()
                    }
                }

            }

            row {
                checkBox("Set up SSH keys for ${servers.selectedItem}")
            }
            row {
                text("Set up SSH keys for rsync to connect without a password by copying the public key to the server. This enables secure, automatic syncing.")
            }

        }
        return panel
    }

    override fun createLeftSideActions(): Array<Action> {
        return arrayOf<Action>(object : AbstractAction(PluginBundle.message("testconnection")) {
            override fun actionPerformed(  e: ActionEvent) {
                //show loader while testing connection inside a dialog like circular progress bar inside a dialog
                //testConnection()
            }
        })
    }

    private fun getServersList(): List<String>{
        return project.getSSHService().getSavedConnections().map {
            it.host
        }
    }
}