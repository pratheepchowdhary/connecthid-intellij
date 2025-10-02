package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.models.Server
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.diagnostic.logger

private val LOG = logger<ConnectHIDRunProfileState>()

class ConnectHIDRunProfileState(
    environment: ExecutionEnvironment,
    private val server: Server?
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        LOG.info("startProcess")
        return DummyProcessHandler()
    }

    override fun execute(
        executor: com.intellij.execution.Executor,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        val project = environment.project
        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project).console
        val processHandler = DummyProcessHandler()
        console.attachToProcess(processHandler)


        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running My Task", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                // --- Step 1: Download ---
                indicator.text = "Downloading file..."
                for (i in 1..100) {
                    if (processHandler.isStopped()) return
                    indicator.fraction = i / 200.0   // 0 → 0.5
                    processHandler.log("Downloading... $i%")
                    Thread.sleep(30)
                }

                // --- Step 2: Upload ---
                indicator.text = "Uploading file..."
                for (i in 1..100) {
                    if (processHandler.isStopped()) return
                    indicator.fraction = (100 + i) / 200.0   // 0.5 → 1.0
                    processHandler.log("Uploading... $i%")
                    Thread.sleep(30)
                }

                indicator.text = "Done!"
                processHandler.finish()
            }
        })

        return DefaultExecutionResult(console, processHandler)
    }
}

