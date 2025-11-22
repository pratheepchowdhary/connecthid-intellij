package com.connecthid.sshjpool

import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.scp.SCPFileTransfer
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

class SSHConnectionPool(
    host: String,
    username: String,
    password: String,
    maxTotalConnections: Int = 10,
    private val maxChannelsPerConnection: Int = 10
) {
    private val log = LoggerFactory.getLogger(SSHConnectionPool::class.java)

    private val factory = SSHConnectionFactory(host, username, password, maxChannelsPerConnection)
    private val pool = GenericObjectPool(factory, GenericObjectPoolConfig<SSHConnection>().apply {
        maxTotal = maxTotalConnections
        blockWhenExhausted = true
        minIdle = 1
        maxIdle = 3
        testOnBorrow = true
    })

    // Track borrowed connections manually
    private val borrowedConnections = ConcurrentLinkedQueue<SSHConnection>()

    @Synchronized
    fun borrowAvailableConnection(): SSHConnection {
        // Try to reuse an existing borrowed connection that has free channels
        borrowedConnections.forEach { conn ->
            if (!conn.isFull() && conn.isAlive()) {
                log.info("Reusing existing connection: ${conn.hashCode()}")
                return conn
            }
        }

        // Otherwise, borrow a new one from pool
        val newConn = pool.borrowObject()
        borrowedConnections.add(newConn)
        log.info("Created new SSH connection: ${newConn.hashCode()}")
        return newConn
    }

    fun borrowSftpClient(): Pair<SSHConnection, SFTPClient> {
        var connection = borrowAvailableConnection()
        var sftp = connection.createSftpClient()

        if (sftp == null) {
            // connection full → borrow a new one
            connection = pool.borrowObject()
            borrowedConnections.add(connection)
            sftp = connection.createSftpClient()
            log.info("All channels used, created new connection: ${connection.hashCode()}")
        }

        return Pair(connection, sftp!!)
    }

    fun returnSftpClient(connection: SSHConnection, sftp: SFTPClient) {
        connection.releaseChannel(sftp)
        // If connection has no active channels, return to pool
        if (connection.getActiveChannelCount() == 0) {
            borrowedConnections.remove(connection)
            pool.returnObject(connection)
            log.debug("Returned connection to pool: ${connection.hashCode()}")
        }
    }


    fun borrowSCPClient(): Pair<SSHConnection, SCPFileTransfer> {
        var connection = borrowAvailableConnection()
        var scp = connection.createSCPTransfer()

        if (scp == null) {
            // connection full → borrow a new one
            connection = pool.borrowObject()
            borrowedConnections.add(connection)
            scp = connection.createSCPTransfer()
            log.info("All channels used, created new connection: ${connection.hashCode()}")
        }

        return Pair(connection, scp!!)
    }

    fun returnSCPClient(connection: SSHConnection, scp: SCPFileTransfer) {
        connection.releaseChannel(scp)

        // If connection has no active channels, return to pool
        if (connection.getActiveChannelCount() == 0) {
            borrowedConnections.remove(connection)
            pool.returnObject(connection)
            log.debug("Returned connection to pool: ${connection.hashCode()}")
        }
    }


    fun borrowExecutionSession(): Pair<SSHConnection, Session> {
        var connection = borrowAvailableConnection()
        var session = connection.createExecSession()

        if (session == null) {
            // connection full → borrow a new one
            connection = pool.borrowObject()
            borrowedConnections.add(connection)
            session = connection.createExecSession()
            log.info("All channels used, created new connection: ${connection.hashCode()}")
        }

        return Pair(connection, session!!)
    }

    fun returnExecutionClient(connection: SSHConnection, session: Session) {
        connection.releaseChannel(session)

        // If connection has no active channels, return to pool
        if (connection.getActiveChannelCount() == 0) {
            borrowedConnections.remove(connection)
            pool.returnObject(connection)
            log.debug("Returned connection to pool: ${connection.hashCode()}")
        }
    }

    /**
     * Open SFTP client, execute block, then close SFTP client.
     * Use for put/get and other SFTP operations.
     */
    fun <T> withSftp(action: (SFTPClient) -> T): T {
        val sftp = borrowSftpClient()
        try {
            return action(sftp.second)
        } finally {
            try { returnSftpClient(sftp.first, sftp.second) } catch (_: Exception) {}
        }
    }

    /**
     * Open SFTP client, execute block, then close SFTP client.
     * Use for put/get and other SFTP operations.
     */
    fun <T> withExc(action: (Session) -> T): T {
        val session = borrowExecutionSession()
        try {
            return action(session.second)
        } finally {
            try { returnExecutionClient(session.first, session.second) } catch (_: Exception) {}
        }
    }

    /**
     * Open SFTP client, execute block, then close SFTP client.
     * Use for put/get and other SFTP operations.
     */
    fun <T> withScp(action: (SCPFileTransfer) -> T): T {
        val session = borrowSCPClient()
        try {
            return action(session.second)
        } finally {
            try { returnSCPClient(session.first, session.second) } catch (_: Exception) {}
        }
    }

    fun shutdown() {
        borrowedConnections.forEach { conn ->
            try {
                conn.close()
            } catch (_: Exception) { }
        }
        borrowedConnections.clear()
        pool.close()
    }

    fun isConnected(): Boolean{
        return pool.listAllObjects().size>0
    }
}
