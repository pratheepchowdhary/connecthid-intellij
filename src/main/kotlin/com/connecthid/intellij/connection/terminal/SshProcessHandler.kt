package com.connecthid.intellij.connection.terminal

import com.connecthid.intellij.models.Server
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import java.io.InputStream
import java.io.OutputStream

/**
 * A ProcessHandler implementation for managing an interactive SSH shell session.
 * Integrates with IntelliJ's terminal emulator, forwarding I/O via coroutines and JSch.
 *
 * @property server The target server configuration.
 */
 class SshProcessHandler(
    val server: Server,
    val interactive: Boolean = true
) : ProcessHandler() {

    private companion object {
        private val LOG = Logger.getInstance(SshProcessHandler::class.java)
    }

    val sshTtyConnector: SshTtyConnector
    var generalCommandLine: GeneralCommandLine? = null
    var exitStatus = 0

    init {
        LOG.debug("SSH channel connected for ${server.host}")
        sshTtyConnector = SshTtyConnector(server, interactive = interactive)
    }

    override fun getProcessOutputStream(): OutputStream = sshTtyConnector.outputStream
    override fun getProcessInputStream(): InputStream = sshTtyConnector.inputStream
    override fun isConnected(): Boolean = sshTtyConnector.isConnected

    override fun disconnect() {
        runCatching { sshTtyConnector.close() }.onFailure { LOG.warn("Cleanup error", it) }
    }

    override fun onStart() {
        generalCommandLine?.let {
            executeCommand(it.commandLineString)
        }
    }

    override fun executeCommand(command: String): Int {
        var exitMessage = ""
        val RED = "\u001B[31m"
        return try {
            LOG.debug("Executing command '$command'")
            if(!interactive){
                notifyTextAvailable("$command\r\n", ProcessOutputTypes.STDOUT)
            }
            val (exitCode,errorMessage) = sshTtyConnector.sendCommand(command)
            exitStatus = exitCode
            exitMessage = errorMessage
            LOG.debug("Command '$command' completed with exit status: $exitStatus")
            if (exitStatus != -1) exitStatus else 0
        } catch (e: Exception) {
            LOG.warn("Failed to execute command '$command'", e)
            notifyTextAvailable("Error executing command: ${e.message}\n", ProcessOutputTypes.STDERR)
            exitStatus = -1
            exitStatus
        } finally {
            if(!interactive){
                if(exitStatus !=0 && exitMessage.isNotEmpty() && !exitMessage.equals("null")){
                    notifyTextAvailable("${RED} ${exitMessage}\n", ProcessOutputTypes.STDERR)
                }
                destroyProcess()
            }
        }
    }
}