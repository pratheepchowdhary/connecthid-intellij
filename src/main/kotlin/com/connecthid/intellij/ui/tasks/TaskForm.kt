package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel

class TaskForm(
    private val project: Project,
    private val taskType: RunConfigurationTaskType,
    taskModel: TaskModel? = null,
    private val fromRunConfiguration: Boolean = false
) {
    private val form: BaseTaskForm = when (taskType) {
        RunConfigurationTaskType.Script -> ScriptTaskForm(project, taskModel, fromRunConfiguration)
        RunConfigurationTaskType.ScpFileTransfer -> ScpTaskForm(project, taskModel, fromRunConfiguration)
        RunConfigurationTaskType.AOSP -> AospTaskForm(project, taskModel, fromRunConfiguration)
    }

    fun createUIComponents(): DialogPanel {
        return form.createUIComponents()
    }

    fun resetEditorFrom(configuration: TaskModel) {
        form.resetEditorFrom(configuration)
    }

    fun setData() {
        form.setData()
    }

    fun save() {
        form.save()
    }
}