package com.connecthid.sshjpool

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.scp.SCPFileTransfer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents one live SSH connection that can host multiple channels.
 */
class SSHConnection(
    val host: String,
    val username: String,
    val password: String,
    val maxChannels: Int = 10
) {
    private val log = LoggerFactory.getLogger(SSHConnection::class.java)



    private val sshClient = SSHClient()
    private val activeChannels = ConcurrentLinkedQueue<Any>() // holds SFTPClient or Session

    init {
        sshClient.addHostKeyVerifier(PromiscuousVerifier())
        sshClient.connect(host)
        sshClient.authPassword(username, password)
        log.info("Connected to $host")
    }

    fun createSftpClient(): SFTPClient? {
        synchronized(this) {
            log.info("Creating new Sftp session to $host")
//            Thread.dumpStack()
            if (activeChannels.size >= maxChannels) return null
            val client = sshClient.newSFTPClient()
            activeChannels.add(client)
            return client
        }
    }

    fun createExecSession(): Session? {
        synchronized(this) {
            log.info("Creating new Exec session to $host")
            if (activeChannels.size >= maxChannels) return null
            val session = sshClient.startSession()
            activeChannels.add(session)
            return session
        }
    }


    fun createSCPTransfer(): SCPFileTransfer? {
        synchronized(this) {
            log.info("Creating new SCP session to $host")
            if (activeChannels.size >= maxChannels) return null
            val scpTransfer = sshClient.newSCPFileTransfer()
            activeChannels.add(scpTransfer)
            return scpTransfer
        }
    }

    fun releaseChannel(channel: Any) {
        synchronized(this) {
            try {
                when (channel) {
                    is SFTPClient -> channel.close()
                    is Session -> channel.close()
                }
            } catch (_: Exception) { }
            activeChannels.remove(channel)
        }
    }

    fun getActiveChannelCount(): Int = activeChannels.size

    fun isFull(): Boolean = activeChannels.size >= maxChannels

    fun isAlive(): Boolean = sshClient.isConnected && sshClient.isAuthenticated

    fun close() {
        log.info("Closing SSH connection to $host")
        activeChannels.forEach { releaseChannel(it) }
        sshClient.disconnect()
    }
}
