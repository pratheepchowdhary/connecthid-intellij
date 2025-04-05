package com.connecthid.intellij.services

import com.jcraft.jsch.*
import java.io.IOException
import java.io.InputStream

class SSHConnection(
    private val host: String,
    private val username: String,
    private val password: String? = null,
    private val port: Int = 22,
    private val privateKeyPath: String? = null
) {
    private val jsch = JSch()
    private var session: Session? = null
    private var channel: Channel? = null

    init {
        JSch.setConfig("StrictHostKeyChecking", "no")
    }

    fun connect() {
        try {
            session = jsch.getSession(username, host, port)
            
            if (privateKeyPath != null) {
                // Use private key authentication
                jsch.addIdentity(privateKeyPath)
            } else if (password != null) {
                // Use password authentication
                session?.setPassword(password)
            } else {
                throw IOException("No authentication method provided")
            }
            
            session?.connect()
        } catch (e: JSchException) {
            throw IOException("Failed to connect to $host: ${e.message}", e)
        }
    }

    fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
    }

    fun isConnected(): Boolean {
        return session?.isConnected == true
    }

    fun getSession(): Session? {
        return session
    }

    fun execute(command: String): String {
        if (!isConnected()) {
            connect()
        }

        try {
            channel = session?.openChannel("exec")
            (channel as ChannelExec).setCommand(command)
            channel?.connect()

            val inputStream = channel?.inputStream
            val errorStream = (channel as ChannelExec).errStream

            val output = readStream(inputStream)
            val error = readStream(errorStream)

            if (error.isNotEmpty()) {
                throw IOException("Command execution failed: $error")
            }

            return output
        } catch (e: JSchException) {
            throw IOException("Failed to execute command: ${e.message}", e)
        } finally {
            channel?.disconnect()
        }
    }

    fun uploadFile(localPath: String, remotePath: String) {
        if (!isConnected()) {
            connect()
        }

        try {
            channel = session?.openChannel("sftp")
            channel?.connect()
            val sftp = channel as ChannelSftp
            sftp.put(localPath, remotePath)
        } catch (e: JSchException) {
            throw IOException("Failed to upload file: ${e.message}", e)
        } catch (e: SftpException) {
            throw IOException("Failed to upload file: ${e.message}", e)
        } finally {
            channel?.disconnect()
        }
    }

    fun downloadFile(remotePath: String, localPath: String) {
        if (!isConnected()) {
            connect()
        }

        try {
            channel = session?.openChannel("sftp")
            channel?.connect()
            val sftp = channel as ChannelSftp
            sftp.get(remotePath, localPath)
        } catch (e: JSchException) {
            throw IOException("Failed to download file: ${e.message}", e)
        } catch (e: SftpException) {
            throw IOException("Failed to download file: ${e.message}", e)
        } finally {
            channel?.disconnect()
        }
    }

    private fun readStream(inputStream: InputStream?): String {
        if (inputStream == null) return ""
        val reader = inputStream.bufferedReader()
        return reader.use { it.readText() }
    }

    fun close() {
        disconnect()
    }
} 