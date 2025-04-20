package com.connecthid.intellij.vfs

import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SftpFile(
    val path: String,
    private val fileSystem: SftpFileSystem
) : VirtualFile() {
    private val connectionService = ServerConnectionService()
    private var attrs: SftpATTRS? = null
    private var children: Array<VirtualFile>? = null
    private var parent: VirtualFile? = null

    val host: String
        get() = path.substringBefore("/")

    override fun getFileSystem(): VirtualFileSystem = fileSystem

    override fun getPath(): String = path

    override fun getName(): String = path.substringAfterLast("/")

    override fun isWritable(): Boolean = true

    override fun isDirectory(): Boolean {
        return getAttributes()?.isDir ?: false
    }

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? {
        if (parent == null) {
            val parentPath = path.substringBeforeLast("/", "")
            if (parentPath.isNotEmpty()) {
                parent = fileSystem.findFileByPath(parentPath)
            }
        }
        return parent
    }

    override fun getChildren(): Array<VirtualFile> {
        if (children == null && isDirectory) {
            val connection = connectionService.getConnection(host) ?: return emptyArray()
            
            try {
                val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
                channel?.connect()
                
                val entries = channel?.ls(path)
                children = entries?.mapNotNull { entry ->
                    val lsEntry = entry as? com.jcraft.jsch.ChannelSftp.LsEntry ?: return@mapNotNull null
                    val filename = lsEntry.filename
                    
                    if (filename != "." && filename != "..") {
                        val childPath = "$path/$filename"
                        SftpFile(childPath, fileSystem)
                    } else null
                }?.toTypedArray() ?: emptyArray()
                
                channel?.disconnect()
            } catch (e: Exception) {
                throw IOException("Failed to list directory: ${e.message}", e)
            }
        }
        return children ?: emptyArray()
    }

    override fun getTimeStamp(): Long {
        return getAttributes()?.getMTime()?.toLong()?.times(1000) ?: 0
    }

    override fun getLength(): Long {
        return getAttributes()?.size ?: 0
    }

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        attrs = null
        children = null
        if (recursive) {
            getChildren().forEach { it.refresh(asynchronous, true, null) }
        }
        postRunnable?.run()
    }

    private fun getAttributes(): SftpATTRS? {
        if (attrs == null) {
            val connection = connectionService.getConnection(host) ?: return null
            
            try {
                val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
                channel?.connect()
                
                attrs = channel?.stat(path)
                channel?.disconnect()
            } catch (e: Exception) {
                throw IOException("Failed to get file attributes: ${e.message}", e)
            }
        }
        return attrs
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SftpFile) return false
        return path == other.path
    }

    override fun hashCode(): Int = path.hashCode()

    override fun toString(): String = "SftpFile: $path"

    override fun getOutputStream(requestor: Any?, modStamp: Long, timeStamp: Long): OutputStream {
        val connection = connectionService.getConnection(host) ?: throw IOException("No connection available")
        
        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()
            
            val outputStream = channel?.put(path)
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
            
            val inputStream = channel?.get(path) ?: throw IOException("Failed to get input stream")
            val byteArray = inputStream.readBytes()
            
            channel?.disconnect()
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
            
            val inputStream = channel?.get(path) ?: throw IOException("Failed to get input stream")
            
            return object : InputStream() {
                override fun read(): Int = inputStream.read()
                
                override fun close() {
                    try {
                        inputStream.close()
                    } finally {
                        channel?.disconnect()
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("Failed to get input stream: ${e.message}", e)
        }
    }
} 