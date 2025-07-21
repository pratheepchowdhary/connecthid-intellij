package com.connecthid.intellij.services

import com.connecthid.intellij.models.AuthenticationMethod
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.SystemInfo
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.jcraft.jsch.Session
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap


data class ServerConnectionState(
    var connections: MutableList<Server> = mutableListOf()
)

@State(name = "ServerConnectionService", storages = [Storage("server-connections.xml")])
class ServerConnectionService() : PersistentStateComponent<ServerConnectionState> {
    private val connections = ConcurrentHashMap<String, SSHConnection>()
    private var state = ServerConnectionState()
    var searchServers = arrayListOf<Server>()

    override fun getState(): ServerConnectionState = state

    override fun loadState(state: ServerConnectionState) {
        this.state = state
    }

    fun addServerConnection(
        host: String,
        username: String,
        port: Int = 22,
        authMethod: AuthenticationMethod = AuthenticationMethod.PASSWORD,
        password: String? = null,
        privateKeyPath: String? = null
    ) {
        if (!state.connections.any { it.host == host && it.username == username }) {
            val connection = Server(host=host, username=username, port=port, authMethod=authMethod, privateKeyPath=privateKeyPath)
            state.connections.add(connection)
            updateSystemInfo(connection)
            connection.password=password
        }
    }

    private fun updateSystemInfo(server: Server) {
        val sshConnection = connections["${server.username}@${server.host}"] ?: return
        try {
            // Get OS information
            val osInfo = sshConnection.execute("cat /etc/os-release").split("\n")
                .associate { line -> 
                    val parts = line.split("=")
                    if (parts.size == 2) parts[0] to parts[1].trim('"') else "" to ""
                }
             
             val hostName = sshConnection.execute("hostname")
            
            
            // Get CPU information
            val cpuInfo = sshConnection.execute("cat /proc/cpuinfo | grep 'model name' | head -n1")
                .substringAfter(":").trim()
           val architecture = sshConnection.execute("uname -m")

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
                architecture = architecture,
                totalRam = totalRam,
                usedRam = usedRam,
                totalStorage = totalStorage,
                usedStorage = usedStorage,
                hostName = hostName ?: ""
            )
            // Find and update the connection in state
            val index = state.connections.indexOfFirst { it.host == server.host && it.username.equals(server.username) }
            if (index != -1) {
                state.connections[index] = server.copy(systemInfo = systemInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Handle error silently - system info will remain empty
        }
    }

    fun removeServerConnection(host: String,username: String) {
        state.connections.removeIf { it.host == host }
        // Trigger state change to ensure persistence
        state = state.copy(connections = state.connections.toMutableList())
    }

    fun getSavedConnections(): List<Server> {
        return state.connections.toList()
    }
    fun isValidSshConnection(
        host: String,
        username: String,
        password: String? = null,
        port: Int = 22,
        privateKeyPath: String? = null
    ): Boolean {

        try {
            val connection = SSHConnection(host, username, password, port, privateKeyPath)
            connection.connect()
            return true
        } catch (e: Exception) {
            return false
        }
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
            connections["$username@${host}"] = connection
            
            // Add or update server connection
            val existingConnection = state.connections.find { it.host.equals(host) && it.username.equals(username) }
            if (existingConnection != null) {
                val index = state.connections.indexOf(existingConnection)
                state.connections[index] = existingConnection.copy(
                    username = username,
                    port = port,
                    authMethod = if (privateKeyPath != null) AuthenticationMethod.PRIVATE_KEY else AuthenticationMethod.PASSWORD,
                    privateKeyPath = privateKeyPath
                )
                existingConnection.password=password
                updateSystemInfo(state.connections[index])
            } else {
                addServerConnection(host, username, port, 
                    if (privateKeyPath != null) AuthenticationMethod.PRIVATE_KEY else AuthenticationMethod.PASSWORD,
                    password,
                    privateKeyPath
                )
            }
            
            return true
        } catch (e: IOException) {
            return false
        }
    }

    fun disconnect(host: String,username: String) {
        connections["$username@${host}"]?.close()
        connections.remove("$username@${host}")
    }

    fun isConnected(host: String,username: String): Boolean {
        return connections["$username@${host}"]?.isConnected() ?: false
    }

    fun checkConnection(host: String,username: String): Boolean {
        // First try SSH connection if it exists
        val sshConnection = connections["$username@${host}"]
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

    fun getConnection(host: String,username: String): SSHConnection? {
        return connections["$username@${host}"]
    }

    fun getConnection(userAtHost: String): SSHConnection? {
        return connections[userAtHost]
    }

    fun getSession(host: String,username: String): Session? {
        return connections["$username@${host}"]?.getSession()
    }

    fun executeCommand(host: String,username: String, command: String): String? {
        return try {
            val connection = connections["$username@${host}"] ?: return null
            connection.execute(command)
        } catch (e: IOException) {
            null
        }
    }

    fun uploadFile(host: String,username: String, localPath: String, remotePath: String): Boolean {
        return try {
            val connection = connections["$username@${host}"] ?: return false
            connection.uploadFile(localPath, remotePath)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun downloadFile(host: String,username: String, remotePath: String, localPath: String): Boolean {
        return try {
            val connection = connections["$username@${host}"] ?: return false
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