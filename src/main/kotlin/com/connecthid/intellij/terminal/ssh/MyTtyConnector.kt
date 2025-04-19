package com.connecthid.intellij.terminal.ssh
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import java.io.BufferedReader
import java.io.InputStreamReader

class MyTtyConnector(
    private val process: Process
) : TtyConnector {

    private val inputReader = InputStreamReader(process.inputStream)
    private val bufferedInput = BufferedReader(inputReader)
    private val outputStream = process.outputStream

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        return bufferedInput.read(buffer, offset, length)
    }

    override fun write(bytes: ByteArray) {
        outputStream.write(bytes)
        outputStream.flush()
    }

    override fun write(text: String) {
        outputStream.write(text.toByteArray())
        outputStream.flush()
    }

    override fun isConnected(): Boolean {
        return process.isAlive
    }

    override fun waitFor(): Int {
        return process.waitFor()
    }

    override fun ready(): Boolean {
        return bufferedInput.ready()
    }

    override fun getName(): String {
        return "MyTtyConnector"
    }

    override fun close() {
        process.destroy()
    }
    override fun resize(termSize: TermSize) {

        println("Resizing terminal: ${termSize.columns} cols, ${termSize.rows} rows")

    }
}
