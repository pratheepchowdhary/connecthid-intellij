package com.connecthid.intellij.connection.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

class LocalPtyConnector(
    private val pty: PtyProcess
) : TtyConnector {

    private val input = BufferedInputStream(pty.inputStream)
    private val output = BufferedOutputStream(pty.outputStream)

    /**
     * Read characters from PTY
     */
    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        if (!pty.isAlive) return -1
        val byteBuf = ByteArray(length)
        val bytesRead = input.read(byteBuf)

        if (bytesRead == -1) return -1

        val str = byteBuf.decodeToString(0, bytesRead)
        val chars = str.toCharArray()
        val copyLength = chars.size.coerceAtMost(length)
        chars.copyInto(buf, offset, 0, copyLength)
        println("READ [$bytesRead bytes]: $str")
        return copyLength
    }

    /**
     * Write byte array
     */
    override fun write(bytes: ByteArray) {
        if (pty.isAlive)  {
            val str = bytes.toString(Charsets.UTF_8)
            println("WRITE [bytes]: $str")
            output.write(bytes)
            output.flush()
        }
    }


    /**
     * Write string -> UTF-8 bytes
     */
    override fun write(str: String?) {
        if (!pty.isAlive) return
        println("Writing to PTY: $str")
        if (str == null) return
        output.write(str.toByteArray(Charsets.UTF_8))
        output.flush()
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
        return input.available() > 0
    }

    override fun getName(): String = "Local PTY"

    override fun close() {
        try {
            input.close()
        } catch (_: Exception) {}
        try {
            output.close()
        } catch (_: Exception) {}
        try {
            pty.destroy()
        } catch (_: Exception) {}
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
