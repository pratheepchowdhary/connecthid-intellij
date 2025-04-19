package com.connecthid.intellij.terminal

import com.connecthid.intellij.terminal.ssh.SshTtyConnector
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.terminal.ui.TerminalWidget
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TtyConnector
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.terminal.ProxyTtyConnector
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalUtil
import java.nio.file.Path


object SshTerminalUtils {


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
        host: String,
        username: String,
        password: String? = null,
        privateKey: String? = null,
        port: Int = 22,
        terminalTabTitle: String = "SSH: $username@$host"
    ) {
        // Create terminal widget

        try {
            val connector = SshTtyConnector(host, port, username, password, privateKey)
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



}
