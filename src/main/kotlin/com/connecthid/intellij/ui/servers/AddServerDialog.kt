package com.connecthid.intellij.ui.servers

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.AuthenticationMethod
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.SystemInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JRadioButton
import javax.swing.JTextField

@ApiStatus.Experimental
open class AddServerDialog(val project: Project, val host: String? = null, val username: String = "root", password: String? = null, port: Int = 22, privateKeyPath: String? = null) : DialogWrapper(true) {
    val propertyGraph = PropertyGraph()
    private val selectedHost = propertyGraph.property(host ?: "")
    private val selectedUsername = propertyGraph.property(username)
    private val selectedPort = propertyGraph.property(port.toString())
    private val selectedPassword = propertyGraph.property(password ?: "")
    private val selectedPrivateKeyPath = propertyGraph.property(privateKeyPath ?: "")
    private var selectedMethod: String = if(privateKeyPath != null) "PRIVATE_KEY" else "PASSWORD"
    private lateinit var jbPasswordField: JBPasswordField
    private lateinit var jbPrivateKeyField: JTextField
    private lateinit var passwordRadioButton: JRadioButton
    private lateinit var privateKeyRadioButton: JRadioButton
    private lateinit var loaderLabel: JLabel
    val fileDescriptor = FileChooserDescriptor(true, false, false, false, false, false)
        .withFileFilter { file -> file.extension in listOf("pem", "ppk", "rsa") }

    // Use lazy initialization to defer service access until actually needed
    val sshConnection by lazy { project.getSSHService() }

    init {
        title = "Add Server"
        init()
    }

    private fun updateFields() {

        jbPasswordField.isEnabled = passwordRadioButton.isSelected
        jbPrivateKeyField.isEnabled = privateKeyRadioButton.isSelected
        selectedMethod = if(passwordRadioButton.isSelected) "PASSWORD" else "PRIVATE_KEY"
    }

    override fun createCenterPanel(): DialogPanel {
        val panel = panel {
            row("Host:") {
                textField()
                    .bindText(selectedHost)
            }
            row("Username:") {
                textField()
                    .bindText(selectedUsername)
            }
            row("Port:") {
                textField()
                    .bindText(selectedPort)
            }

            buttonsGroup {
                row("Authentication:") {
                    radioButton("Password", "PASSWORD")
                        .also { passwordRadioButton = it.component }
                    radioButton("Private key", "PRIVATE_KEY")
                        .also { privateKeyRadioButton = it.component }
                }
            }.bind({ selectedMethod }, { selectedMethod = it })

            row("Password:") {
                passwordField()
                    .bindText(selectedPassword)
                    .enabledIf(passwordRadioButton.selected)
                    .apply { jbPasswordField = component }
            }

            row("Private Key:") {

                textFieldWithBrowseButton(fileChooserDescriptor = fileDescriptor, project) {
                    selectedPrivateKeyPath.set(it.path)
                    return@textFieldWithBrowseButton it.path
                }.bindText(selectedPrivateKeyPath)
                    .enabledIf(privateKeyRadioButton.selected)
                    .apply { jbPrivateKeyField = component.textField }
            }
            row {
                label("").also {
                    loaderLabel = it.component
                    loaderLabel.isVisible = false
                }


            }

        }.apply {
            preferredSize = Dimension(400, 200)
            maximumSize = Dimension(400, 200)
            border = JBUI.Borders.empty(10)
        }
        passwordRadioButton.addChangeListener {
            updateFields()
        }
        passwordRadioButton.addChangeListener {
            updateFields()
        }


        return panel
    }
    override fun createLeftSideActions(): Array<Action> {
        return arrayOf<Action>(object : AbstractAction(PluginBundle.message("testconnection")) {
            override fun actionPerformed(  e: ActionEvent) {
                //show loader while testing connection inside a dialog like circular progress bar inside a dialog
                testConnection()
            }
        })
    }
    private fun testConnection() {
        val result = runWithModalProgressBlocking(project = project, title = "Testing Connection") {
            return@runWithModalProgressBlocking sshConnection.isValidSshConnection(
                host = selectedHost.get(),
                username = selectedUsername.get(),
                port = selectedPort.get().toInt(),
                password = if (selectedMethod == "PASSWORD") selectedPassword.get() else null,
                privateKeyPath = if (selectedMethod == "PRIVATE_KEY") selectedPrivateKeyPath.get() else null
            )
        }
        if (result) {
            loaderLabel.isVisible = true
            loaderLabel.text = "Connection Successful"
            loaderLabel.icon = AllIcons.General.Information
        } else {
            loaderLabel.isVisible = true
            loaderLabel.text = "Connection Failed"
            loaderLabel.icon = AllIcons.General.Error
        }


    }



    override fun doOKAction() {
        val result = runWithModalProgressBlocking(project = project, title = "Connecting to Server") {
            return@runWithModalProgressBlocking sshConnection.connect(
                host = selectedHost.get(),
                username = selectedUsername.get(),
                port = selectedPort.get().toInt(),
                password = if (selectedMethod == "PASSWORD") selectedPassword.get() else null,
                privateKeyPath = if (selectedMethod == "PRIVATE_KEY") selectedPrivateKeyPath.get() else null
            )

        }
        if (result) {
            if(host!= null && (!selectedHost.get().equals(host) || !selectedUsername.get().equals(username))){
                sshConnection.removeServerConnection(host,username)
            }
            super.doOKAction()
            JOptionPane.showMessageDialog(null, "Server Connected Successfully")
        } else {
            loaderLabel.isVisible = true
            loaderLabel.text = "Connection Failed"
            loaderLabel.icon = AllIcons.General.Error
        }
    }
    override fun doCancelAction(){
       super.doCancelAction()
    }
}