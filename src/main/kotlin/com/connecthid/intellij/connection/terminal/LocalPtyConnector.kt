package com.connecthid.intellij.connection.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.io.BufferedOutputStream
import java.io.InputStreamReader

class LocalPtyConnector(
    private val pty: PtyProcess
) : TtyConnector {

    private val reader = InputStreamReader(pty.inputStream, Charsets.UTF_8)
    private val output = BufferedOutputStream(pty.outputStream)

    /**
     * Read characters from PTY
     */
    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        if (!pty.isAlive) return -1
        return reader.read(buf, offset, length)
    }

    /**
     * Write byte array
     */
    override fun write(bytes: ByteArray) {
        if (pty.isAlive) {
            output.write(bytes)
            output.flush()
        }
    }

    /**
     * Write string -> UTF-8 bytes
     */
    override fun write(str: String?) {
        if (str != null && pty.isAlive) {
            output.write(str.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    override fun isConnected(): Boolean = try {
        pty.isAlive
    } catch (e: Exception) {
        false
    }

    override fun waitFor(): Int {
        try {
            return pty.waitFor()
        } catch (e: InterruptedException) {
            return -1
        }
    }

    override fun ready(): Boolean {
        return reader.ready()
    }

    override fun getName(): String = "Local PTY"

    override fun close() {
        runCatching { reader.close() }
        runCatching { output.close() }
        runCatching { pty.destroy() }
    }

    /**
     * Resize the terminal (columns × rows)
     */
    override fun resize(termSize: TermSize) {
        try {
            pty.setWinSize(WinSize(termSize.columns, termSize.rows))
        } catch (_: Exception) {}
    }
}
