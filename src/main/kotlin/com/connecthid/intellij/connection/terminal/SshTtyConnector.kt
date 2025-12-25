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
import java.io.InputStreamReader
import java.io.OutputStream


open class SshTtyConnector(
    val server: Server,
    private val workingDir: String? = null,
    val interactive: Boolean = true
) : TtyConnector {


    lateinit var inputStream: InputStream
     lateinit var outputStream: OutputStream
    private var reader: InputStreamReader? = null
    private var isRunning = false
    val service = getSSHService()
    private var connectionPool: Pair<SSHConnection, Session>? = null
    private var connection: SSHJConnection? = null
    private var shell: Session.Shell?=null

    init {
        connect()
    }

    private fun connect() {
        connection = service.getConnection(server) ?: throw Exception("Unable to connect to server")
        connectionPool = connection!!.sshPool.borrowExecutionSession()
        connectionPool!!.second.allocateDefaultPTY()
        if (interactive) {
            shell = connectionPool!!.second.startShell()
        }

        inputStream = connectionPool!!.second.inputStream
        outputStream = connectionPool!!.second.outputStream
        reader = InputStreamReader(inputStream, Charsets.UTF_8)

        workingDir?.let {
            val cmd = "cd $it\n"
            outputStream.write(cmd.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
        isRunning = true
    }

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        if (!isRunning) return -1
        return reader?.read(buf, offset, length) ?: -1
    }

    override fun write(bytes: ByteArray) {
        if (isRunning) {
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    override fun write(str: String) {
        if (isRunning) {
            outputStream.write(str.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
    }

    override fun isConnected(): Boolean {
        return connectionPool?.first?.isAlive() == true && connectionPool?.second?.isOpen == true
    }

    override fun waitFor(): Int {
        while (isConnected) {
            Thread.sleep(100)
        }
        return 0
    }

    override fun ready(): Boolean {
        return reader?.ready() == true
    }

    override fun getName(): String {
        return "SSH-${server.username}@${server.host}"
    }

    override fun close() {
        isRunning = false
        runCatching { reader?.close() }
        runCatching {
            connection?.sshPool?.returnExecutionClient(connectionPool!!.first, connectionPool!!.second)
        }
    }

    override fun resize(termSize: TermSize) {
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
                reader = InputStreamReader(inputStream, Charsets.UTF_8)
                cmd.join()
                return Pair(cmd.getExitStatus(),cmd.exitErrorMessage)
            }
        }
        return Pair(255,"Invalid command-line usage")
    }
}
