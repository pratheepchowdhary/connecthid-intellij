
package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.connection.sftp.downloadSftpFiles
import com.connecthid.intellij.connection.sftp.uploadSftpFiles
import com.connecthid.intellij.connection.terminal.TerminalExecution
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.utils.*
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalExecutionConsole
import com.intellij.util.io.BaseOutputReader
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

class RunTask(
    private val taskModel: TaskModel,
    private val project: Project
) : OSProcessHandler(GeneralCommandLine().apply {
    withExePath(project.getDefaultShell())
}) {
    private val log = LoggerFactory.getLogger(RunTask::class.java)

    private val service by lazy { getSSHService() }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeHandlers = CopyOnWriteArrayList<ProcessHandler>()
    private val fileOperations = mutableListOf<Task.Backgroundable>()


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
                log("Task cancelled.")
                finish(cancelled = true)
                throw e
            } catch (e: Exception) {
                if (!isStopped()) {
                    log("Task failed: ${e.message}")
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
        var resultCode = 0

        if (taskType == RunConfigurationTaskType.Script) {
            resultCode =   startTask(task)
            if(resultCode != 0){
                if(task.scriptId.equals(this.taskModel.scriptId)){
                    log("Process finished with exit code ${resultCode}")
                }
                return
            }
        }
        else if (taskType == RunConfigurationTaskType.ScpFileTransfer) {
            log("📂 File transfer task ")
            console.attachToProcess(this)
            service.getServer(task.server)?.let {
                if (taskModel.uploadFiles.isNotEmpty() && taskModel.remoteFolder.isNotEmpty()) {
                    val files = taskModel.uploadFiles.getLocalFiles()
                    val remoteLocation = taskModel.remoteFolder.getSftpFile(it)
                    val latch = CountDownLatch(1)
                    val task =  uploadSftpFiles(project, remoteDir = remoteLocation, files, progress = { percent, text ->
                        val line = "\r"  + "$text ${coloredProgressBar((percent*100).toInt())}"
                        notifyTextAvailable(line, ProcessOutputTypes.STDOUT)

                    }) {
                        println("Upload completed, refreshing tree...")
                        latch.countDown()

                    }
                    task?.let {
                        fileOperations.add(it)
                        latch.await()
                        fileOperations.remove(it)
                    }
                }
                if (taskModel.downloadFiles.isNotEmpty()) {
                    val remoteFiles = taskModel.downloadFiles.getRemoteFiles(it)
                    val localLocation = taskModel.remoteFolder.getLocalFile()
                    val latch = CountDownLatch(1)
                    val task = downloadSftpFiles(project, remoteFiles, localLocation,progress = { percent, text ->


                    }) {
                        latch.countDown()
                    }
                    task?.let {
                        fileOperations.add(it)
                        latch.await()
                        fileOperations.remove(it)
                    }

                }
            }
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
        if(task.scriptId.equals(this.taskModel.scriptId)){
            log("Process finished with exit code ${resultCode}")
        }

        coroutineScope.ensureActive()
        finish()
    }
    fun coloredProgressBar(progress: Int, size: Int = 20): String {
        val green = "\u001B[32m"
        val yellow = "\u001B[33m"
        val red = "\u001B[31m"
        val reset = "\u001B[0m"

        val filled = (progress * size) / 100
        val partialBlock = listOf(" ", "▏", "▎", "▍", "▌", "▋", "▊", "▉", "█")
        val fractional = ((progress * size * 8) / 100) % 8

        val fullBlocks = "█".repeat(filled)
        val halfBlock = if (progress < 100) partialBlock[fractional] else ""
        val emptyBlocks = "-".repeat(size - filled - if (fractional > 0 && progress < 100) 1 else 0)

        return buildString {
            append("[")
            append(green).append(fullBlocks)
            if (fractional > 0 && progress < 100) {
                append(yellow).append(halfBlock)
            }
            append(red).append(emptyBlocks)
            append(reset).append("] ").append(progress).append("%")
        }
    }


    /**
     * Build and execute local command line.
     */
    private suspend fun startTask(task: TaskModel) : Int{
        //log("----- ${task.scriptName} task started ----")
        val (processHandler, command) = TerminalExecution.runInConsole(
            task,
            (if (task.server.equals("localhost", ignoreCase = true)) null else service.getServer(task.server))
        )
        processHandler.startNotify()
        if (processHandler is com.connecthid.intellij.connection.terminal.ProcessHandler) {
            console.attachToProcess(processHandler, processHandler.connector, true)
            if (taskModel.executeInTerminal || !taskModel.isLocal) {
                val command = if(!taskModel.isLocal && !taskModel.executeInTerminal) command.commandLineString else TerminalExecution.buildInterpreterCommand(taskModel)
                processHandler.executeCommand(command)
            }
        } else {
            console.attachToProcess(processHandler)
        }
        synchronized(activeHandlers) {
            activeHandlers.add(processHandler)
        }

        while (!processHandler.isProcessTerminated) {
            coroutineScope.ensureActive()
            delay(100)
        }
        synchronized(activeHandlers) {
            activeHandlers.remove(processHandler)
        }
        val resultCode = processHandler.exitCode?:-1
//        if(resultCode == 0){
//            log("---------------")
//        } else {
//            log("${task.scriptName} task failed", ConsoleViewContentType.LOG_ERROR_OUTPUT)
//        }
        return resultCode
    }

    /**
     * ProcessHandler overrides
     */
    override fun destroyProcessImpl() {
        stopped = true
        log("Process stopped by user \n")
        fileOperations.forEach {
            it.onCancel()
        }
        fileOperations.clear()
        //todo
        //console.terminalWidget.close()
        coroutineScope.cancel()
        synchronized(activeHandlers) {
            activeHandlers.forEach { it.destroyProcess() }
            activeHandlers.clear()
        }
        super.destroyProcessImpl()
    }

    override fun detachProcessImpl() {
        log.debug("detachProcessImpl")
        stopped = true
        detached = true
        log("\nProcess stopped by user")
        fileOperations.forEach {
            it.onCancel()
        }
        fileOperations.clear()
            //todo
       // console.terminalWidget.close()
        coroutineScope.cancel()
        synchronized(activeHandlers) {
            activeHandlers.forEach { it.destroyProcess() }
            activeHandlers.clear()
        }
        super.detachProcessImpl()
    }

    override fun detachIsDefault() = true

    fun isStopped(): Boolean = stopped

    fun log(message: String,logType:ConsoleViewContentType = ConsoleViewContentType.SYSTEM_OUTPUT) {
        val text = if (!message.endsWith("\n") && !message.startsWith("\r")) "$message\n" else message
        console.print(text, logType)
    }

    fun finish(cancelled: Boolean = false) {
        if (!stopped && !detached) {
            if (cancelled) {
                log("Task cancelled.")
                notifyProcessTerminated(130)
            } else {
                //log("Task finished successfully")
                notifyProcessTerminated(0)
            }
        }
    }
}