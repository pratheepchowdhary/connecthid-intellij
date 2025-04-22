package com.connecthid.intellij.vfs

import com.connecthid.intellij.services.SSHConnection
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SftpFile(
    val pathLoacation: String,
    private val fileSystem: SftpFileSystem
) : VirtualFile() {
    private val connectionService = fileSystem.connectionService
    private  val server = fileSystem.server
    private var attributes: SftpATTRS? = null
    private var children: Array<VirtualFile>? = null

    val host: String
        get() = server.host


    private fun getConnection(): SSHConnection ?{
        val connection = connectionService.getConnection(server.host)
        if (connection == null){
            connectionService.connect(host,server.username,server.password, port = server.port)
        }
        return connection
    }

    private  fun getChannel(): ChannelSftp?{

        val connection = getConnection() ?: return null
        return connection.getSession()!!.openChannel("sftp") as? ChannelSftp
    }


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
            try {

                
                println("SSH connection established, opening SFTP channel...")
                val channel = getChannel()
                channel?.connect()
                
                if (channel == null || !channel.isConnected) {
                    println("Failed to establish SFTP channel")
                    return emptyArray()
                }
                
                // List the current directory
                val currentPath = if (pathLoacation == "/") "." else pathLoacation
                println("Listing directory with path: $currentPath")
                
                val files = channel.ls(currentPath) ?: return emptyArray()
                println("Raw file list: ${files.joinToString { it.toString() }}")
                
                children = files
                    .mapNotNull { it as? ChannelSftp.LsEntry }
                    .filter { it.filename != "." && it.filename != ".." }
                    .map { 
                        val childPath = if (pathLoacation == "/") "/${it.filename}" else "$pathLoacation/${it.filename}"
                        println("Creating child: $childPath (filename: ${it.filename}, longname: ${it.longname})")
                        SftpFile(childPath, fileSystem)
                    }
                    .toTypedArray()
                
                println("Created ${children!!.size} child files")
                channel.disconnect()
            } catch (e: Exception) {
                println("Error listing directory: ${e.message}")
                e.printStackTrace()
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
        
        try {
            println("SSH connection established, opening SFTP channel...")
            val channel = getChannel()
            channel?.connect()
            if (channel == null || !channel.isConnected) {
                println("Failed to establish SFTP channel")
                return
            }
            val currentPath = if (pathLoacation == "/") "." else pathLoacation
            println("Getting attributes for path: $currentPath")
            attributes = channel.stat(currentPath)
            println("Attributes found: ${attributes != null}")
            channel.disconnect()
        } catch (e: Exception) {
            println("Error getting attributes: ${e.message}")
            e.printStackTrace()
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

        
        try {
            val channel = getChannel()
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