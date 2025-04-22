package com.connecthid.intellij.vfs

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelSftp
import java.io.IOException
import java.io.InputStream

class SftpFileSystem(val project: Project, server: Server) : VirtualFileSystem() {
    private val connectionService = ServerConnectionService()
    internal val fileCache = mutableMapOf<String, SftpFile>()
    private val listeners = mutableListOf<VirtualFileListener>()

    companion object {
        const val PROTOCOL = "sftp"
    }

    override fun getProtocol(): String = PROTOCOL

    override fun findFileByPath(path: String): VirtualFile? {
        return fileCache.getOrPut(path) { SftpFile(path, this) }
    }

    override fun refresh(asynchronous: Boolean) {
        fileCache.clear()
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        refresh(false)
        return findFileByPath(path)
    }

    override fun deleteFile(requestor: Any?, vFile: VirtualFile): Unit {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = connectionService.getConnection(sftpFile.host) ?: throw IOException("No connection available")

        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()

            if (vFile.isDirectory) {
                channel?.rmdir(vFile.path)
            } else {
                channel?.rm(vFile.path)
            }

            channel?.disconnect()
            fileCache.remove(vFile.path)
        } catch (e: Exception) {
            throw IOException("Failed to delete file: ${e.message}", e)
        }
    }

    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile): Unit {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val newParentSftp = newParent as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = connectionService.getConnection(sftpFile.host) ?: throw IOException("No connection available")

        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()

            val newPath = "${newParentSftp.path}/${sftpFile.getName()}"
            channel?.rename(sftpFile.path, newPath)

            channel?.disconnect()
            fileCache.remove(sftpFile.path)
            fileCache[newPath] = SftpFile(newPath, this)
        } catch (e: Exception) {
            throw IOException("Failed to move file: ${e.message}", e)
        }
    }

    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String): Unit {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = connectionService.getConnection(sftpFile.host) ?: throw IOException("No connection available")

        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()

            val newPath = "${sftpFile.getParent()?.path ?: ""}/$newName"
            channel?.rename(sftpFile.path, newPath)

            channel?.disconnect()
            fileCache.remove(sftpFile.path)
            fileCache[newPath] = SftpFile(newPath, this)
        } catch (e: Exception) {
            throw IOException("Failed to rename file: ${e.message}", e)
        }
    }

    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = connectionService.getConnection(sftpDir.host) ?: throw IOException("No connection available")

        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()

            val newPath = "${sftpDir.path}/$fileName"
            channel?.put(InputStream.nullInputStream(), newPath)

            channel?.disconnect()
            val newFile = SftpFile(newPath, this)
            fileCache[newPath] = newFile
            return newFile
        } catch (e: Exception) {
            throw IOException("Failed to create file: ${e.message}", e)
        }
    }

    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = connectionService.getConnection(sftpDir.host) ?: throw IOException("No connection available")

        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()

            val newPath = "${sftpDir.path}/$dirName"
            channel?.mkdir(newPath)

            channel?.disconnect()
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
        val connection = connectionService.getConnection(sftpFile.host) ?: throw IOException("No connection available")

        try {
            val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
            channel?.connect()

            val newPath = "${newParentSftp.path}/$copyName"
            
            if (sftpFile.isDirectory) {
                channel?.mkdir(newPath)
            } else {
                val inputStream = channel?.get(sftpFile.path)
                channel?.put(inputStream, newPath)
            }

            channel?.disconnect()
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
} 