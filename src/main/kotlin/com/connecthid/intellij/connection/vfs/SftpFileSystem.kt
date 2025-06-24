package com.connecthid.intellij.connection.vfs

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.services.SSHConnection
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock


class SftpFileSystem(val project: Project, val server: Server) : VirtualFileSystem() {
    val connectionService = project.getSSHService()
    internal val fileCache = mutableMapOf<String, SftpFile>()
    private val listeners = mutableListOf<VirtualFileListener>()
    private var channelSftp: ChannelSftp?=null
    private val fileEditor by  lazy { FileEditorManager.getInstance(project) }
    private val connectionLock = ReentrantLock()
    var channelCount = hashMapOf<Int, ChannelSftp>()
    var maxChannels = 5;
    companion object {
        const val PROTOCOL = "sftp"
    }

    init {
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object :
            FileEditorManagerListener {
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                println("File closed: ${file.name}")
                if(file is SftpFile){
                    file.disconnectChannel()

                }
            }
        })
    }

    override fun getProtocol(): String = PROTOCOL

    override fun findFileByPath(path: String): VirtualFile {
        return fileCache.getOrPut(path) {
            getFile(path)
        }
    }

    private fun getFile(path: String):SftpFile{
        try {
            val channel = getChannel() ?: return SftpFile(path, this)
            val attrs  = channel.lstat(path)
            return SftpFile(path, this,attrs)
        } catch (e: Exception) {
            throw IOException("Failed to delete file: ${e.message}", e)
            return SftpFile(path, this)
        } finally {
            disconnectChannel()
        }
    }

    override fun refresh(asynchronous: Boolean) {

        fileCache.values.forEach {
            it.disconnectChannel()
        }
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
        } finally {
            disconnectChannel()
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
            val attrs  = channel.lstat(newPath)
            fileCache[newPath] = SftpFile(newPath, this,attrs)
        } catch (e: Exception) {
            throw IOException("Failed to move file: ${e.message}", e)
        } finally {
            disconnectChannel()
        }
    }

    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        try {
            val channel = getChannel() ?: return
            val oldPath = sftpFile.path
            val newPath = "${sftpFile.getParent()?.path ?: ""}/$newName"
            channel.rename(oldPath, newPath)
            fileCache.remove(oldPath)
            val attrs = channel.lstat(newPath)
            val renamedFile = SftpFile(newPath, this, attrs)
            fileCache[newPath] = renamedFile
            renamedFile.refresh(false, false, null)

            // Handle open editors for the renamed file and all children if directory
            val openFiles = fileEditor.openFiles
            if (sftpFile.isDirectory) {
                val affectedFiles = openFiles.filter { it.path.startsWith(oldPath + "/") }
                for (oldChild in affectedFiles) {
                    val relative = oldChild.path.removePrefix(oldPath)
                    val newChildPath = newPath + relative
                    fileEditor.closeFile(oldChild)
                    val newChild = findFileByPath(newChildPath)
                    openFileInIDE(newChild)
                }
            }
            // Handle the renamed file itself
            if (fileEditor.isFileOpen(vFile)) {
                fileEditor.closeFile(vFile)
                openFileInIDE(renamedFile)
            }
        } catch (e: Exception) {
            throw IOException("Failed to rename file: ${e.message}", e)
        } finally {
            disconnectChannel()
        }
    }

    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        try {
            val channel = getChannel() ?: return throw IOException("Failed to create file")
            val newPath = "${sftpDir.path}/$fileName"
            channel.put(InputStream.nullInputStream(), newPath)
            val attrs  = channel.lstat(newPath)
            val newFile = SftpFile(newPath, this,attrs)
            fileCache[newPath] = newFile
            return newFile
        } catch (e: Exception) {
            throw IOException("Failed to create file: ${e.message}", e)
        }
        finally {
            disconnectChannel()
        }
    }

    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")


        try {
            val channel = getChannel() ?: return throw IOException("Failed to create file")
            val newPath = "${sftpDir.path}/$dirName"
            channel.mkdir(newPath)
            val attrs  = channel.lstat(newPath)
            val newDir = SftpFile(newPath, this,attrs)
            fileCache[newPath] = newDir
            return newDir
        } catch (e: Exception) {
            throw IOException("Failed to create directory: ${e.message}", e)
        }
        finally {
            disconnectChannel()
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
            val attrs  = channel.lstat(newPath)
            val newFile = SftpFile(newPath, this,attrs)
            fileCache[newPath] = newFile
            return newFile
        } catch (e: Exception) {
            throw IOException("Failed to copy file: ${e.message}", e)
        } finally {
            disconnectChannel()
        }
    }

    override fun addVirtualFileListener(listener: VirtualFileListener) {
        listeners.add(listener)
    }

    override fun removeVirtualFileListener(listener: VirtualFileListener) {
        listeners.remove(listener)
    }

    override fun isReadOnly(): Boolean = false


    internal fun getConnection(): SSHConnection? {
        connectionLock.lock()
        try{
            var connection = connectionService.getConnection(server.host)
            if (connection == null || !connection.isConnected()) {
                connectionService.connect(server.host, server.username, server.password, port = server.port)
                connection = connectionService.getConnection(server.host)
                if(connection != null && connection.isConnected()){
                    maxChannels = getMaxSessionsValue(connection.getSession()!!)
                    println("maxChannels:"+maxChannels)
                }
            }
            if( connection!= null && connection.isConnected() && !isSessionAlive(connection.getSession()!!)){
                connectionService.disconnect(server.host)
                connectionService.connect(server.host, server.username, server.password, port = server.port)
                connection = connectionService.getConnection(server.host)
                if(connection != null && connection.isConnected()){
                    maxChannels = getMaxSessionsValue(connection.getSession()!!)
                    println("maxChannels:"+maxChannels)
                }
            }
            return connection

        } finally {
            connectionLock.unlock()
        }
    }

    fun isSessionAlive(session: Session): Boolean {
        return try {
            session.sendKeepAliveMsg()
            true
        } catch (e: Exception) {
            false
        }
    }


     private fun getChannel(): ChannelSftp? {
        val connection = getConnection() ?: return null
        if(!connection.isConnected()) return null
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
    private fun disconnectChannel() {
        try {
           channelSftp?.disconnect()
           channelSftp = null
        } catch (e: Exception) {
            println("Error disconnecting SFTP channel: ${e.message}")
            e.printStackTrace()
        }
    }


    fun openFileInIDE(file: VirtualFile): Boolean {
        try {
            // Check if file is already opened in editor
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
                fileEditor.openFile(file, true)
            } else {
                // For text files, ensure proper text editor
                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document != null) {
                    fileEditor.openTextEditor(
                        OpenFileDescriptor(project, file, 0),
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


    fun getMaxSessionsValue(session: Session): Int {
        return try {
            val channel = session.openChannel("exec") as ChannelExec

            // Use sshd -T if available, else fallback to reading sshd_config
            val command = "sshd -T 2>/dev/null | grep -i maxsessions || grep -i MaxSessions /etc/ssh/sshd_config"
            channel.setCommand(command)
            channel.setInputStream(null)
            val outputStream = ByteArrayOutputStream()
            channel.outputStream = outputStream
            channel.connect()
            // Wait for command to complete
            while (!channel.isClosed) {
                Thread.sleep(100)
            }
            channel.disconnect()
            val output = outputStream.toString().trim()
            return output.lines().firstOrNull { it.contains("maxsessions", ignoreCase = true) }?.let { line ->
                line.trim().split(Regex("\\s+")).firstOrNull { it.matches(Regex("\\d+")) }?.toIntOrNull()
            } ?: 5

        } catch (e: Exception) {
            e.printStackTrace()
            return 5
        }
    }


}