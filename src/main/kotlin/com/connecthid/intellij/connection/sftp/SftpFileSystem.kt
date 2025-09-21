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


    private val connectionLock = ReentrantLock()
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









    fun getChannelFromPool(server: Server): ChannelSftp? {
        val connection = getConnection(server) ?: return null
        return connection.getChannelFromPool()
    }

    fun releaseChannelToPool(channel: ChannelSftp?,server: Server) {
        val connection = getConnection(server) ?: return
        connection.releaseChannelToPool(channel)
    }

    fun searchTextInFiles(
        server: Server,
        pattern: String,
        path: String = server.rootPath,
        word: Boolean = false,
        case: Boolean = false,
        regexp: Boolean = false
    ): List<SftpFileOccurrence> {
        val results = mutableListOf<SftpFileOccurrence>()
        val connection = getConnection(server) ?: return results
        var execChannel: ChannelExec? = null
        try {
            // Build grep options based on flags
            val options = mutableListOf("-n", "-b", "-r", "-o")
            if (word) options.add("-w")
            if (!case) options.add("-i")
            if (!regexp) options.add("-F")
            // Exclude hidden files and folders
            val escapedPattern = pattern.replace("'", "'\\''")
            val grepCommand = "grep ${options.joinToString(" ")} --exclude-dir='.*' --exclude='.*' '$escapedPattern' '$path' 2>/dev/null"

            println("Searching with command: $grepCommand")

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
                    val matchEndOffset = matchOffset + lineContent.count()

                    // Add this occurrence to our file matches
                    val fileMatches = fileOccurrences.getOrPut(filePath) { mutableListOf() }
                    fileMatches.add(SftpMatchInfo(lineNumber, matchOffset, matchEndOffset, lineContent))
                }
            }

            // Create SftpFileOccurrence objects for each file with its matches
            for ((filePath, matches) in fileOccurrences) {
                val file = SftpFile(filePath, server)
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

    fun searchFiles(
        server: Server,
        pattern: String,
        path: String = server.rootPath,
        word: Boolean = false,
        case: Boolean = false,
        regexp: Boolean = false
    ): List<SftpFile> {
        val results = mutableListOf<SftpFile>()
        if (pattern.length < 2) {
            return results
        }
        val connection = getConnection(server) ?: return results
        var execChannel: ChannelExec? = null
        try {
            // Build find command based on flags
            val findOptions = mutableListOf<String>()
            var findPattern = pattern
            var findFlag = "-name"
            if (regexp) {
                findFlag = "-regex"
                // For regex, pattern should be a full path regex, so prepend '.*' if not present
                if (!findPattern.startsWith(".*")) findPattern = ".*" + findPattern
            } else if (word) {
                // For word, match exact file name
                findFlag = "-name"
                findPattern = pattern
            } else {
                // Default: substring match
                findFlag = if (case) "-name" else "-iname"
                findPattern = if (pattern.contains("*")) pattern else "*$pattern*"
            }
            // Exclude hidden files and folders
            val excludeHidden = "! -path '*/.*'"
            val findCommand = "find '$path' -type f $excludeHidden $findFlag '$findPattern' 2>/dev/null"

            println("Searching with command: $findCommand")
            execChannel = connection.getExecChannelFromPool()
            execChannel?.setCommand(findCommand)
            execChannel?.connect()
            val input = execChannel?.inputStream
            val foundFiles = input?.bufferedReader()?.readLines() ?: emptyList()
            execChannel?.disconnect()
            for (filePath in foundFiles) {
                results.add(SftpFile(filePath, server))
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

    fun listFolderPaths(server: Server,path: String = server.rootPath): List<String> {
        val results = mutableListOf<String>()
        val connection = getConnection(server) ?: return results
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

    fun archiveFile(server: Server,virtualFile: VirtualFile): VirtualFile?{
        val sftpFile = virtualFile as? SftpFile ?: return null
        var channel: ChannelExec? = null
        val connection = getConnection(server) ?: return null
        try {
            channel = connection.getExecChannelFromPool() ?: return null
            val parentPath = sftpFile.getParent()?.path ?: return null
            val fileName = sftpFile.name
            val zipFileName = if(sftpFile.isDirectory) "$fileName.zip" else "$fileName.zip"
            val zipFilePath = "$parentPath/$zipFileName"
            val zipCommand = if(sftpFile.isDirectory){
                "cd '$parentPath' && zip -r '$zipFileName' '$fileName'"
            }else{
                "cd '$parentPath' && zip '$zipFileName' '$fileName'"
            }
            println("Zipping with command: $zipCommand")
            channel.setCommand(zipCommand)
            channel.connect()
            while (!channel.isClosed) {
                Thread.sleep(100)
            }
            val exitStatus = channel.exitStatus
            if (exitStatus == 0) {
                println("Zip created at: $zipFilePath")
                // Invalidate cache for parent directory to reflect new zip file
                fileCache.remove(parentPath)
                return findFileByPath(zipFilePath)
            } else {
                println("Failed to create zip. Exit status: $exitStatus")
                return null
            }
        } catch (e: Exception) {
            println("Error zipping file: ${e.message}")
            e.printStackTrace()
            return  null
        } finally {
            connection.releaseExecChannelToPool(channel)
        }
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
            FileTypeChooser.associateFileType(extension)
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