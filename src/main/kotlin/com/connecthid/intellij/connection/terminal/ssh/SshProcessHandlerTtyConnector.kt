package com.connecthid.intellij.connection.terminal.ssh
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import org.jetbrains.annotations.NotNull
import java.io.IOException
import java.nio.charset.Charset

class SshProcessHandlerTtyConnector(
     private val processHandler: SshProcessHandler,
     private val charset: Charset
) : TtyConnector {

    override fun close() {
        // No-op: Handler manages lifecycle
    }

    override fun resize(@NotNull termSize: TermSize) {
        // JSch integration: Call custom resize if handler is ours
        processHandler.resize(termSize.columns, termSize.rows)
    }



    @Throws(IOException::class)
    override fun read(buf: CharArray, offset: Int, length: Int): Int =
        throw IllegalStateException("Reads handled by ProcessHandler")

    override fun write(bytes: ByteArray) = writeBytes(bytes)

    override fun isConnected(): Boolean = processHandler.isConnected()

    @Throws(IOException::class)
    override fun write(string: String) = writeBytes(string.toByteArray(charset))



    override fun waitFor(): Int {
        println("waitFor() - waiting for session to close")
        while (!processHandler.isConnected()) {
            Thread.sleep(100)
        }
        println("waitFor() - session closed")
        return 0
    }

    override fun ready(): Boolean = false
    override fun getName(): String {
        return return ""
    }

    private fun writeBytes(bytes: ByteArray) {
        processHandler.processInput?.let { input ->
            input.write(bytes)
            input.flush()
        }
    }



}