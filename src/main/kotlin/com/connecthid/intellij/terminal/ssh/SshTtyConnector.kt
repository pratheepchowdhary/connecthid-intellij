package com.connecthid.intellij.terminal.ssh

import com.jcraft.jsch.*
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import java.io.InputStream
import java.io.OutputStream

class SshTtyConnector(
    private val host: String,
    private val port: Int = 22,
    private val user: String,
    private val password: String? = null,
    private val privateKeyPath: String? = null
) : TtyConnector {

    private lateinit var session: Session
    private lateinit var channel: ChannelShell
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    private var isRunning = false

    init {
        connect()
    }

    private fun connect() {
        println("Connecting to $user@$host:$port")

        val jsch = JSch()
        if (!privateKeyPath.isNullOrEmpty()) {
            println("Using private key at: $privateKeyPath")
            jsch.addIdentity(privateKeyPath)
        }

        session = jsch.getSession(user, host, port).apply {
            if (!password.isNullOrEmpty()) {
                setPassword(password)
            }
            setConfig("StrictHostKeyChecking", "no")
            connect(5000)
        }

        println("Session connected.")

        channel = session.openChannel("shell") as ChannelShell
        channel.setPty(true)
        channel.setPtyType("xterm-256color")
        inputStream = channel.inputStream
        outputStream = channel.outputStream

        channel.connect(3000)
        isRunning = true

        println("ChannelShell connected.")
    }

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        if (!isRunning) return -1
        val byteBuf = ByteArray(length)
        val bytesRead = inputStream.read(byteBuf)

        if (bytesRead == -1) return -1

        val str = byteBuf.decodeToString(0, bytesRead)
        val chars = str.toCharArray()
        val copyLength = chars.size.coerceAtMost(length)
        chars.copyInto(buf, offset, 0, copyLength)

        println("READ [$bytesRead bytes]: $str")
        return copyLength
    }

    override fun write(bytes: ByteArray) {
        if (isRunning) {
            val str = bytes.toString(Charsets.UTF_8)
            println("WRITE [bytes]: $str")
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    override fun write(str: String) {
        if (isRunning) {
            println("WRITE [string]: $str")
            outputStream.write(str.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
    }

    override fun isConnected(): Boolean {
        val connected = session.isConnected && channel.isConnected
        println("isConnected() -> $connected")
        return connected
    }

    override fun waitFor(): Int {
        println("waitFor() - waiting for session to close")
        while (isConnected) {
            Thread.sleep(100)
        }
        println("waitFor() - session closed")
        return 0
    }

    override fun ready(): Boolean {
        val available = inputStream.available()
        println("ready() -> $available bytes available")
        return available > 0
    }

    override fun getName(): String {
        return "SSH-$user@$host"
    }

    override fun close() {
        println("Closing SSH connection...")
        isRunning = false
        try {
            channel.disconnect()
            session.disconnect()
            println("SSH connection closed.")
        } catch (e: Exception) {
            println("Error closing SSH connection: ${e.message}")
        }
    }

    override fun resize(termSize: TermSize) {
        println("Resizing terminal: ${termSize.columns} cols, ${termSize.rows} rows")
        channel.setPtySize(termSize.columns, termSize.rows, 800, 600)
    }
}
