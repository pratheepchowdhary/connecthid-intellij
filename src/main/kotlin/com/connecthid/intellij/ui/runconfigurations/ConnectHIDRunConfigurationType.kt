package com.connecthid.intellij.ui.runconfigurations

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NotNullFactory
import com.intellij.openapi.util.NotNullLazyValue
import javax.swing.Icon

internal class ConnectHIDRunConfigurationType : ConfigurationTypeBase(
    "ConnectHID", "ConnectHID", "Run configuration for ConnectHID",
    NotNullLazyValue.createValue<Icon?>(NotNullFactory { AllIcons.Nodes.Console })
) {
    init {
        addFactory(ConnectHIDConfigurationFactory(this, RunConfigurationTask.Script))
        addFactory(ConnectHIDConfigurationFactory(this, RunConfigurationTask.SftpFileTransfer))
    }

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return super.getConfigurationFactories()
    }

}