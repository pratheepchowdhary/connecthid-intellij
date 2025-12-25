package com.connecthid.intellij.connection.terminal

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

abstract class CProcessHandler: ProcessHandler() {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processStdin: PipedOutputStream = PipedOutputStream()
    protected lateinit var shellInput: PipedInputStream

    protected val inputForwardingActive = AtomicBoolean(true)
    private val forwardingFinished = CompletableDeferred<Unit>()

    abstract fun getProcessOutputStream(): OutputStream
    abstract fun getProcessInputStream(): InputStream
    abstract fun isConnected(): Boolean
    abstract fun disconnect()
    abstract fun executeCommand(command: String): Int

    override fun getProcessInput(): OutputStream = processStdin

    override fun startNotify() {
        super.startNotify()
        try {
            shellInput = PipedInputStream(processStdin)
            setupForwarding()
            onStart()
        } catch (e: Exception) {
            notifyProcessTerminated(-1)
        }
    }

    open fun onStart() {}

    private fun setupForwarding() {
        val outputJob = scope.launch {
            val buffer = ByteArray(8192)
            val stream = getProcessInputStream()
            try {
                while (isConnected() && isActive) {
                    ensureActive()
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (n > 0) {
                        notifyTextAvailable(String(buffer, 0, n, StandardCharsets.UTF_8), ProcessOutputTypes.STDOUT)
                    }
                }
            } catch (e: IOException) {
                // Expected on disconnect
            }
        }

        val inputJob = scope.launch {
            val buffer = ByteArray(1024)
            val remoteOut = getProcessOutputStream()
            try {
                while (inputForwardingActive.get() && isActive) {
                    ensureActive()
                    val n = shellInput.read(buffer)
                    if (n < 0) break
                    if (n > 0) {
                        remoteOut.write(buffer, 0, n)
                        remoteOut.flush()
                    }
                }
            } catch (e: IOException) {
                // Expected on disconnect
            }
        }

        scope.launch(NonCancellable) {
            outputJob.join()
            inputJob.join()
            forwardingFinished.complete(Unit)
        }
    }

    override fun destroyProcessImpl() {
        inputForwardingActive.set(false)
        scope.cancel()
        try {
            runBlocking {
                withTimeoutOrNull(2000.milliseconds) {
                    forwardingFinished.await()
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        notifyProcessDetached()
        runCatching { processStdin.close() }
        disconnect()
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        destroyProcessImpl()
    }

    override fun detachIsDefault(): Boolean = false
}