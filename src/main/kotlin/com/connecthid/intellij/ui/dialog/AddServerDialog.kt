package com.connecthid.intellij.ui.dialog

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.services.AuthenticationMethod
import com.connecthid.intellij.services.ServerConnection
import com.connecthid.intellij.services.SystemInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
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
import javax.swing.*
import kotlin.collections.contains


@ApiStatus.Experimental
class AddServerDialog(host: String? = null, username: String = "root", password: String? = null, port: Int = 22, privateKeyPath: String? = null) : DialogWrapper(true) {
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
    val fileDescriptor = FileChooserDescriptor(true, false, false, false, false, false)
        .withFileFilter { file -> file.extension in listOf("pem", "ppk", "rsa") }

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
                textFieldWithBrowseButton("Select Private Key File", fileChooserDescriptor = fileDescriptor){
                    selectedPrivateKeyPath.set(it.path)
                    return@textFieldWithBrowseButton it.path
                }.bindText(selectedPrivateKeyPath)
                    .enabledIf(privateKeyRadioButton.selected)
                    .apply { jbPrivateKeyField = component.textField }
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
            override fun actionPerformed(@NotNull e: ActionEvent) {


            }
        })
    }
    override fun doOKAction() {
        super.doOKAction()
    }
    override fun doCancelAction(){
       super.doCancelAction()
    }

    fun getServerConnection(): ServerConnection {
        return ServerConnection(
            host = selectedHost.get(),
            username = selectedUsername.get(),
            port = selectedPort.get().toInt(),
            authMethod = AuthenticationMethod.valueOf(selectedMethod),
            privateKeyPath = if (selectedMethod == "PRIVATE_KEY") selectedPrivateKeyPath.get() else null,
            systemInfo = SystemInfo()
        )
    }
}
