package com.connecthid.intellij.connection.terminal

import com.connecthid.intellij.models.TaskModel
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.io.InputStream
import java.io.OutputStream

class PtyProcessHandler(
    command: List<String>,
    workingDir: String,
    env: Map<String, String>
) : ProcessHandler() {

    private var pty: PtyProcess

    init {
        pty = PtyProcessBuilder()
            .setCommand(command.toTypedArray())
            .setDirectory(workingDir)
            .setEnvironment(env)
            .start()

        connector = LocalPtyConnector(pty)
    }
    override fun getProcessOutputStream(): OutputStream = pty.outputStream
    override fun getProcessInputStream(): InputStream = pty.inputStream

    override fun executeCommand(command: String, scriptFile: TaskModel): Int {
        return try {
            val cmd = if (command.endsWith("\n")) command else "$command\n"
            if (connector.isConnected) {
                connector.write(cmd)
                0
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    override fun isProcessTerminated(): Boolean = !pty.isAlive
}
