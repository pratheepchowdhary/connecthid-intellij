package com.connecthid.intellij.connection.terminal.ssh

import com.connecthid.intellij.connection.ssh.SSHConnection
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
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
class SshShellProcessHandler(
    val server: Server
) : ProcessHandler() {

    private val service = getSSHService()
    private companion object {
        private val LOG = Logger.getInstance(SshShellProcessHandler::class.java)
    }

    private var connection: SSHConnection
    private val channel: ChannelShell
    private val processStdin: PipedOutputStream = PipedOutputStream()
    private lateinit var shellInput: PipedInputStream
    private val shellOut: Lazy<OutputStream> = lazy { channel.outputStream }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val inputForwardingActive = AtomicBoolean(true)
    private var commandDelayMs: Long = 200L
    private var outputPollDelayMs: Long = 20L // Configurable polling delay for better responsiveness

    // quick connected flag to be used by isConnected()
    @Volatile
    private var connectedFlag = true

    // signal when forwarding coroutines are finished
    private val forwardingFinished = CompletableDeferred<Unit>()

    init {
        connection = service.getConnection(server) ?: throw Exception("Unable to connect server")
        channel = (connection.getShellChannelFromPool()
            ?: throw Exception("Unable to obtain shell channel"))
        channel.setPty(true)
        // Add timeout to prevent hanging on connect
        runBlocking {
            withTimeout(10000.milliseconds) {
                channel.connect()
            }
        }
        LOG.debug("SSH channel connected for ${server.host}")
    }

    /**
     * Resizes the PTY to the specified dimensions.
     * @param columns Number of columns.
     * @param rows Number of rows.
     */
    fun resize(columns: Int, rows: Int) {
        if (channel.isConnected) {
            channel.setPtySize(columns, rows, 800, 600)
            LOG.debug("Resized PTY to ${columns}x${rows}")
        }
    }

    /**
     * Sends a command to the remote shell with a configurable post-send delay for execution pacing.
     * Must be called after startNotify().
     * @param command The command to send.
     */
    suspend fun sendCommand(command: String) {
        if (!started.get()) throw IllegalStateException("Handler not started.")
        withContext(Dispatchers.IO) {
            try {
                shellOut.value.write((command + "\n").toByteArray(StandardCharsets.UTF_8))
                shellOut.value.flush()
                delay(commandDelayMs)
            } catch (e: IOException) {
                notifyTextAvailable("Send command error: ${e.message}", ProcessOutputTypes.STDERR)
            }
        }
    }

    fun setCommandDelayMs(delayMs: Long) {
        commandDelayMs = delayMs
    }

    /**
     * Sets the polling delay for output forwarding (in ms). Lower values increase responsiveness but CPU usage.
     */
    fun setOutputPollDelayMs(delayMs: Long) {
        outputPollDelayMs = delayMs
    }

    override fun destroyProcessImpl() {
        LOG.warn("destroyProcessImpl stopping terminal")
        // mark not connected early to let terminal/emulator give up quickly
        connectedFlag = false

        // stop input forwarding and cancel coroutines first
        inputForwardingActive.set(false)
        scope.cancel() // cancel coroutines (reading/writing)
        // Wait (bounded) for forwarding coroutines to finish to avoid interrupting emulator
        try {
            runBlocking {
                withTimeoutOrNull(2000.milliseconds) {
                    forwardingFinished.await()
                } ?: LOG.warn("Forwarding timeout exceeded during shutdown")
            }
        } catch (e: CancellationException) {
            LOG.debug("Shutdown cancelled", e)
        }

        notifyProcessDetached()

        // close local stdin stream
        runCatching { processStdin.close() }.onFailure { LOG.warn("Error closing stdin", it) }

        // disconnect/cleanup channel and release back to pool if applicable
        runCatching {
            if (channel.isConnected && !channel.isClosed) {
                try {
                    channel.disconnect()
                } catch (ignored: Exception) {
                    LOG.warn("Error disconnecting channel", ignored)
                }
            }
            // If your pool expects disconnected channels, ensure it's disconnected before releasing.
            connection.releaseShellChannelToPool(channel)
        }.onFailure { LOG.warn("Cleanup error", it) }

        // finally mark terminated; log raw exit status for debugging
        val exitStatus = channel.exitStatus
        LOG.debug("Channel exit status: $exitStatus")
        notifyProcessTerminated(exitStatus.takeIf { it != -1 } ?: 0)
    }

    override fun detachProcessImpl() {
        // reuse destroy path
        destroyProcessImpl()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream = processStdin
    fun getShellChannel() = channel

    override fun startNotify() {
        if (!started.compareAndSet(false, true)) return

        runCatching {
            // connect PipedInputStream to the PipedOutputStream used as process stdin
            shellInput = PipedInputStream(processStdin)
            setupForwarding()
            // NOTE: assume providedChannel is already connected by caller if provided.
            // If not provided and channel came from pool, caller should ensure it's connected (or you can call channel.connect())
        }.onFailure {
            LOG.warn("Failed to start SSH terminal", it)
            notifyProcessTerminated(-1)
        }
    }

    private fun setupForwarding() {
        // Launch two coroutines: output (shell -> terminal) and input (terminal -> shell)
        // When both finish, complete forwardingFinished.

        // Output: Shell -> Console (non-blocking read with timeout for efficiency)
        val outputJob = scope.launch {
            val shellIn = channel.inputStream
            val buffer = ByteArray(4096) // Larger buffer for better throughput
            try {
                while (!channel.isClosed && isActive) {
                    ensureActive()
                    val read = withTimeout(outputPollDelayMs) {
                        try {
                            shellIn.read(buffer)
                        } catch (e: IOException) {
                            -1
                        }
                    } ?: continue // No data, retry
                    if (read < 0) break // EOF
                    notifyTextAvailable(String(buffer, 0, read, StandardCharsets.UTF_8), ProcessOutputTypes.STDOUT)
                }
            } catch (e: CancellationException) {
                // normal cancellation - rethrow so parent job handles it
                throw e
            } catch (e: InterruptedException) {
                LOG.debug("Terminal reading interrupted")
                Thread.currentThread().interrupt()
            } catch (e: IOException) {
                LOG.warn("Terminal I/O exception", e)
            } finally {
                // Nothing to do here except ensure the other coroutine is allowed to finish
                LOG.debug("Output forwarding coroutine finished")
            }
        }

        // Input: Console -> Shell
        val inputJob = scope.launch {
            val buf = ByteArray(1024)
            try {
                while (inputForwardingActive.get() && isActive) {
                    ensureActive()
                    val read = try {
                        shellInput.read(buf)
                    } catch (e: IOException) {
                        -1
                    }
                    if (read > 0) {
                        try {
                            shellOut.value.write(buf, 0, read)
                            shellOut.value.flush()
                        } catch (e: IOException) {
                            LOG.warn("Error writing to shell", e)
                            break
                        }
                    } else {
                        // EOF or no data -> break
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InterruptedException) {
                LOG.debug("Terminal input forwarding interrupted")
                Thread.currentThread().interrupt()
            } catch (e: IOException) {
                LOG.warn("Terminal input I/O exception", e)
            } finally {
                LOG.debug("Input forwarding coroutine finished")
            }
        }

        // When both are complete, complete forwardingFinished if not already
        // Use NonCancellable to ensure completion even if cancelled
        scope.launch(NonCancellable) {
            try {
                try {
                    outputJob.join()
                } catch (e: CancellationException) {
                    throw e
                }
                try {
                    inputJob.join()
                } catch (e: CancellationException) {
                    throw e
                }
            } finally {
                if (!forwardingFinished.isCompleted) {
                    forwardingFinished.complete(Unit)
                }
            }
        }
    }

    /**
     * Checks if the SSH connection and channel are active.
     * @return True if connected.
     */
    fun isConnected(): Boolean {
        val connected = connectedFlag && connection.isConnected() && channel.isConnected
        LOG.debug("isConnected() -> $connected") // Replaced println with LOG
        return connected
    }
}