package com.connecthid.intellij.services

import com.connecthid.intellij.connection.ssh.SSHJConnection
import com.connecthid.intellij.connection.ssh.system.Os
import com.connecthid.intellij.connection.ssh.system.getOs
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
    private val connections = ConcurrentHashMap<String, SSHJConnection>()
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

    fun getTasks(): List<TaskModel>{
        return state.taskModels.toList().sortedByDescending { it.createTimeStamp }
    }

    fun removeScript(taskModel: TaskModel){
        state.taskModels.remove(taskModel)
        saveState()
    }

    fun removeScript(scriptId: String){
        state.taskModels.removeIf { it.scriptId.equals(scriptId) }
        saveState()
    }

    private fun createConnectHidDir(osName: Os, sshConnection: SSHJConnection,  userHome: String): String {
        var connectHidDir =""
        return try {
            if (osName == Os.Windows) {

                val dirPath = "$userHome\\.connecthid"
                // mkdir if missing
                sshConnection.execute("if not exist \"$dirPath\" mkdir \"$dirPath\"")
                connectHidDir = dirPath
            } else {

                val dirPath = "$userHome/.connecthid"
                sshConnection.execute("mkdir -p \"$dirPath\" || true")
                connectHidDir = dirPath
            }

            println("Home path: $connectHidDir")
           return connectHidDir
        } catch (e: Exception) {
            e.printStackTrace()
            connectHidDir
        }
    }


    private fun updateSystemInfo(server: Server) {
        val sshConnection = connections["${server.username}@${server.host}"] ?: return
        try {
            val systemInformation = sshConnection.getSystemInfo()
            systemInformation.osName?:return
            val os = systemInformation.osName!!.getOs()
            val connectHidPath = createConnectHidDir(os, sshConnection,systemInformation.homePath!!)

            // --- Update state ---
            val systemInfo = SystemInfo(
                osName = systemInformation.osName?:"",
                displayName = systemInformation.displayName?:"",
                osVersion = systemInformation.osVersion?:"",
                cpuType = systemInformation.cpuModel?:"",
                architecture = systemInformation.architecture?:"",
                totalRam = systemInformation.totalRam ?: "",
                usedRam = systemInformation.usedRam ?: "",
                totalStorage = systemInformation.totalStorage ?: "",
                usedStorage = systemInformation.usedStorage ?: "",
                hostName = systemInformation.hostName?:"",
                defaultShell = systemInformation.defaultShell?:"",
                connectHidDir = connectHidPath,
                homePath = systemInformation.homePath ?: ""
            )
            println(systemInfo.toString())

            val index = state.connections.indexOfFirst { it.host == server.host && it.username == server.username }
            if (index != -1) {
                state.connections[index] = server.copy(systemInfo = systemInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            val connection = SSHJConnection(host, username, password, port, privateKeyPath)
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
            val connection = SSHJConnection(host, username, password, port, privateKeyPath)
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

    private fun getConnection(host: String, username: String): SSHJConnection? {
        return connections["$username@${host}"]
    }

    fun getServer(server: String): Server? {
        return state.connections.firstOrNull() { it.stmpName.equals(server) }
    }

    fun getConnection(userAtHost: String): SSHJConnection? {
        return connections[userAtHost]
    }


    fun getConnection(server: Server): SSHJConnection? {
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
