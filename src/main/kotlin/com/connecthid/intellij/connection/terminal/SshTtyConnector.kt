package com.connecthid.intellij.connection.terminal

import com.connecthid.intellij.connection.ssh.SSHJConnection
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.sshjpool.SSHConnection
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections


open class SshTtyConnector(
    val server: Server,
    private val workingDir: String? = null,
    val interactive: Boolean = true
) : TtyConnector {


    lateinit var inputStream: InputStream
     lateinit var outputStream: OutputStream
    private var isRunning = false
    val service = getSSHService()
    private var connectionPool: Pair<SSHConnection, Session>? = null
    private var connection: SSHJConnection? = null
    private var shell: Session.Shell?=null

    init {
        connect()
    }

    private fun connect() {
        println("Connecting to ${server.username}@${server.host}:${server.port}")
        connection = service.getConnection(server) ?: throw Exception("Unable to connect to server")
        connectionPool = connection!!.sshPool.borrowExecutionSession()
        connectionPool!!.second.allocateDefaultPTY()
        if (interactive) {
            shell = connectionPool!!.second.startShell()
        }

        inputStream = connectionPool!!.second.inputStream
        outputStream = connectionPool!!.second.outputStream
        workingDir?.let {
            val cmd = "cd $it\n"
            outputStream.write(cmd.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            println("Sent working directory command: $cmd")
        }
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
        val connected = connectionPool?.first?.isAlive()?:false && connectionPool?.second?.isOpen?:false
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
        return "SSH-${server.username}@${server.host}"
    }

    override fun close() {
        println("Closing SSH connection...")
        isRunning = false
        try {
            connection?.sshPool?.returnExecutionClient(connectionPool!!.first, connectionPool!!.second)
            println("SSH connection closed.")
        } catch (e: Exception) {
            println("Error closing SSH connection: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun resize(termSize: TermSize) {
        println("Resizing terminal: ${termSize.columns} cols, ${termSize.rows} rows")
        shell?.changeWindowDimensions(termSize.columns, termSize.rows, 800, 600)
        if(shell == null && connectionPool!!.second is SessionChannel){
            (connectionPool!!.second as SessionChannel).changeWindowDimensions(termSize.columns, termSize.rows, 800, 600)
        }
    }
    fun sendCommand(command: String) : Pair<Int, String>{
        if (isConnected()) {
            if (interactive) {
                outputStream.write((command + "\n").toByteArray())
                outputStream.flush() // make sure command is sent immediately
                return Pair(0,"")
            } else {
                val cmd = connectionPool!!.second.exec(command)
                cmd.autoExpand = true
                inputStream = cmd.inputStream
                outputStream = cmd.outputStream
                cmd.join()
                return Pair(cmd.getExitStatus(),cmd.exitErrorMessage)
            }
        }
        return Pair(255,"Invalid command-line usage")
    }
}

