package com.connecthid.intellij.connection.terminal

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.utils.Utils.mapStringToEnvMap
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.BaseOutputReader
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.terminal.ProxyTtyConnector
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalUtil
import java.nio.file.Path


object TerminalExecution {


    fun executeInTerminal(project: Project, command: String) {
        executeInTerminal(project, command, null, null)
    }


    fun executeInTerminal(project: Project, command: String, workingDir: Path) {
        executeInTerminal(project, command, workingDir, null)
    }


    fun executeInTerminal(project: Project, command: String, terminalTabTitle: String) {
        executeInTerminal(project, command, null, terminalTabTitle)
    }

    
    fun executeInTerminal(project: Project, command: String, workingDir: Path?, terminalTabTitle: String?) {
        val terminalWidget = createTerminalWidget(project, workingDir, terminalTabTitle)
        terminalWidget.ttyConnectorAccessor.executeWithTtyConnector {
            terminalWidget.sendCommandToExecute(command)
        }

    }
    fun openSshSession(
        project: Project,
        server: Server,
        workingDir: String? = null,
    ) {
        val manager = TerminalToolWindowManager.getInstance(project)
        val runner = SshTerminalRunner(project,server,workingDir)
        manager.createNewSession(runner)
    }

    fun openSshSession1(
        project: Project,
        server: Server,
        terminalTabTitle: String = "SSH: ${server.username}@${server.host}"
    ) {
        // Create terminal widget

        try {
            val connector = SshTtyConnector(server = server)
            val terminalWidget = createTerminalWidget(project, null, terminalTabTitle)
            terminalWidget.connectToTty(connector, terminalWidget.termSize!!)
            terminalWidget.ttyConnectorAccessor.executeWithTtyConnector {
                terminalWidget.sendCommandToExecute("ls")
            }
            terminalWidget.requestFocus()
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to open SSH session: ${e.message}",
                "SSH Connection Error"
            )
        }
    }
    
    

    
    fun createTerminalWidget(project: Project, workingDir: Path?, terminalTabTitle: String?): TerminalWidget {
        val manager = TerminalToolWindowManager.getInstance(project)
        val workingDirectory = workingDir?.toString()
        return manager.createShellWidget(workingDirectory, terminalTabTitle, true, true)
    }

    fun createSshTerminalWidget(project: Project, workingDir: Path?, terminalTabTitle: String?): TerminalWidget {
        val manager = TerminalToolWindowManager.getInstance(project)

        val workingDirectory = workingDir?.toString()
        manager.createNewSession()
        return manager.createShellWidget(workingDirectory, terminalTabTitle, true, true)

    }

    
    fun getOrCreateTerminalWidget(project: Project, workingDir: Path?, terminalTabTitle: String?): TerminalWidget {
        val manager = TerminalToolWindowManager.getInstance(project)
        val workingDirectory = workingDir?.toString()
        return manager.terminalWidgets
            .firstOrNull { widget ->
                (terminalTabTitle.isNullOrBlank() || StringUtils.equals(widget.terminalTitle.buildTitle(), terminalTabTitle)) &&
                        !hasRunningCommands(widget)
            } ?: manager.createShellWidget(workingDirectory, terminalTabTitle, true, true)
    }

    
    fun hasRunningCommands(widget: TerminalWidget): Boolean {
        val connector = widget.ttyConnector ?: return false
        val processTtyConnector = getProcessTtyConnector(connector)
        return processTtyConnector?.let { TerminalUtil.hasRunningCommands(it) }
            ?: throw IllegalStateException("Cannot determine if there are running processes for ${connector.javaClass}")
    }

    
    fun getProcessTtyConnector(connector: TtyConnector?): ProcessTtyConnector? {
        return when (connector) {
            is ProcessTtyConnector -> connector
            is ProxyTtyConnector -> getProcessTtyConnector(connector.connector)
            else -> null
        }
    }

    fun runInConsole(task: TaskModel, server: Server?): ProcessHandler {
        val command = task.buildCommand()
        if (server == null) {
            val processHandler = createProcessHandler(command,task.executeInTerminal)
            return processHandler
        } else{
            return SshProcessHandler(server,task.executeInTerminal).also {
                it.commandDelayMs = 10
                it.generalCommandLine = command
            }
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

fun TaskModel.buildCommand(): GeneralCommandLine{
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
    if(scriptFile.isEmpty() && scriptText.isNotEmpty() && executeInTerminal){
        commandLine.addParameters("-i")
    }
    if (isLocal && executeInTerminal && scriptFile.isEmpty()) {

    } else {
        if (scriptFile.isNotEmpty()) {
            if (scriptOptions.isNotEmpty()) {
                commandLine.addParameters(ParametersListUtil.parse(scriptOptions))
            }
            commandLine.addParameter(scriptFile)
        } else {
            commandLine.addParameters("-c")
            commandLine.addParameters(scriptText)
        }
    }
    return commandLine
}

fun Project.openTerminal(server: Server,path: String?=null){
    TerminalExecution.openSshSession(this,server,path)
}
