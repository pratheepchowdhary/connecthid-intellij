package com.connecthid.intellij.connection.terminal

import com.connecthid.intellij.models.Server
import com.intellij.openapi.project.Project
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner

class SshTerminalRunner(
    project: Project,
    val server: Server,
    private val workingDir: String?=null): LocalTerminalDirectRunner(project) {
    val terminalTabTitle: String = "SSH: ${server.username}@${server.host}"
    override fun getDefaultTabTitle(): String {
        return terminalTabTitle
    }

    override fun createTtyConnector(process: PtyProcess): TtyConnector {
        return SshTtyConnector(server,workingDir)
    }
}