package com.connecthid.intellij.vfs

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.services.SSHConnection
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelSftp
import java.io.IOException
import java.io.InputStream

class SftpFileSystem(val project: Project, val server: Server) : VirtualFileSystem() {
    val connectionService = project.getSSHService()
    internal val fileCache = mutableMapOf<String, SftpFile>()
    private val listeners = mutableListOf<VirtualFileListener>()
    private var channelSftp: ChannelSftp?=null

    companion object {
        const val PROTOCOL = "sftp"
    }

    override fun getProtocol(): String = PROTOCOL

    override fun findFileByPath(path: String): VirtualFile {
        return fileCache.getOrPut(path) {
            SftpFile(path, this)
        }
    }

    override fun refresh(asynchronous: Boolean) {
        fileCache.clear()
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        refresh(false)
        return findFileByPath(path)
    }

    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        try {
            val channel = getChannel() ?: return
            if (vFile.isDirectory) {
                channel.rmdir(vFile.path)
            } else {
                channel.rm(vFile.path)
            }
            fileCache.remove(vFile.path)
        } catch (e: Exception) {
            throw IOException("Failed to delete file: ${e.message}", e)
        }
    }

    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val newParentSftp = newParent as? SftpFile ?: throw IOException("Not an SFTP file")

        try {
            val channel = getChannel() ?: return
            val newPath = "${newParentSftp.path}/${sftpFile.getName()}"
            channel.rename(sftpFile.path, newPath)
            fileCache.remove(sftpFile.path)
            fileCache[newPath] = SftpFile(newPath, this)
        } catch (e: Exception) {
            throw IOException("Failed to move file: ${e.message}", e)
        }
    }

    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String): Unit {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")

        try {
            val channel = getChannel() ?: return
            val newPath = "${sftpFile.getParent()?.path ?: ""}/$newName"
            channel.rename(sftpFile.path, newPath)
            fileCache.remove(sftpFile.path)
            fileCache[newPath] = SftpFile(newPath, this)
        } catch (e: Exception) {
            throw IOException("Failed to rename file: ${e.message}", e)
        }
    }

    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        try {
            val channel = getChannel() ?: return throw IOException("Failed to create file")
            val newPath = "${sftpDir.path}/$fileName"
            channel.put(InputStream.nullInputStream(), newPath)
            val newFile = SftpFile(newPath, this)
            fileCache[newPath] = newFile
            return newFile
        } catch (e: Exception) {
            throw IOException("Failed to create file: ${e.message}", e)
        }
    }

    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")


        try {
            val channel = getChannel() ?: return throw IOException("Failed to create file")
            val newPath = "${sftpDir.path}/$dirName"
            channel.mkdir(newPath)
            val newDir = SftpFile(newPath, this)
            fileCache[newPath] = newDir
            return newDir
        } catch (e: Exception) {
            throw IOException("Failed to create directory: ${e.message}", e)
        }
    }

    override fun copyFile(requestor: Any?, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
        val sftpFile = virtualFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val newParentSftp = newParent as? SftpFile ?: throw IOException("Not an SFTP file")

        try {
            val channel = getChannel() ?:  throw IOException("Failed to copy file")

            val newPath = "${newParentSftp.path}/$copyName"
            
            if (sftpFile.isDirectory) {
                channel.mkdir(newPath)
            } else {
                val inputStream = channel.get(sftpFile.path)
                channel.put(inputStream, newPath)
            }
            val newFile = SftpFile(newPath, this)
            fileCache[newPath] = newFile
            return newFile
        } catch (e: Exception) {
            throw IOException("Failed to copy file: ${e.message}", e)
        }
    }

    override fun addVirtualFileListener(listener: VirtualFileListener) {
        listeners.add(listener)
    }

    override fun removeVirtualFileListener(listener: VirtualFileListener) {
        listeners.remove(listener)
    }

    override fun isReadOnly(): Boolean = false


    private fun getConnection(): SSHConnection? {
        var connection = connectionService.getConnection(server.host)
        if (connection == null || !connection.isConnected()) {
            connectionService.connect(server.host, server.username, server.password, port = server.port)
            connection = connectionService.getConnection(server.host)
        }
        return connection
    }
     fun getChannel(): ChannelSftp? {
        val connection = getConnection() ?: return null
        if (channelSftp == null || !channelSftp!!.isConnected) {
            try {
                channelSftp?.disconnect()
                channelSftp = connection.getSession()?.openChannel("sftp") as? ChannelSftp
                channelSftp!!.connect()
            } catch (e: Exception) {
                println("Error creating SFTP channel: ${e.message}")
                e.printStackTrace()
                return null
            }
        }
        return channelSftp
    }
    fun disconnectChannel() {
        try {
           channelSftp?.disconnect()
           channelSftp = null
        } catch (e: Exception) {
            println("Error disconnecting SFTP channel: ${e.message}")
            e.printStackTrace()
        }
    }

} 