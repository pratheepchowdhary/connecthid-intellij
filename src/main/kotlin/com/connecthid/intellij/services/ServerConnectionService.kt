package com.connecthid.intellij.services

import com.connecthid.intellij.connection.ssh.SSHConnection
import com.connecthid.intellij.models.*
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock


data class ServerConnectionState(
    var connections: MutableList<Server> = mutableListOf(),
    var workspaces: MutableList<Workspace> = mutableListOf(),
    var taskModels: MutableList<TaskModel> = mutableListOf()
)

@State(name = "ServerConnectionService", storages = [Storage("server-connections.xml")])
class ServerConnectionService() : PersistentStateComponent<ServerConnectionState> {
    private val connections = ConcurrentHashMap<String, SSHConnection>()
    private var state = ServerConnectionState()
    var searchServers = arrayListOf<Server>()
    private val connectionLock = ReentrantLock()

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
            val connection = Server(
                host = host,
                username = username,
                port = port,
                authMethod = authMethod,
                privateKeyPath = privateKeyPath
            )
            state.connections.add(connection)
            updateSystemInfo(connection)
            connection.setPassword(password)
        }
    }

    fun addWorkspace(server: String, path: String, folderName: String) {
        if (!state.workspaces.any { it.server == server && it.path == path }) {
            val workspace = Workspace(server = server, path = path, folderName = folderName)
            state.workspaces.add(workspace)
            saveState()
        }
    }

    fun removeWorkspace(server: String, path: String) {
        state.workspaces.removeIf { it.server == server && it.path == path }
        saveState()
    }

    fun getWorkspaces(): List<Workspace> {
        return state.workspaces.toList()
    }

    fun addScript(taskModel: TaskModel){
        if (!state.taskModels.any { it.scriptId.equals(taskModel.scriptId) }) {
            state.taskModels.add(taskModel)
            saveState()
        } else {
            state.taskModels.removeIf { it.scriptId.equals(taskModel.scriptId)}
            state.taskModels.add(taskModel)
            saveState()
        }
    }

    fun getScripts(): List<TaskModel>{
        return state.taskModels.toList()
    }

    fun removeScript(taskModel: TaskModel){
        state.taskModels.remove(taskModel)
        saveState()
    }

    fun removeScript(scriptId: String){
        state.taskModels.removeIf { it.scriptId.equals(scriptId) }
        saveState()
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
                hostName = hostName
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

    fun removeServerConnection(host: String, username: String) {
        state.connections.removeIf { it.host == host }
        // Trigger state change to ensure persistence
        saveState()
    }

    fun saveState() {
        // Trigger state change to ensure persistence
        state =
            state.copy(connections = state.connections.toMutableList(), workspaces = state.workspaces.toMutableList())
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
                existingConnection.setPassword(password)
                updateSystemInfo(state.connections[index])
            } else {
                addServerConnection(
                    host, username, port,
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

    fun disconnect(host: String, username: String) {
        connections["$username@${host}"]?.close()
        connections.remove("$username@${host}")
    }

    fun isConnected(host: String, username: String): Boolean {
        return connections["$username@${host}"]?.isConnected() ?: false
    }

    fun checkConnection(host: String, username: String): Boolean {
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

    private fun getConnection(host: String, username: String): SSHConnection? {
        return connections["$username@${host}"]
    }

    fun getServer(server: String): Server? {
        return state.connections.firstOrNull() { it.stmpName.equals(server) }
    }

    fun getConnection(userAtHost: String): SSHConnection? {
        return connections[userAtHost]
    }


    fun getConnection(server: Server): SSHConnection? {
        connectionLock.lock()
        try {
            var connection = getConnection(server.host, server.username)
            if (connection == null || !connection.isConnected()) {
                connect(server.host, server.username, server.getPassword(), port = server.port)
                connection = getConnection(server.host, server.username)
            }
            return connection

        } finally {
            connectionLock.unlock()
        }
    }
}
