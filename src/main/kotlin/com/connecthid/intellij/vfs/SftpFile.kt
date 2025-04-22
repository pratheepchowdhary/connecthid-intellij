package com.connecthid.intellij.vfs

import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class SftpFile(
    val pathLoacation: String,
    private val fileSystem: SftpFileSystem
) : VirtualFile() {
    private val connectionService = ServerConnectionService()
    private var attributes: SftpATTRS? = null
    private var children: Array<VirtualFile>? = null

    val host: String
        get() = pathLoacation.split("/").first()

    override fun getFileSystem(): VirtualFileSystem = fileSystem

    override fun getName(): String = pathLoacation.split("/").last()

    override fun isWritable(): Boolean = true

    override fun isDirectory(): Boolean {
        if (attributes == null) {
            getAttributes()
        }
        return attributes?.isDir ?: false
    }

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? {
        val parentPath = pathLoacation.substringBeforeLast("/", "")
        return if (parentPath.isEmpty()) null else SftpFile(parentPath, fileSystem)
    }

    override fun getChildren(): Array<VirtualFile> {
        if (children == null) {
            val connection = connectionService.getConnection(host) ?: return emptyArray()
            
            try {
                val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
                channel?.connect()
                
                val files = channel?.ls(pathLoacation) ?: return emptyArray()
                children = files
                    .mapNotNull { it as? ChannelSftp.LsEntry }
                    .filter { it.filename != "." && it.filename != ".." }
                    .map { SftpFile("$pathLoacation/${it.filename}", fileSystem) }
                    .toTypedArray()
                
                channel?.disconnect()
            } catch (e: Exception) {
                return emptyArray()
            }
        }
        return children ?: emptyArray()
    }

    override fun getTimeStamp(): Long {
        if (attributes == null) {
            getAttributes()
        }
        return attributes?.getMTime()?.toLong()?.times(1000) ?: 0
    }

    override fun getLength(): Long {
        if (attributes == null) {
            getAttributes()
        }
        return attributes?.size?.toLong() ?: 0
    }

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        attributes = null
        children = null
        postRunnable?.run()
    }

    private fun getAttributes() {
        val connection = connectionService.getConnection(host) ?: return
        
        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()
            
            attributes = channel?.stat(pathLoacation)
            channel?.disconnect()
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SftpFile) return false
        return pathLoacation == other.pathLoacation
    }

    override fun hashCode(): Int = pathLoacation.hashCode()

    override fun toString(): String = "SftpFile: $pathLoacation"

    override fun getOutputStream(requestor: Any?, modStamp: Long, timeStamp: Long): OutputStream {
        val connection = connectionService.getConnection(host) ?: throw IOException("No connection available")
        
        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()
            
            val outputStream = channel?.put(pathLoacation)
            channel?.disconnect()
            
            return outputStream ?: throw IOException("Failed to get output stream")
        } catch (e: Exception) {
            throw IOException("Failed to get output stream: ${e.message}", e)
        }
    }

    override fun contentsToByteArray(): ByteArray {
        val connection = connectionService.getConnection(host) ?: throw IOException("No connection available")
        
        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()
            
            val inputStream = channel?.get(pathLoacation) ?: throw IOException("Failed to get input stream")
            val byteArray = inputStream.readBytes()

            channel.disconnect()
            return byteArray
        } catch (e: Exception) {
            throw IOException("Failed to read file contents: ${e.message}", e)
        }
    }

    override fun getInputStream(): InputStream {
        val connection = connectionService.getConnection(host) ?: throw IOException("No connection available")
        
        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()
            
            val inputStream = channel?.get(pathLoacation) ?: throw IOException("Failed to get input stream")
            
            return object : InputStream() {
                override fun read(): Int = inputStream.read()
                
                override fun close() {
                    try {
                        inputStream.close()
                    } finally {
                        channel.disconnect()
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("Failed to get input stream: ${e.message}", e)
        }
    }

    override fun getPath(): String = pathLoacation
} 