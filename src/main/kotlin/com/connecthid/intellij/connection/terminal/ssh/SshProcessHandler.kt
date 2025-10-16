package com.connecthid.intellij.connection.terminal.ssh


import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nullable
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class SshProcessHandler(
    host: String,
    port: Int,
    user: String,
    password: String
) : ProcessHandler() {
    private companion object {
        private val LOG = Logger.getInstance(SshProcessHandler::class.java)
    }

    private val session: Session
    private val channel: ChannelShell
    private val processStdin: PipedOutputStream = PipedOutputStream()
    private lateinit var shellInput: PipedInputStream
    private val shellOut: Lazy<OutputStream> = lazy { channel.getOutputStream() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val inputForwardingActive = AtomicBoolean(true)
    private var commandDelayMs: Long = 200L

    init {
        val jsch = JSch()
        session = jsch.getSession(user, host, port).apply {
            setPassword(password)
            val config = Properties().apply { setProperty("StrictHostKeyChecking", "no") }  // Prod: Use "yes"
            setConfig(config)
            connect()
        }
        channel = session.openChannel("shell") as ChannelShell
        channel.setPty(true)  // Initial PTY allocation
    }

    // PTY Resizing: Send SSH window change signal (call from TtyConnector)
    fun resize(columns: Int, rows: Int) {
        if (channel.isConnected) {
            channel.setPtySize(columns, rows, 800, 600)
            LOG.debug("Resized PTY to ${columns}x${rows}")
        }
    }

    // Send command (suspend for async)
    suspend fun sendCommand(command: String) {
        if (!started.get() || !shellOut.isInitialized()) {
            throw IllegalStateException("Handler not started.")
        }
        withContext(Dispatchers.IO) {
            try {
                shellOut.value.write((command + "\n").toByteArray())
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

    override fun destroyProcessImpl() {
        inputForwardingActive.set(false)
        scope.cancel()
        runCatching {
            processStdin.close()
            if (!channel.isClosed) channel.disconnect()
            if (session.isConnected) session.disconnect()
        }.onFailure { LOG.warn("Cleanup error", it) }
        notifyProcessDetached()
    }

    override fun detachProcessImpl() = destroyProcessImpl()

    override fun detachIsDefault(): Boolean = false



    @Nullable
    override fun getProcessInput(): OutputStream? = processStdin

    override fun startNotify() {
        if (!started.compareAndSet(false, true)) return

        runCatching {
            shellInput = PipedInputStream(processStdin)
            setupForwarding()
            channel.connect()
        }.onFailure { notifyProcessTerminated(-1) }
    }

    private fun setupForwarding() {
        // Output forwarding: Shell -> Console (polling coroutine)
        scope.launch {
            val shellIn = channel.getInputStream()
            val buffer = ByteArray(1024)
            while (isActive && !channel.isClosed) {
                while (shellIn.available() > 0) {
                    val read = shellIn.read(buffer, 0, 1024)
                    if (read < 0) break
                    notifyTextAvailable(String(buffer, 0, read), ProcessOutputTypes.STDOUT)
                }
                delay(50)  // Polling delay
            }
            notifyProcessTerminated(channel.getExitStatus().takeIf { it != -1 } ?: 0)
        }

        // Input forwarding: Console -> Shell (coroutine)
        scope.launch {
            val buf = ByteArray(1024)
            while (inputForwardingActive.get() && coroutineContext.isActive) {
                val read = shellInput.read(buf)
                if (read > 0) {
                    shellOut.value.write(buf, 0, read)
                    shellOut.value.flush()
                } else break
            }
        }
    }

     fun isConnected(): Boolean {
        val connected = session.isConnected && channel.isConnected
        println("isConnected() -> $connected")
        return connected
    }
}
