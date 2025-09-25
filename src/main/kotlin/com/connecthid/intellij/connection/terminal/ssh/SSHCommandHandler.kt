package com.connecthid.intellij.connection.terminal.ssh

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.NopProcessHandler
import com.intellij.openapi.project.Project
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class SSHCommandHandler(
    private val server: Server,
    private val remoteCommand: String,
) : NopProcessHandler() {
    val connectionService = getSSHService()


    override fun startNotify() {
        super.startNotify()
        connectionService.getConnection(server.host,server.username)


    }
}
