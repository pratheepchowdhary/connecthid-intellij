package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.utils.log
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner


class ConnectHIDRunProfileState(
    environment: ExecutionEnvironment,
    taskModel: TaskModel
) : CommandLineState(environment) {
    val runTask = RunTask(taskModel,environment.project)

    override fun startProcess(): ProcessHandler {
        log.info("startProcess")
        return runTask
    }

    override fun execute(
        executor: com.intellij.execution.Executor,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        runTask.runTask()
        return DefaultExecutionResult(runTask.getConsoleView(), runTask)
    }
}

