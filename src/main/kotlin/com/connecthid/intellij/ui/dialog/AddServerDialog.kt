package com.connecthid.intellij.ui.dialog

import com.connecthid.intellij.services.AuthenticationMethod
import com.connecthid.intellij.services.ServerConnection
import com.connecthid.intellij.services.SystemInfo
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress.mainBackgroundColor
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.JPasswordField
import com.intellij.ui.dsl.builder.*
import java.awt.Component.CENTER_ALIGNMENT

@ApiStatus.Experimental
class AddServerDialog : DialogWrapper(true) {
    private val hostField = JBTextField()
    private val usernameField = JBTextField()
    private val passwordField = JPasswordField()
    private val portField = IntegerField("22",1, 65535) // min 1, max 65535
    private val privateKeyPathField = JBTextField()
    private var authMethod = AuthenticationMethod.PASSWORD
    val propertyGraph = PropertyGraph()
    private val selectedMethod = propertyGraph.property(AuthenticationMethod.PASSWORD)

    init {
        title = "Add Server"
        init()
    }

    override fun createCenterPanel(): DialogPanel {

        return panel {
            row("Host:") {
                textField()
            }
            row("Username:") {
                textField()
            }
            row("Port:") {
                textField()
            }
            var radioButtonValue = 2
            buttonsGroup {
                row("Authentication:") {
                    radioButton("Password", 1)
                    radioButton("Private key", 2)
                }
            }.bind({ radioButtonValue }, { radioButtonValue = it })
            row("Password:") {
                passwordField().applyToComponent { text = "password" }
            }
            row("Private Key:") {
                textField()
            }

        }.apply {
            preferredSize = Dimension(400, 200)
            maximumSize = Dimension(400, 200)
            
            border = JBUI.Borders.empty(10)
            alignmentX = CENTER_ALIGNMENT
        }
    }

    private fun validateHost(): ValidationInfo? {
        return if (hostField.text.isNullOrBlank()) {
            ValidationInfo("Host is required", hostField)
        } else null
    }

    private fun validateUsername(): ValidationInfo? {
        return if (usernameField.text.isNullOrBlank()) {
            ValidationInfo("Username is required", usernameField)
        } else null
    }

    private fun validatePort(): ValidationInfo? {
        return if (portField.value < 1 || portField.value > 65535) {
            ValidationInfo("Port must be between 1 and 65535", portField)
        } else null
    }

    private fun validatePassword(): ValidationInfo? {
        return if (authMethod == AuthenticationMethod.PASSWORD && passwordField.password.isEmpty()) {
            ValidationInfo("Password is required", passwordField)
        } else null
    }

    private fun validatePrivateKey(): ValidationInfo? {
        return if (authMethod == AuthenticationMethod.PRIVATE_KEY && privateKeyPathField.text.isNullOrBlank()) {
            ValidationInfo("Private key path is required", privateKeyPathField)
        } else null
    }

    fun getServerConnection(): ServerConnection {
        return ServerConnection(
            host = hostField.text,
            username = usernameField.text,
            port = portField.value,
            authMethod = authMethod,
            privateKeyPath = if (authMethod == AuthenticationMethod.PRIVATE_KEY) privateKeyPathField.text else null,
            systemInfo = SystemInfo()
        )
    }
}
