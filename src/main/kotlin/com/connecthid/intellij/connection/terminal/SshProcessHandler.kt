package com.connecthid.intellij.connection.terminal

import com.connecthid.intellij.connection.sftp.uploadIfMTimeDifferent
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.TaskModel
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


    var exitStatus = 0

    init {
        LOG.debug("SSH channel connected for ${server.host}")
        connector = SshTtyConnector(server, interactive = interactive)
    }

    private val sshConnector: SshTtyConnector get() = connector as SshTtyConnector
    override fun getProcessOutputStream(): OutputStream = sshConnector.outputStream
    override fun getProcessInputStream(): InputStream = sshConnector.inputStream



    override fun executeCommand(command: String, task: TaskModel): Int {
        if(task.isScriptFile && task.scriptFile.isNotEmpty() && !task.scriptFile.startsWith("sftp")){
            sshConnector.connection?.withSftp {
                it.uploadIfMTimeDifferent(task.scriptFile,server.systemInfo.connectHidDir.plus("/tasks/"))
            }
        }
        var exitMessage = ""
        val RED = "\u001B[31m"
        return try {
            LOG.debug("Executing command '$command'")
            if(!interactive){
                notifyTextAvailable("$command\r\n", ProcessOutputTypes.STDOUT)
            }
            val (exitCode,errorMessage) = sshConnector.sendCommand(command)
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