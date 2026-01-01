package com.connecthid.intellij.connection.terminal

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.utils.Utils.mapStringToEnvMap
import com.connecthid.intellij.utils.Utils.parseSftpUrl
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalExecutionConsole
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.BaseOutputReader
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.ProxyTtyConnector
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.nio.file.Path


object TerminalExecution {


    fun openSshSession(
        project: Project,
        server: Server,
        workingDir: String? = null,
    ) {
        val manager = TerminalToolWindowManager.getInstance(project)
        val runner = SshTerminalRunner(project,server,workingDir)
        manager.createNewSession(runner)
    }

    
    fun createTerminalWidget(project: Project, workingDir: Path?, terminalTabTitle: String?): TerminalWidget {
        val manager = TerminalToolWindowManager.getInstance(project)
        val workingDirectory = workingDir?.toString()
        return manager.createShellWidget(workingDirectory, terminalTabTitle, true, true)
    }

    
    fun getProcessTtyConnector(connector: TtyConnector?): ProcessTtyConnector? {
        return when (connector) {
            is ProcessTtyConnector -> connector
            is ProxyTtyConnector -> getProcessTtyConnector(connector.connector)
            else -> null
        }
    }

    fun runInConsole(task: TaskModel, server: Server?): Pair<ProcessHandler, GeneralCommandLine> {
        val command = task.buildCommand(server?.systemInfo?.connectHidDir ?: "")
        if (server == null) {
            return Pair(createProcessHandler(command,task.executeInTerminal),command)
        } else{
            return Pair(SshProcessHandler(server,task.executeInTerminal),command)
        }
    }

    private fun createProcessHandler(commandLine: GeneralCommandLine, executeInTerminal: Boolean): ProcessHandler {
        if (!executeInTerminal) {
            return object : KillableProcessHandler(commandLine) {
                override fun readerOptions(): BaseOutputReader.Options {
                    return BaseOutputReader.Options.forTerminalPtyProcess()
                }
            }
        }
        val cmd = commandLine.commandLineString.split(" ").toList()
        val env = commandLine.environment+ mapOf(
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8"
        )
        val workingDir = commandLine.workDirectory?.absolutePath?:""
        return PtyProcessHandler(cmd,workingDir,env)
    }



    fun buildInterpreterCommand(task: TaskModel): String {
        val interp = task.interpreterPath.lowercase()
        val file = task.scriptFile
        val text = task.scriptText
        val opts = task.scriptOptions
        val interpOpts = task.interpreterOptions

        // Escape Windows paths
        fun esc(path: String) = path.replace("\\", "\\\\")

        // --------------------------------------
        // If script TEXT is provided
        // --------------------------------------
        if (text.isNotBlank()) {
            return text.trim()
        }

        // --------------------------------------
        // FALLBACK: Original behaviour → when script FILE is provided
        // --------------------------------------
        return when {
            interp.endsWith("bash") ||
                    interp.endsWith("zsh") ||
                    interp.endsWith("sh") ||
                    interp.endsWith("fish") -> {
                ". \"${file}\" $opts"
            }

            interp.endsWith("powershell.exe") ||
                    interp.endsWith("pwsh.exe") ||
                    interp.endsWith("powershell") ||
                    interp.endsWith("pwsh") -> {
                ". \"${esc(file)}\" $opts"
            }

            interp.endsWith("cmd.exe") ||
                    interp.endsWith("cmd") -> {
                "call \"${esc(file)}\" $opts"
            }

            interp.endsWith("python") ||
                    interp.endsWith("python3") ||
                    interp.endsWith("python.exe") -> {
                "\"${task.interpreterPath}\" $interpOpts \"${file}\" $opts"
            }

            interp.endsWith("node") ||
                    interp.endsWith("node.exe") -> {
                "\"${task.interpreterPath}\" $interpOpts \"${file}\" $opts"
            }

            interp.endsWith("ruby") ||
                    interp.endsWith("ruby.exe") -> {
                "\"${task.interpreterPath}\" $interpOpts \"${file}\" $opts"
            }

            else -> {
                "\"${task.interpreterPath}\" $interpOpts \"${file}\" $opts"
            }
        }
    }





}

fun TaskModel.buildCommand(path: String): GeneralCommandLine{
    val commandLine = PtyCommandLine()
        .withConsoleMode(executeInTerminal)
        .withInitialColumns(160)
        .withInitialRows(40)
        .withEnvironment(mapStringToEnvMap(envData))
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withWorkingDirectory(Path.of(workingDir))
        .withExePath(interpreterPath)
    if (interpreterOptions.isNotEmpty()) {
        commandLine.addParameters(ParametersListUtil.parse(interpreterOptions))
    }
    //local script text non interactive
    if(isLocal&& !isScriptFile && !executeInTerminal){
        commandLine.addParameters("-c")
        commandLine.addParameters(scriptText)
    }  else if (isLocal&&!executeInTerminal){
        if(scriptOptions.isNotEmpty()){
            commandLine.addParameters(ParametersListUtil.parse(scriptOptions))
        }
        commandLine.addParameters(scriptFile)
    }

    // remote script
    if(!isLocal && !isScriptFile){
        commandLine.addParameters("-c")
        commandLine.addParameters(scriptText)
    }  else if (!isLocal){
        if(scriptOptions.isNotEmpty()){
            commandLine.addParameters(ParametersListUtil.parse(scriptOptions))
        }
        if (scriptFile.startsWith("sftp")) {
            val url  = parseSftpUrl(scriptFile)
            commandLine.addParameters(url.path)
        } else {
            commandLine.addParameters(path.plus("/tasks/").plus(Path.of(scriptFile).fileName.toString()))
        }
    }
    return commandLine
}



fun TerminalExecutionConsole.attachToProcessHandler(
    processHandler: ProcessHandler,
    taskModel: TaskModel,
    command: GeneralCommandLine
){
    if (processHandler is com.connecthid.intellij.connection.terminal.ProcessHandler) {
        attachToProcess(processHandler, processHandler.connector, true)
        if(taskModel.isLocal&& !taskModel.isScriptFile){
            command.addParameters("-c")
            command.addParameters(taskModel.scriptText)
        }  else if (taskModel.isLocal){
            if(taskModel.scriptOptions.isNotEmpty()){
                command.addParameters(ParametersListUtil.parse(taskModel.scriptOptions))
            }
            command.addParameters(taskModel.scriptFile)
        }
        processHandler.executeCommand(command.commandLineString,taskModel)
    } else {
        attachToProcess(processHandler)
    }
}


fun Project.openTerminal(server: Server,path: String?=null){
    TerminalExecution.openSshSession(this,server,path)
}
