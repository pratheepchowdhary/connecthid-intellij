package com.connecthid.intellij.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.jcraft.jsch.Session
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

enum class AuthenticationMethod {
    PASSWORD,
    PRIVATE_KEY
}

data class ServerConnection(
    val host: String,
    val username: String,
    val port: Int = 22,
    val authMethod: AuthenticationMethod = AuthenticationMethod.PASSWORD,
    val privateKeyPath: String? = null
)

data class ServerConnectionState(
    var connections: MutableList<ServerConnection> = mutableListOf()
)

@State(name = "ServerConnectionService", storages = [Storage("server-connections.xml")])
@Service(Service.Level.PROJECT)
class ServerConnectionService(private val project: Project) : PersistentStateComponent<ServerConnectionState> {
    private val connections = ConcurrentHashMap<String, SSHConnection>()
    private var state = ServerConnectionState()

    override fun getState(): ServerConnectionState = state

    override fun loadState(state: ServerConnectionState) {
        this.state = state
    }

    fun addServerConnection(
        host: String,
        username: String,
        port: Int = 22,
        authMethod: AuthenticationMethod = AuthenticationMethod.PASSWORD,
        privateKeyPath: String? = null
    ) {
        if (!state.connections.any { it.host == host }) {
            state.connections.add(ServerConnection(host, username, port, authMethod, privateKeyPath))
        }
    }

    fun removeServerConnection(host: String) {
        state.connections.removeIf { it.host == host }
    }

    fun getSavedConnections(): List<ServerConnection> {
        return state.connections.toList()
    }

    fun connect(
        host: String,
        username: String,
        password: String? = null,
        port: Int = 22,
        privateKeyPath: String? = null
    ): Boolean {
        try {
            val connection = SSHConnection(host, username, password, port, privateKeyPath)
            connection.connect()
            connections[host] = connection
            addServerConnection(host, username, port, 
                if (privateKeyPath != null) AuthenticationMethod.PRIVATE_KEY else AuthenticationMethod.PASSWORD,
                privateKeyPath
            )
            return true
        } catch (e: IOException) {
            return false
        }
    }

    fun disconnect(host: String) {
        connections[host]?.close()
        connections.remove(host)
    }

    fun isConnected(host: String): Boolean {
        return connections[host]?.isConnected() ?: false
    }

    fun checkConnection(host: String): Boolean {
        // First try SSH connection if it exists
        val sshConnection = connections[host]
        if (sshConnection != null && sshConnection.isConnected()) {
            return true
        }

        // Fallback to basic TCP connection check
        return try {
            val socket = Socket(host, 22)
            socket.close()
            true
        } catch (e: IOException) {
            false
        }
    }

    fun getConnection(host: String): SSHConnection? {
        return connections[host]
    }

    fun getSession(host: String): Session? {
        return connections[host]?.getSession()
    }

    fun executeCommand(host: String, command: String): String? {
        return try {
            val connection = connections[host] ?: return null
            connection.execute(command)
        } catch (e: IOException) {
            null
        }
    }

    fun uploadFile(host: String, localPath: String, remotePath: String): Boolean {
        return try {
            val connection = connections[host] ?: return false
            connection.uploadFile(localPath, remotePath)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun downloadFile(host: String, remotePath: String, localPath: String): Boolean {
        return try {
            val connection = connections[host] ?: return false
            connection.downloadFile(remotePath, localPath)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun getConnectedServers(): List<String> {
        return connections.keys.toList()
    }
} 