package com.connecthid.intellij.connection.terminal.ssh

import com.connecthid.intellij.models.Server
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * A ProcessHandler implementation for managing an interactive SSH shell session.
 * Integrates with IntelliJ's terminal emulator, forwarding I/O via coroutines and JSch.
 *
 * @property server The target server configuration.
 */
class SshShellProcessHandler(
    server: Server
) : SshProcessHandler(server) {

    private companion object {
        private val LOG = Logger.getInstance(SshShellProcessHandler::class.java)
    }


    override fun getChannelTarget(): Channel? {
        return connection.getShellChannelFromPool()
    }

    init {
        (channel as ChannelShell).setPty(true)
    }

    /**
     * Resizes the PTY to the specified dimensions.
     * @param columns Number of columns.
     * @param rows Number of rows.
     */
    override fun resize(columns: Int, rows: Int) {
        if (channel.isConnected) {
            (channel as ChannelShell).setPtySize(columns, rows, 800, 600)
            LOG.debug("Resized PTY to ${columns}x${rows}")
        }
    }

    override suspend fun sendCommand(command: String): Int {
        withContext(Dispatchers.IO) {
            try {
                shellOut.value.write((command + "\n").toByteArray(StandardCharsets.UTF_8))
                shellOut.value.flush()
                delay(commandDelayMs)
            } catch (e: IOException) {
                notifyTextAvailable("Send command error: ${e.message}", ProcessOutputTypes.STDERR)
            }
        }
        return 0
    }
}