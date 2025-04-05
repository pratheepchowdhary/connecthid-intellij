package com.connecthid.intellij.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jcraft.jsch.Session
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ServerConnectionService(private val project: Project) {
    private val connections = ConcurrentHashMap<String, SSHConnection>()

    fun connect(host: String, username: String, password: String, port: Int = 22): Boolean {
        try {
            val connection = SSHConnection(host, username, password, port)
            connection.connect()
            connections[host] = connection
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
} 