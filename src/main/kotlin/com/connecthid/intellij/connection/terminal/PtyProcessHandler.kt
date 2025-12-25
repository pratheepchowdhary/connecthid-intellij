package com.connecthid.intellij.connection.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class PtyProcessHandler(
    private val command: List<String>,
    private val workingDir: String,
    private val env: Map<String, String>
) : CProcessHandler() {

    private lateinit var pty: PtyProcess
    lateinit var connector: LocalPtyConnector

    override fun startNotify() {
        pty = PtyProcessBuilder()
            .setCommand(command.toTypedArray())
            .setDirectory(workingDir)
            .setEnvironment(env)
            .start()

        connector = LocalPtyConnector(pty)
        super.startNotify()
    }

    override fun getProcessOutputStream(): OutputStream = pty.outputStream
    override fun getProcessInputStream(): InputStream = pty.inputStream
    override fun isConnected(): Boolean = pty.isAlive

    override fun disconnect() {
        runCatching { pty.destroy() }
    }

    override fun executeCommand(command: String): Int {
        return try {
            val cmd = if (command.endsWith("\n")) command else "$command\n"
            pty.outputStream.write(cmd.toByteArray(StandardCharsets.UTF_8))
            pty.outputStream.flush()
            0
        } catch (e: Exception) {
            -1
        }
    }

    override fun isProcessTerminated(): Boolean = !pty.isAlive
}
