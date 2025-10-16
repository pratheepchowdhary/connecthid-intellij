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
        processHandler.close()
    }

    override fun resize(@NotNull termSize: TermSize) {
        // JSch integration: Call custom resize if handler is ours
        processHandler.resize(termSize.columns, termSize.rows)
    }


    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        if (!processHandler.isConnected()) return -1
        val byteBuf = ByteArray(length)
        val bytesRead = processHandler.getShellChannel().inputStream.read(byteBuf)

        if (bytesRead == -1) return -1

        val str = byteBuf.decodeToString(0, bytesRead)
        val chars = str.toCharArray()
        val copyLength = chars.size.coerceAtMost(length)
        chars.copyInto(buf, offset, 0, copyLength)

        println("READ [$bytesRead bytes]: $str")
        return copyLength
    }

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



    override fun ready(): Boolean {
        val available = processHandler.getShellChannel().inputStream.available()
        println("ready() -> $available bytes available")
        return available > 0
    }

    override fun getName(): String {
        return "SSH-${processHandler.server.stmpName}"
    }

    private fun writeBytes(bytes: ByteArray) {
        processHandler.processInput.let { input ->
            input.write(bytes)
            input.flush()
        }
    }



}