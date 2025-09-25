package com.connecthid.intellij.connection.sftp

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.SftpFileOccurrence
import com.connecthid.intellij.models.SftpMatchInfo
import com.connecthid.intellij.models.getPassword
import com.connecthid.intellij.services.SSHConnection
import com.connecthid.intellij.utils.Utils.parseSftpUrl
import com.connecthid.intellij.utils.isWindows
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.ex.FileTypeChooser
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.iterator


 class SftpFileSystem : VirtualFileSystem() {
    // Use lazy initialization to defer service access until actually needed
    val connectionService by lazy { getSSHService() }
    internal val fileCache = mutableMapOf<String, SftpFile>()
    private val listeners = mutableListOf<VirtualFileListener>()
    var showHiddenFiles = true // This can be a setting in the future to toggle hidden files visibility

    companion object {
        const val PROTOCOL = "sftp"
    }


    override fun getProtocol(): String = PROTOCOL

    override fun findFileByPath( path: String): VirtualFile? {
        try {
            var remotepath = path
            if (!path.startsWith("sftp://")) remotepath = "sftp://$path"
            val url  = parseSftpUrl(remotepath)
            val server = connectionService.getServer("${url.username}@${url.host}") ?: return null
            return fileCache.getOrPut(path) {
                getFile(url.path,server)
            }
        } catch (ex: Exception){
            println(ex.message)
            return null
        }

    }

    private fun getFile(path: String,server: Server): SftpFile {
        val fileStat = getFileStat(path,server)
        return SftpFile(path, server, fileStat)
    }

    fun getFileStat(path: String,server: Server):SftpATTRS ?{
        var channel: ChannelSftp? = null
        val connection = getConnection(server) ?: return null
        try {
            channel = connection.getChannelFromPool()
            if (channel == null || !channel.isConnected) return null
            val attrs = channel.lstat(path)
            return attrs
        } catch (e: Exception) {
            return null
        } finally {
            connection.releaseChannelToPool(channel)
        }
    }


    override fun refresh(asynchronous: Boolean) {

        fileCache.clear()
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        refresh(false)
        return findFileByPath(path)
    }

    private fun deleteRecursiveSftp(channel: ChannelSftp, vFile: VirtualFile) {
        if (vFile.isDirectory) {
            val children = vFile.children.toList() // cache once
            for (child in children) {
                deleteRecursiveSftp(channel, child) // reuse same channel
            }
            channel.rmdir(vFile.path) // remove empty dir
        } else {
            channel.rm(vFile.path) // remove file
        }
    }
    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        var channel: ChannelSftp? = null
        val sftpFile = vFile as? SftpFile ?: return
        val connection = getConnection(sftpFile.server) ?: return
        try {
            if(sftpFile.server.systemInfo.osName.isWindows() || !vFile.isDirectory){
                channel = connection.getChannelFromPool() ?: return
                if (vFile.isDirectory) {
                    if (vFile.children.isNotEmpty()) {
                        // ✅ Reuse the same channel for the whole recursion
                        deleteRecursiveSftp(channel, vFile)
                    } else {
                        channel.rmdir(vFile.path)
                    }
                } else {
                    channel.rm(vFile.path)
                }
            } else if(vFile.isDirectory){
                connection.execute("rm -rf '${vFile.path}'")
            }
            fileCache.remove(vFile.path)
        } catch (e: Exception) {
            throw IOException("Failed to delete file: ${e.message}", e)
        } finally {
            connection.releaseChannelToPool(channel)
        }
    }

    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val newParentSftp = newParent as? SftpFile ?: throw IOException("Not an SFTP file")
        var channel: ChannelSftp? = null
        val connection = getConnection(sftpFile.server) ?: return
        try {
            channel = connection.getChannelFromPool() ?: return
            val newPath = "${newParentSftp.path}/${sftpFile.getName()}"
            channel.rename(sftpFile.path, newPath)
            fileCache.remove(sftpFile.path)
            val attrs = channel.lstat(newPath)
            fileCache[newPath] = SftpFile(newPath, sftpFile.server, attrs)
        } catch (e: Exception) {
            throw IOException("Failed to move file: ${e.message}", e)
        } finally {
            connection.releaseChannelToPool(channel)
        }
    }

    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        var channel: ChannelSftp? = null
        val connection = getConnection(sftpFile.server) ?: return
        try {
            channel = connection.getChannelFromPool() ?: return
            val oldPath = sftpFile.path
            val newPath = "${sftpFile.getParent()?.path ?: ""}/$newName"
            channel.rename(oldPath, newPath)
            fileCache.remove(oldPath)
            val attrs = channel.lstat(newPath)
            val renamedFile = SftpFile(newPath, sftpFile.server, attrs)
            fileCache[newPath] = renamedFile
            requestor?.let {
                val callback = it as? (renamedFile: SftpFile) -> Unit
                callback?.invoke(renamedFile)
            }

        } catch (e: Exception) {
            throw IOException("Failed to rename file: ${e.message}", e)
        } finally {
            connection.releaseChannelToPool(channel)
        }
    }

    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        var channel: ChannelSftp? = null
        val connection = getConnection(sftpDir.server) ?: throw IOException("Failed to create file")
        try {
            channel = connection.getChannelFromPool() ?: throw IOException("Failed to create file")
            val newPath = "${sftpDir.path}/$fileName"
            channel.put(InputStream.nullInputStream(), newPath)
            val attrs = channel.lstat(newPath)
            val newFile = SftpFile(newPath, sftpDir.server, attrs)
            fileCache[newPath] = newFile
            return newFile
        } catch (e: Exception) {
            throw IOException("Failed to create file: ${e.message}", e)
        } finally {
            connection.releaseChannelToPool(channel)
        }
    }

    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        var channel: ChannelSftp? = null
        val connection = getConnection(sftpDir.server) ?: throw IOException("Failed to create file")
        try {
            channel = connection.getChannelFromPool() ?: throw IOException("Failed to create file")
            val newPath = "${sftpDir.path}/$dirName"
            channel.mkdir(newPath)
            val attrs = channel.lstat(newPath)
            val newDir = SftpFile(newPath, sftpDir.server, attrs)
            fileCache[newPath] = newDir
            return newDir
        } catch (e: Exception) {
            throw IOException("Failed to create directory: ${e.message}", e)
        } finally {
            connection.releaseChannelToPool(channel)
        }
    }

    override fun copyFile(requestor: Any?, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
        val sftpFile = virtualFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val newParentSftp = newParent as? SftpFile ?: throw IOException("Not an SFTP file")
        var channel: ChannelSftp? = null
        val connection = getConnection(sftpFile.server) ?: throw IOException("Failed to copy file")
        try {
            channel = connection.getChannelFromPool() ?: throw IOException("Failed to copy file")
            val newPath = "${newParentSftp.path}/$copyName"
            if (sftpFile.isDirectory) {
                channel.mkdir(newPath)
            } else {
                val inputStream = channel.get(sftpFile.path)
                channel.put(inputStream, newPath)
            }
            val attrs = channel.lstat(newPath)
            val newFile = SftpFile(newPath, sftpFile.server, attrs)
            fileCache[newPath] = newFile
            return newFile
        } catch (_: Exception) {
            throw IOException("Failed to copy file")
        } finally {
            connection.releaseChannelToPool(channel)
        }
    }

    fun copyFile(targetFile: VirtualFile, destinationFile: VirtualFile) {
        // Copy targetFile to destinationFile path using ssh if not windows
        val sftpFile = destinationFile as? SftpFile ?: return
        val connection = getConnection(sftpFile.server) ?: return
        try {
            if (sftpFile.server.systemInfo.osName.isWindows()) {
                throw IOException("Copy not supported on Windows servers")
            } else {
                connection.execute("cp -r '${targetFile.path}' '${destinationFile.path}'")
            }
        } catch (e: Exception) {
            throw IOException(e.message)
        }
    }

    fun moveFile(targetFile: VirtualFile, destinationFile: VirtualFile) {
        // Copy targetFile to destinationFile path using ssh if not windows
        val sftpFile = destinationFile as? SftpFile ?: return
        val connection = getConnection(sftpFile.server) ?: return
        try {
            if (sftpFile.server.systemInfo.osName.isWindows()) {
                throw IOException("Copy not supported on Windows servers")
            } else {
                connection.execute("mv '${targetFile.path}' '${destinationFile.path}'")
            }
        } catch (e: Exception) {
            throw IOException(e.message)
        }
    }

    override fun addVirtualFileListener(listener: VirtualFileListener) {
        listeners.add(listener)
    }

    override fun removeVirtualFileListener(listener: VirtualFileListener) {
        listeners.remove(listener)
    }

    override fun isReadOnly(): Boolean = false

    fun isConnected(server: Server): Boolean {
        val connection = connectionService.getConnection(server.host,server.username)
        return connection?.isConnected() ?: false
    }


    internal fun getConnection(server: Server): SSHConnection? {
        return connectionService.getConnection(server)
    }


    fun getChannelFromPool(server: Server): ChannelSftp? {
        val connection = getConnection(server) ?: return null
        return connection.getChannelFromPool()
    }

    fun releaseChannelToPool(channel: ChannelSftp?,server: Server) {
        val connection = getConnection(server) ?: return
        connection.releaseChannelToPool(channel)
    }

}

fun Project.openFileInIDE(file: VirtualFile): Boolean {
    try {
        // Check if file is already opened in editor
        val fileEditor = FileEditorManager.getInstance(this)
        val editors = fileEditor.getEditors(file)
        if (editors.isNotEmpty()) {
            // File is already opened, navigate to it
            fileEditor.openFile(file, true)
            return true
        }
        // If it's a text file, ensure it's opened in a text editor
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(file.name)
        if (fileType.isBinary) {
            // For binary files, just open them directly
            val extension = file.extension ?: return false

            // Show the IntelliJ "Associate File Type" dialog
           // FileTypeChooser.associateFileType(extension)
            fileEditor.openFile(file, true)
        } else {
            // For text files, ensure proper text editor
            val document = FileDocumentManager.getInstance().getDocument(file)
            if (document != null) {
                fileEditor.openTextEditor(
                    OpenFileDescriptor(this, file, 0),
                    true
                )
            }
        }
        return true
    } catch (e: Exception) {
        e.printStackTrace()

    }
    return false
}