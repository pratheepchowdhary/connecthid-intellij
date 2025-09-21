package com.connecthid.intellij.ui.runconfigurations

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
        addFactory(ConnectHIDConfigurationFactory(this, RunConfigurationTask.LocalScript))
        addFactory(ConnectHIDConfigurationFactory(this, RunConfigurationTask.RemoteScript))
        addFactory(ConnectHIDConfigurationFactory(this, RunConfigurationTask.Upload))
        addFactory(ConnectHIDConfigurationFactory(this, RunConfigurationTask.Download))
    }


}