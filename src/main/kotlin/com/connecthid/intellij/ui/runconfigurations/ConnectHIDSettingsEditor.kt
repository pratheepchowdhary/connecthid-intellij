package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.ui.tasks.TaskForm
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import javax.swing.*

class ConnectHIDSettingsEditor(
    project: Project,
    taskType: RunConfigurationTaskType
) : SettingsEditor<ConnectHIDRunConfiguration>() {

    val form: TaskForm


    init {
        form = TaskForm(project, taskType,fromRunConfiguration=true)
    }


    override fun resetEditorFrom(configuration: ConnectHIDRunConfiguration) {
        form.resetEditorFrom(configuration.getTask())
    }

    override fun applyEditorTo(configuration: ConnectHIDRunConfiguration) {
        form.setData()
        configuration.taskModel.scriptName = configuration.name
    }


    override fun createEditor(): JComponent = form.createUIComponents()

}
