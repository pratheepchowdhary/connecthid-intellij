package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.models.TaskModel
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.util.*


class ConnectHIDRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String?,
    val taskType: RunConfigurationTaskType
) : RunConfigurationBase<ConnectHIDRunConfigurationOptions>(project, factory, name) {


    var taskModel = TaskModel(scriptId = UUID.randomUUID().toString())


    override fun getOptions(): ConnectHIDRunConfigurationOptions {
        return super.getOptions() as ConnectHIDRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        return ConnectHIDSettingsEditor(project, taskType)
    }

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        println("check configation")
        //todo
        return
//        val scriptPath = Path.of(myScriptPath)
//        if (myExecuteScriptFile) {
//            if (!Files.exists(scriptPath)) {
//                throw RuntimeConfigurationError(message("sh.run.script.not.found"))
//            }
//            if (StringUtil.isNotEmpty(myInterpreterPath) || !Files.isExecutable(scriptPath)) {
//                val interpreterPath = Path.of(myInterpreterPath)
//                if (!Files.exists(interpreterPath)) {
//                    throw RuntimeConfigurationError(message("sh.run.interpreter.not.found"))
//                }
//                if (!Files.isExecutable(interpreterPath)) {
//                    throw RuntimeConfigurationError(message("sh.run.interpreter.should.be.executable"))
//                }
//            }
//        }
//        if (!Files.exists(Path.of(myScriptWorkingDirectory))) {
//            throw RuntimeConfigurationError(message("sh.run.working.dir.not.found"))
//        }

    }


    fun getTask(): TaskModel {
        return taskModel
    }

    fun setTask(taskModel: TaskModel) {
        this.taskModel = taskModel
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(taskModel, element)
    }

    @Throws(InvalidDataException::class)
    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(taskModel, element)
    }


    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ): RunProfileState {
        return ConnectHIDRunProfileState(environment, taskModel)
    }

}

