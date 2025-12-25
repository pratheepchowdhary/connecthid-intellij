package com.connecthid.intellij.connection.terminal

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import kotlinx.coroutines.*
import kotlinx.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds

class PtyProcessHandler(
    private val command: List<String>,
    private val workingDir: String,
    private val env: Map<String, String>
) : ProcessHandler() {

    private lateinit var pty: PtyProcess
    lateinit var connector: LocalPtyConnector

    private val stdinPipe = PipedOutputStream()
    private lateinit var stdinInput: PipedInputStream

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val finished = CompletableDeferred<Unit>()

    override fun startNotify() {
        super.startNotify()

        pty = PtyProcessBuilder()
            .setCommand(command.toTypedArray())
            .setDirectory(workingDir)
            .setEnvironment(env)
            .start()

        connector = LocalPtyConnector(pty)
        stdinInput = PipedInputStream(stdinPipe)

        setupForwarding()
    }

    override fun getProcessInput() = stdinPipe

    override fun destroyProcessImpl() {
        scope.cancel()
        runCatching { pty.destroy() }
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        destroyProcessImpl()
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = false
    override fun isProcessTerminated(): Boolean = !pty.isAlive

    private fun setupForwarding() {
        val outputJob = scope.launch {
            val input = pty.inputStream
            val buffer = ByteArray(4096)
            try {
                while (isActive && pty.isAlive) {
                    val read = withTimeout(20.milliseconds) {
                        input.read(buffer)
                    }
                    if (read <= 0) break
                    notifyTextAvailable(
                        String(buffer, 0, read, StandardCharsets.UTF_8),
                        ProcessOutputTypes.STDOUT
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
            }
        }

        val inputJob = scope.launch {
            val buffer = ByteArray(1024)
            try {
                while (isActive && pty.isAlive) {
                    val read = stdinInput.read(buffer)
                    if (read <= 0) break
                    pty.outputStream.write(buffer, 0, read)
                    pty.outputStream.flush()
                }
            } catch (_: Exception) {}
        }

        scope.launch {
            outputJob.join()
            inputJob.join()
            if (!finished.isCompleted)
                finished.complete(Unit)
        }
    }
}
