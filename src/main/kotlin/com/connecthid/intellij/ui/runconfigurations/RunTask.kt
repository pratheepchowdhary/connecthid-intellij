
package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.connection.terminal.ssh.SshExecProcessHandler
import com.connecthid.intellij.connection.terminal.ssh.SshProcessHandlerTtyConnector
import com.connecthid.intellij.connection.terminal.ssh.SshShellProcessHandler
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.utils.Utils.mapStringToEnvMap
import com.connecthid.intellij.utils.getDefaultShell
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalExecutionConsole
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.BaseOutputReader
import kotlinx.coroutines.*
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

class RunTask(
    private val taskModel: TaskModel,
    private val project: Project
) : OSProcessHandler(GeneralCommandLine().apply {
    withExePath(project.getDefaultShell())
}) {

    private val service by lazy { getSSHService() }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeHandlers = CopyOnWriteArrayList<ProcessHandler>()


    val console: TerminalExecutionConsole by lazy {
        val terminalConsole = TerminalExecutionConsole(project, null)
        terminalConsole
    }

    @Volatile
    private var stopped = false
    private var detached = false



    fun getConsoleView(): ConsoleView = console

    override fun readerOptions(): BaseOutputReader.Options {
        return BaseOutputReader.Options.forTerminalPtyProcess()
    }

    /**
     * Launch main task execution inside coroutine scope.
     */
    fun runTask(task: TaskModel = taskModel) {
        startNotify()
        coroutineScope.launch {
            try {
                executeTask(task)
            } catch (e: CancellationException) {
                log("⚠️ Task cancelled.")
                finish(cancelled = true)
                throw e
            } catch (e: Exception) {
                if (!isStopped()) {
                    log("❌ Task failed: ${e.message}")
                    notifyProcessTerminated(1)
                }
            } finally {
                if (!isProcessTerminated && !detached) {
                    notifyProcessTerminated(0)
                }
            }
        }
    }

    /**
     * Executes a given task, respecting coroutine cancellation.
     */
    private suspend fun executeTask(task: TaskModel) {
        coroutineScope.ensureActive()

        val taskType = RunConfigurationTaskType.fromType(task.scriptType)

        if (taskType == RunConfigurationTaskType.Script) {
            if (task.server.equals("localhost", ignoreCase = true)) {
                buildExecutionResult(task)
            } else {
                service.getServer(task.server)?.let {
                 val resultCode =   buildSSHExecutionResult(task,it)
                 if(resultCode != 0){
                     return
                 }
                }
            }
        } else if (taskType == RunConfigurationTaskType.SftpFileTransfer) {
            log("📂 File transfer task detected (not implemented)")
        }

        // Execute dependent tasks
        if (task.runAfter.isNotEmpty()) {
            val taskIds = task.runAfter.split(";")
            val allTasks = service.getTasks()
            for (id in taskIds) {
                coroutineScope.ensureActive()
                val dependentTask = allTasks.firstOrNull { it.scriptId == id }
                if (dependentTask != null) {
                    executeTask(dependentTask)
                }
            }
        }

        coroutineScope.ensureActive()
        finish()
    }

    /**
     * Build and execute local command line.
     */
    private suspend fun buildExecutionResult(task: TaskModel) {
        val commandLine: GeneralCommandLine = if (task.isScriptFile) {
            createCommandLineForFile(task)
        } else {
            createCommandLineForScript(task)
        }

        val processHandler = createProcessHandler(commandLine)

        console.attachToProcess(processHandler)
        synchronized(activeHandlers) {
            activeHandlers.add(processHandler)
        }
        processHandler.startNotify()

        while (!processHandler.isProcessTerminated) {
            coroutineScope.ensureActive()
            delay(100)
        }
        synchronized(activeHandlers) {
            activeHandlers.remove(processHandler)
        }

        log("✅ Script completed: ${task.scriptName}")
    }

    /**
     * Build and execute command over SSH.
     */
    private suspend fun buildSSHExecutionResult(task: TaskModel, server: Server) : Int{
        val commandLine: GeneralCommandLine = if (task.isScriptFile) {
            createCommandLineForFile(task)
        } else {
            createCommandLineForScript(task)
        }
        log("Connecting to ${server.stmpName}")
        val processHandler = if(!task.executeInTerminal) SshExecProcessHandler(server) else SshShellProcessHandler(server)
        log("Connected to ${server.stmpName}")
        val connector = SshProcessHandlerTtyConnector(processHandler,Charsets.UTF_8)
        console.attachToProcess(processHandler,connector,true)
        synchronized(activeHandlers) {
            activeHandlers.add(processHandler)
        }
        processHandler.startNotify()
        processHandler.commandDelayMs=(10)
        log("${task.scriptName} started executing..")
        val resultCode = processHandler.sendCommand(task.scriptText)
        while (!processHandler.isProcessTerminated) {
            coroutineScope.ensureActive()
            delay(100)
        }

        synchronized(activeHandlers) {
            activeHandlers.remove(processHandler)
        }
        if(resultCode == 0){
            log("✅ ${task.scriptName} finished successfully")
        } else {
           log("❌  ${task.scriptName} task failed", ConsoleViewContentType.LOG_ERROR_OUTPUT)
        }
        return  resultCode
    }



    private fun createProcessHandler(commandLine: GeneralCommandLine): ProcessHandler {
        return object : KillableProcessHandler(commandLine) {
            override fun readerOptions(): BaseOutputReader.Options {
                return BaseOutputReader.Options.forTerminalPtyProcess()
            }
        }
    }

    private fun createCommandLineForFile(task: TaskModel): GeneralCommandLine {
        val commandLine = PtyCommandLine()
            .withConsoleMode(false)
            .withInitialColumns(160)
            .withInitialRows(40)
            .withEnvironment(mapStringToEnvMap(task.envData))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkingDirectory(Path.of(task.workingDir))
            .withExePath(task.interpreterPath)

        if (task.interpreterOptions.isNotEmpty()) {
            commandLine.addParameters(ParametersListUtil.parse(task.interpreterOptions))
        }
        commandLine.addParameter(task.scriptFile)
        if (task.scriptOptions.isNotEmpty()) {
            commandLine.addParameters(ParametersListUtil.parse(task.scriptOptions))
        }
        return commandLine
    }

    private fun createCommandLineForScript(task: TaskModel): GeneralCommandLine {
        return PtyCommandLine()
            .withConsoleMode(false)
            .withInitialColumns(160)
            .withInitialRows(40)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkingDirectory(Path.of(task.workingDir))
            .withExePath(project.getDefaultShell())
            .withParameters("-c")
            .withParameters(task.scriptText)
    }

    /**
     * ProcessHandler overrides
     */
    override fun destroyProcessImpl() {
        stopped = true
        log("❌ Process stopped by user")
        console.terminalWidget.close()
        coroutineScope.cancel()
        synchronized(activeHandlers) {
            activeHandlers.forEach { it.destroyProcess() }
            activeHandlers.clear()
        }
        notifyProcessTerminated(1)
        super.destroyProcessImpl()
    }

    override fun detachProcessImpl() {
        print("detachProcessImpl")
        stopped = true
        detached = true
        log("⚠️ Process detached by user")
        console.terminalWidget.close()
        coroutineScope.cancel()
        synchronized(activeHandlers) {
            activeHandlers.forEach { it.destroyProcess() }
            activeHandlers.clear()
        }
        notifyProcessDetached()
        super.detachProcessImpl()
    }

    override fun detachIsDefault() = true

    fun isStopped(): Boolean = stopped

    fun log(message: String,logType:ConsoleViewContentType = ConsoleViewContentType.SYSTEM_OUTPUT) {
        val text = if (!message.endsWith("\n")) "$message\n" else message
        console.print(text, logType)
    }

    fun finish(cancelled: Boolean = false) {
        if (!stopped && !detached) {
            if (cancelled) {
                log("⚠️ Task cancelled.")
                notifyProcessTerminated(1)
            } else {
                log("✅ Task finished successfully")
                notifyProcessTerminated(0)
            }
        }
    }
}