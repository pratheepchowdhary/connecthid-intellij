package com.connecthid.intellij.connection.terminal.ssh

import com.intellij.openapi.project.Project
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner

class SshTerminalRunner(
    project: Project,
    private val host: String,
    private val port: Int = 22,
    private val user: String,
    private val password: String? = null,
    private val privateKeyPath: String? = null,
    private val workingDir: String?=null):LocalTerminalDirectRunner(project) {
    val terminalTabTitle: String = "SSH: $user@$host"
    override fun getDefaultTabTitle(): String {
        return terminalTabTitle
    }

    override fun createTtyConnector(process: PtyProcess): TtyConnector {
        return SshTtyConnector(host, port, user, password, privateKeyPath,workingDir)
    }
}