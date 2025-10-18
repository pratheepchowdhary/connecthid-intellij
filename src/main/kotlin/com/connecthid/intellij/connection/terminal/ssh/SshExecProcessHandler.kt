package com.connecthid.intellij.connection.terminal.ssh

import com.connecthid.intellij.connection.ssh.SSHConnection
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * A ProcessHandler implementation for managing an interactive SSH shell session.
 * Integrates with IntelliJ's terminal emulator, forwarding I/O via coroutines and JSch.
 *
 * @property server The target server configuration.
 */
class SshExecProcessHandler(
     server: Server
) : SshProcessHandler(server) {


    private companion object {
        private val LOG = Logger.getInstance(SshExecProcessHandler::class.java)
    }


    override fun getChannelTarget(): Channel? {
        return connection.getExecChannelFromPool()
    }


    init {
        (channel as ChannelExec).setPty(true)
    }

    /**
     * Resizes the PTY to the specified dimensions.
     * @param columns Number of columns.
     * @param rows Number of rows.
     */
    override fun resize(columns: Int, rows: Int) {
        if (channel.isConnected) {
            (channel as ChannelExec).setPtySize(columns, rows, 800, 600)
            LOG.debug("Resized PTY to ${columns}x${rows}")
        }
    }

    // UPDATED: Now returns Int (exit code); waits for command completion
    override suspend fun sendCommand(command: String): Int {
        return try {
            (channel as ChannelExec).let {
                it.setCommand(command)
                it.connect(10000)
            }
            LOG.debug("Started execution of command: $command")

            // NEW: Wait for command to complete (channel close) with polling
            while (!channel.isClosed) {
                delay(100.milliseconds) // Efficient polling; adjustable if needed
            }
            exitStatus = channel.exitStatus
            LOG.debug("Command '$command' completed with exit status: $exitStatus")
            exitStatus.takeIf { it != -1 } ?: 0 // Return 0 if unavailable
        } catch (e: Exception) {
            LOG.warn("Failed to execute command '$command'", e)
            notifyTextAvailable("Error executing command: ${e.message}\n", ProcessOutputTypes.STDERR)
            exitStatus = -1
            exitStatus// Return error code
        } finally {
            // Optional: Auto-destroy after command if desired (uncomment if caller doesn't manage lifecycle)
             destroyProcess()
        }
    }
}