package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import javax.swing.JComponent

class TaskDialog(
    private val project: Project, private val taskType: RunConfigurationTask, taskModel: TaskModel? = null
) : DialogWrapper(project) {

    val form: TaskForm
    private var dialogPanel: JComponent? = null

    init {
        form = TaskForm(project, taskType, taskModel)
        init()
        title = (if (taskModel == null) "Create ${taskType.name}" else "Update ${taskModel.scriptName}")
    }


    override fun getInitialSize(): Dimension {
        return Dimension(650, 500)
    }


    override fun createCenterPanel(): JComponent {
        if (dialogPanel == null) {
            dialogPanel = form.createUIComponents()
        }
        return dialogPanel!!
    }


    override fun doOKAction() {
        form.save()
        super.doOKAction()

    }

    override fun doCancelAction() {
        super.doCancelAction()
    }
}