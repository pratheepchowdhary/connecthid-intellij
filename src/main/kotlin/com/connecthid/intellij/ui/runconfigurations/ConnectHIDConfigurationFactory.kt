package com.connecthid.intellij.ui.runconfigurations

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class ConnectHIDConfigurationFactory(type: ConfigurationType,val task: RunConfigurationTask) : ConfigurationFactory(type) {
    override fun getId(): String {
        return task.name
    }

    override fun getName(): @Nls String {
        return task.name
    }

    override fun getIcon(): Icon {
        if (task == RunConfigurationTask.RemoteScript) {
            return AllIcons.Actions.RunAnything
        } else if (task == RunConfigurationTask.LocalScript) {
            return AllIcons.Actions.RunAnything
        } else if (task == RunConfigurationTask.Upload) {
            return AllIcons.Actions.Upload
        } else {
            return AllIcons.Actions.Download
        }
    }

    override fun createTemplateConfiguration(
        project: Project
    ): RunConfiguration {
        return ConnectHIDRunConfiguration(project, this, task.name, task)
    }

    override fun getOptionsClass(): Class<out BaseState> {
        return ConnectHIDRunConfigurationOptions::class.java
    }
}

