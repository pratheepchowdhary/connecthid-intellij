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

data class SystemInfo(
    val osName: String = "",
    val osVersion: String = "",
    val cpuType: String = "",
    val totalRam: String = "",
    val usedRam: String = "",
    val totalStorage: String = "",
    val usedStorage: String = ""
)

data class ServerConnection(
    val host: String,
    val username: String,
    val port: Int = 22,
    val authMethod: AuthenticationMethod = AuthenticationMethod.PASSWORD,
    val privateKeyPath: String? = null,
    var systemInfo: SystemInfo = SystemInfo()
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
            val connection = ServerConnection(host, username, port, authMethod, privateKeyPath)
            state.connections.add(connection)
            updateSystemInfo(connection)
        }
    }

    private fun updateSystemInfo(server: ServerConnection) {
        val sshConnection = connections[server.host] ?: return
        try {
            // Get OS information
            val osInfo = sshConnection.execute("cat /etc/os-release").split("\n")
                .associate { line -> 
                    val parts = line.split("=")
                    if (parts.size == 2) parts[0] to parts[1].trim('"') else "" to ""
                }
            
            // Get CPU information
            val cpuInfo = sshConnection.execute("cat /proc/cpuinfo | grep 'model name' | head -n1")
                .substringAfter(":").trim()

            // Get RAM information
            val memInfo = sshConnection.execute("free -h").split("\n")[1]
                .split("\\s+".toRegex())
            val totalRam = memInfo[1]
            val usedRam = memInfo[2]

            // Get Storage information
            val dfOutput = sshConnection.execute("df -h /").split("\n")[1]
                .split("\\s+".toRegex())
            val totalStorage = dfOutput[1]
            val usedStorage = dfOutput[2]

            // Update system info
            val systemInfo = SystemInfo(
                osName = osInfo["NAME"] ?: "",
                osVersion = osInfo["VERSION_ID"] ?: "",
                cpuType = cpuInfo,
                totalRam = totalRam,
                usedRam = usedRam,
                totalStorage = totalStorage,
                usedStorage = usedStorage
            )

            // Find and update the connection in state
            val index = state.connections.indexOfFirst { it.host == server.host }
            if (index != -1) {
                state.connections[index] = server.copy(systemInfo = systemInfo)
            }
        } catch (e: Exception) {
            // Handle error silently - system info will remain empty
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
            
            // Add or update server connection
            val existingConnection = state.connections.find { it.host == host }
            if (existingConnection != null) {
                val index = state.connections.indexOf(existingConnection)
                state.connections[index] = existingConnection.copy(
                    username = username,
                    port = port,
                    authMethod = if (privateKeyPath != null) AuthenticationMethod.PRIVATE_KEY else AuthenticationMethod.PASSWORD,
                    privateKeyPath = privateKeyPath
                )
                updateSystemInfo(state.connections[index])
            } else {
                addServerConnection(host, username, port, 
                    if (privateKeyPath != null) AuthenticationMethod.PRIVATE_KEY else AuthenticationMethod.PASSWORD,
                    privateKeyPath
                )
            }
            
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