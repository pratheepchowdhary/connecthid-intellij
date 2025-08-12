package com.connecthid.intellij.connection.vfs

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.getPassword
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
import com.jcraft.jsch.SftpATTRS
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock


class SftpFileSystem(val project: Project, val server: Server) : VirtualFileSystem() {
    // Use lazy initialization to defer service access until actually needed
    val connectionService by lazy { project.getSSHService() }
    internal val fileCache = mutableMapOf<String, SftpFile>()
    private val listeners = mutableListOf<VirtualFileListener>()

    private val fileEditor by  lazy { FileEditorManager.getInstance(project) }
    private val connectionLock = ReentrantLock()
    companion object {
        const val PROTOCOL = "sftp"
    }


    override fun getProtocol(): String = PROTOCOL

    override fun findFileByPath(path: String): VirtualFile {
        return fileCache.getOrPut(path) {
            getFile(path)
        }
    }

    private fun getFile(path: String): SftpFile {
        val fileStat = getFileStat(path)
        return SftpFile(path, this, fileStat)
    }

    fun getFileStat(path: String):SftpATTRS ?{
        var channel: ChannelSftp? = null
        val connection = getConnection() ?: return null
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

    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        var channel: ChannelSftp? = null
        val connection = getConnection() ?: return
        try {
            channel = connection.getChannelFromPool() ?: return
            if (vFile.isDirectory) {
                channel.rmdir(vFile.path)
            } else {
                channel.rm(vFile.path)
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
        val connection = getConnection() ?: return
        try {
            channel = connection.getChannelFromPool() ?: return
            val newPath = "${newParentSftp.path}/${sftpFile.getName()}"
            channel.rename(sftpFile.path, newPath)
            fileCache.remove(sftpFile.path)
            val attrs = channel.lstat(newPath)
            fileCache[newPath] = SftpFile(newPath, this, attrs)
        } catch (e: Exception) {
            throw IOException("Failed to move file: ${e.message}", e)
        } finally {
            connection.releaseChannelToPool(channel)
        }
    }

    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        var channel: ChannelSftp? = null
        val connection = getConnection() ?: return
        try {
            channel = connection.getChannelFromPool() ?: return
            val oldPath = sftpFile.path
            val newPath = "${sftpFile.getParent()?.path ?: ""}/$newName"
            channel.rename(oldPath, newPath)
            fileCache.remove(oldPath)
            val attrs = channel.lstat(newPath)
            val renamedFile = SftpFile(newPath, this, attrs)
            fileCache[newPath] = renamedFile

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
            connection.releaseChannelToPool(channel)
        }
    }

    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        var channel: ChannelSftp? = null
        val connection = getConnection() ?: throw IOException("Failed to create file")
        try {
            channel = connection.getChannelFromPool() ?: throw IOException("Failed to create file")
            val newPath = "${sftpDir.path}/$fileName"
            channel.put(InputStream.nullInputStream(), newPath)
            val attrs = channel.lstat(newPath)
            val newFile = SftpFile(newPath, this, attrs)
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
        val connection = getConnection() ?: throw IOException("Failed to create file")
        try {
            channel = connection.getChannelFromPool() ?: throw IOException("Failed to create file")
            val newPath = "${sftpDir.path}/$dirName"
            channel.mkdir(newPath)
            val attrs = channel.lstat(newPath)
            val newDir = SftpFile(newPath, this, attrs)
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
        val connection = getConnection() ?: throw IOException("Failed to copy file")
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
            val newFile = SftpFile(newPath, this, attrs)
            fileCache[newPath] = newFile
            return newFile
        } catch (_: Exception) {
            throw IOException("Failed to copy file")
        } finally {
            connection.releaseChannelToPool(channel)
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
            var connection = connectionService.getConnection(server.host,server.username)
            if (connection == null || !connection.isConnected()) {
                connectionService.connect(server.host, server.username, server.getPassword(), port = server.port)
                connection = connectionService.getConnection(server.host,server.username)
            }
            connection?.let {
                it.fileSystem = this
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





    fun getChannelFromPool(): ChannelSftp? {
        val connection = getConnection() ?: return null
        return connection.getChannelFromPool()
    }

    fun releaseChannelToPool(channel: ChannelSftp?) {
        val connection = getConnection() ?: return
        connection.releaseChannelToPool(channel)
    }

    fun searchTextInFiles(pattern: String, path: String = server.rootPath): List<SftpFileOccurrence> {
        val results = mutableListOf<SftpFileOccurrence>()
        val connection = getConnection() ?: return results
        var execChannel: ChannelExec? = null
        try {
            // Use grep to search for pattern in files with line numbers and byte offsets
            // The -n option makes grep output line numbers
            // The -b option makes grep output byte offsets of each match
            // The -r option makes grep search recursively
            val escapedPattern = pattern.replace("'", "'\\''") // Escape single quotes for shell
            val grepCommand = "grep -n -b -r -o '$escapedPattern' '$path' 2>/dev/null"

            execChannel = connection.getExecChannelFromPool()
            execChannel?.setCommand(grepCommand)
            execChannel?.connect()
            val input = execChannel?.inputStream
            val foundOccurrences = input?.bufferedReader()?.readLines() ?: emptyList()
            execChannel?.disconnect()

            // Group occurrences by file path
            val fileOccurrences = mutableMapOf<String, MutableList<SftpMatchInfo>>()

            for (occurrence in foundOccurrences) {
                // Parse output format: filepath:line_number:byte_offset:matching line text
                val colonIndices = findFirstNColonIndices(occurrence, 3)
                if (colonIndices.size >= 3) {
                    val filePath = occurrence.substring(0, colonIndices[0])
                    val lineNumber = occurrence.substring(colonIndices[0] + 1, colonIndices[1]).toIntOrNull() ?: 1
                    val byteOffset = occurrence.substring(colonIndices[1]+1  , colonIndices[2]).toIntOrNull() ?: 0
                    val lineContent = occurrence.substring(colonIndices[2] + 1)

                    // Since grep -b returns the byte offset from the start of the line,
                    // we can use it directly as the match offset in the line
                    val matchOffset = byteOffset
                    val matchEndOffset = matchOffset + pattern.length

                    // Add this occurrence to our file matches
                    val fileMatches = fileOccurrences.getOrPut(filePath) { mutableListOf() }
                    fileMatches.add(SftpMatchInfo(lineNumber, matchOffset, matchEndOffset, lineContent))
                }
            }

            // Create SftpFileOccurrence objects for each file with its matches
            for ((filePath, matches) in fileOccurrences) {
                val file = SftpFile(filePath, this)
                results.add(SftpFileOccurrence(file, matches))
            }
        } catch (e: Exception) {
            println("Error searching text in files: ${e.message}")
            e.printStackTrace()
        } finally {
            connection.releaseExecChannelToPool(execChannel)
        }
        return results
    }

    // Helper method to find the first N colon positions in a string
    private fun findFirstNColonIndices(str: String, n: Int): List<Int> {
        val indices = mutableListOf<Int>()
        var pos = -1
        repeat(n) {
            pos = str.indexOf(':', pos + 1)
            if (pos != -1) {
                indices.add(pos)
            } else {
                return indices // Not enough colons found
            }
        }
        return indices
    }

    fun searchFiles(pattern: String, path: String = server.rootPath): List<SftpFile> {
        val results = mutableListOf<SftpFile>()
        if (pattern.length < 2) {
            return results
        }
        val connection = getConnection() ?: return results
        var execChannel: ChannelExec? = null
        try {
            val remotePattern = if (pattern.contains("*")) pattern else "*$pattern*"
            val findCommand = "find '$path' -type f -name '$remotePattern' 2>/dev/null"
            execChannel = connection.getExecChannelFromPool()
            execChannel?.setCommand(findCommand)
            execChannel?.connect()
            val input = execChannel?.inputStream
            val foundFiles = input?.bufferedReader()?.readLines() ?: emptyList()
            execChannel?.disconnect()
            for (filePath in foundFiles) {
                results.add(SftpFile(filePath, this))
                println("Found file: $filePath")
            }
        } catch (e: Exception) {
            println(e.message)
            e.printStackTrace()
        } finally {
            connection.releaseExecChannelToPool(execChannel)
        }
        return results
    }

    fun listFolderPaths(path: String = server.rootPath): List<String> {
        val results = mutableListOf<String>()
        val connection = getConnection() ?: return results
        var execChannel: ChannelExec? = null
        try {
            val findCommand = "find '$path' -mindepth 1 -maxdepth 1 -type d 2>/dev/null"
            execChannel = connection.getExecChannelFromPool()
            execChannel?.setCommand(findCommand)
            execChannel?.connect()
            val input = execChannel?.inputStream
            val foundDirs = input?.bufferedReader()?.readLines() ?: emptyList()
            execChannel?.disconnect()
            results.addAll(foundDirs)
        } catch (e: Exception) {
            println(e.message)
            e.printStackTrace()
        } finally {
            connection.releaseExecChannelToPool(execChannel)
        }
        return results
    }

}