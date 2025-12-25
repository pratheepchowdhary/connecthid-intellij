package com.connecthid.intellij.connection.terminal

import com.connecthid.intellij.models.Server
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
 class SshProcessHandler(
    val server: Server,
    val interactive: Boolean = true
) : ProcessHandler() {

    private companion object {
        private val LOG = Logger.getInstance(SshProcessHandler::class.java)
    }

    private val processStdin: PipedOutputStream = PipedOutputStream()
    private lateinit var shellInput: PipedInputStream
    val shellOut: Lazy<OutputStream> = lazy { sshTtyConnector.outputStream }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inputForwardingActive = AtomicBoolean(true)
    var commandDelayMs: Long = 200L
    private var outputPollDelayMs: Long = 20L // Configurable polling delay for better responsiveness

    // quick connected flag to be used by isConnected()
    @Volatile
    private var connectedFlag = true

    // signal when forwarding coroutines are finished
    private val forwardingFinished = CompletableDeferred<Unit>()

    var exitStatus = 0
    val sshTtyConnector: SshTtyConnector
    var generalCommandLine:GeneralCommandLine ?=null




    init {
        LOG.debug("SSH channel connected for ${server.host}")
        sshTtyConnector = SshTtyConnector(server, interactive = interactive)
    }

    /**
     * Sends a command to the remote shell with a configurable post-send delay for execution pacing.
     * Must be called after startNotify().
     * @param command The command to send.
     */

    fun sendCommand(command: String): Int {
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
            exitStatus.takeIf { it != -1 } ?: 0
        } catch (e: Exception) {
            LOG.warn("Failed to execute command '$command'", e)
            notifyTextAvailable("Error executing command: ${e.message}\n", ProcessOutputTypes.STDERR)
            exitStatus = -1
            exitStatus// Return error code
        } finally {
            // Optional: Auto-destroy after command if desired (uncomment if caller doesn't manage lifecycle)
            if(!interactive){
                if(exitStatus !=0 && exitMessage.isNotEmpty() && !exitMessage.equals("null")){
                    notifyTextAvailable("${RED} ${exitMessage}\n", ProcessOutputTypes.STDERR)
                }
                destroyProcess()
            }
        }
    }

    /**
     * Sets the polling delay for output forwarding (in ms). Lower values increase responsiveness but CPU usage.
     */
    fun setOutputPollDelayMs(delayMs: Long) {
        outputPollDelayMs = delayMs
    }

    // UPDATED: destroyProcessImpl checks flag to avoid double notify
    override fun destroyProcessImpl() {
        LOG.warn("destroyProcessImpl stopping terminal")
        connectedFlag = false
        inputForwardingActive.set(false)
        scope.cancel()
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

        runCatching { processStdin.close() }.onFailure { LOG.warn("Error closing stdin", it) }

        runCatching {
            sshTtyConnector.close()
        }.onFailure { LOG.warn("Cleanup error", it) }

        LOG.debug("Channel exit status: $exitStatus")
        destroyProcess()
        notifyProcessTerminated(exitStatus)
    }

    override fun detachProcessImpl() {
        // reuse destroy path
        destroyProcessImpl()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream = processStdin



    override fun startNotify() {
        super.startNotify()
        runCatching {
            // connect PipedInputStream to the PipedOutputStream used as process stdin
            shellInput = PipedInputStream(processStdin)
            setupForwarding()
            // NOTE: assume providedChannel is already connected by caller if provided.
            // If not provided and channel came from pool, caller should ensure it's connected (or you can call channel.connect())
            generalCommandLine?.let {
                sendCommand(it.commandLineString)
            }

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
            val shellIn = sshTtyConnector.inputStream
            val buffer = ByteArray(4096) // Larger buffer for better throughput
            try {
                while (sshTtyConnector.isConnected && isActive) {
                    ensureActive()
                    val read = withTimeout(outputPollDelayMs) {
                        try {
                            shellIn.read(buffer)
                        } catch (e: IOException) {
                            -1
                        }
                    } // No data, retry
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

        // Input: Console -> Shell // FIXED: Added active check inside loop for quicker exit
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



}