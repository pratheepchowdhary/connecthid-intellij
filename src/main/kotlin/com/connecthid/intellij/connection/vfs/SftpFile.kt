package com.connecthid.intellij.connection.vfs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.locks.ReentrantLock

class SftpFile(
    val pathLocation: String,
    private val fileSystem: SftpFileSystem,
    val fileEntry:SftpATTRS? = null
) : VirtualFile() {
    private var channelSftp: ChannelSftp?=null
    private val channelLock = ReentrantLock()
    private var channelId = -1



    private var children: Array<VirtualFile>? = null
    override fun getFileSystem(): VirtualFileSystem = fileSystem

    override fun getName(): String = pathLocation.split("/").last()

    override fun isWritable(): Boolean {
        if(fileEntry == null){
            return true
        }
        return fileEntry.isWritable()
    }

    override fun isDirectory(): Boolean {
        if (fileEntry == null) {
            return true
        }
        return fileEntry.isDir
    }

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? {
        val parentPath = pathLocation.substringBeforeLast("/", "")
        return if (parentPath.isEmpty()) null else SftpFile(parentPath, fileSystem)
    }


    override fun getChildren(): Array<VirtualFile> {
        if (children == null) {
            try {
                val channel = getChannel()
                if (channel == null || !channel.isConnected) {
                    println("Failed to establish SFTP channel")
                    return emptyArray()
                }
                
                // List the current directory
                val currentPath = if (pathLocation == "/") "." else pathLocation
                println("Listing directory with path: $currentPath")
                
                val files = channel.ls(currentPath) ?: return emptyArray()
                println("Raw file list: ${files.joinToString { it.toString() }}")
                
                children = files
                    .mapNotNull { it as? ChannelSftp.LsEntry }
                    .filter { it.filename != "." && it.filename != ".." }
                    .map { 
                        val childPath = if (pathLocation == "/") "/${it.filename}" else "$pathLocation/${it.filename}"
                        println("Creating child: $childPath (filename: ${it.filename}, longname: ${it.longname})")
                        SftpFile(childPath, fileSystem,it.attrs)
                    }
                    .toTypedArray()
                
                println("Created ${children!!.size} child files")
            } catch (e: Exception) {
                println("Error listing directory: ${e.message}")
                e.printStackTrace()
                return emptyArray()
            }
            finally {
                disconnectChannel()
            }
        }
        return children ?: emptyArray()
    }


    override fun getTimeStamp(): Long {
        if (fileEntry == null) {
            return  0
        }
        return fileEntry.aTime.toLong()

    }

    override fun getModificationStamp(): Long {
        if (fileEntry == null) {
            return  0
        }
        return fileEntry.mTime.toLong()
    }

    override fun getLength(): Long {
        if (fileEntry == null) {
            return  0
        }
        return fileEntry.size.toLong()
    }

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        children = null
        postRunnable?.run()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SftpFile) return false
        return pathLocation == other.pathLocation
    }

    override fun hashCode(): Int = pathLocation.hashCode()

    override fun toString(): String = "SftpFile: $pathLocation"

    override fun getOutputStream(requestor: Any?, modStamp: Long, timeStamp: Long): OutputStream {
        
        try {
            val channel = getChannel() ?: throw IOException("Failed to get SFTP channel")
            return channel.put(pathLocation) ?: throw IOException("Failed to get output stream")
        } catch (e: Exception) {
            throw IOException("Failed to get output stream: ${e.message}", e)
        }
    }

    override fun contentsToByteArray(): ByteArray {
        
        try {
            val channel = getChannel() ?: throw IOException("Failed to get SFTP channel")
            val inputStream = channel.get(pathLocation) ?: throw IOException("Failed to get input stream")
            return inputStream.readBytes()
        } catch (e: Exception) {
            throw IOException("Failed to read file contents: ${e.message}", e)
        }
    }

    override fun getInputStream(): InputStream {
        
        try {
            val channel = getChannel() ?: throw IOException("Failed to get SFTP channel")
            val inputStream = channel.get(pathLocation) ?: throw IOException("Failed to get input stream")
            
            return object : InputStream() {
                override fun read(): Int = inputStream.read()
                
                override fun close() {
                    try {
                        inputStream.close()
                    } catch (e: Exception) {
                        println("Error closing input stream: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("Failed to get input stream: ${e.message}", e)
        }
    }

    override fun getPath(): String = pathLocation


    private fun getChannel(): ChannelSftp? {
        channelLock.lock()
        val connection = fileSystem.getConnection() ?: return null
        try {
            if (channelSftp == null || !channelSftp!!.isConnected) {
                channelSftp?.disconnect()
                channelSftp = connection.getSession()?.openChannel("sftp") as? ChannelSftp
                channelSftp!!.connect()
                val size = fileSystem.channelCount.size
                channelId = size+1
                fileSystem.channelCount[channelId]=channelSftp!!
                println("added Channel : ${channelId} and all channels size: "+fileSystem.channelCount.size)
            }
            return channelSftp
        } catch (e: Exception) {
            println("Error creating SFTP channel: ${e.message}")
            e.printStackTrace()
            return null
        } finally {
            channelLock.unlock()
        }
    }
    internal fun disconnectChannel() {
        channelLock.lock()
        try {
            channelSftp?.disconnect()
            channelSftp = null
            if(channelId!=-1){
               fileSystem.channelCount.remove(channelId)
              println("Removed Channel : ${channelId} and remaining channels size: "+fileSystem.channelCount.size)
            }
        } catch (e: Exception) {
            println("Error disconnecting SFTP channel: ${e.message}")
            e.printStackTrace()
        } finally {
            channelLock.unlock()
        }
    }
}

fun SftpATTRS.isWritable(): Boolean {
    val permissions = this.permissions // This is an int
    // Check if owner has write permission (bitmask 0o200 == 128)
    val S_IWUSR = 0b10_0000_000 // Octal 0200 == decimal 128
    return (permissions and S_IWUSR) != 0
}

