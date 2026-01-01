package com.connecthid.intellij.connection.sftp

import com.connecthid.intellij.connection.ssh.SSHJConnection
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.utils.Utils.parseSftpUrl
import com.connecthid.intellij.utils.isWindows
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileSystem
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*


class SftpFileSystem : VirtualFileSystem() {
    // Use lazy initialization to defer service access until actually needed
    val connectionService by lazy { getSSHService() }
    val fileCache = mutableMapOf<String, SftpFile>()
    private val listeners = mutableListOf<VirtualFileListener>()
    var showHiddenFiles = true // This can be a setting in the future to toggle hidden files visibility

    companion object {
        const val PROTOCOL = "sftp"
    }
    private val log = LoggerFactory.getLogger(SftpFileSystem::class.java)


    override fun getProtocol(): String = PROTOCOL
    @Synchronized
    override  fun findFileByPath( path: String): VirtualFile? {
        try {
            var remotepath = path
            if (!path.startsWith("sftp://")) remotepath = "sftp://$path"
            val url  = parseSftpUrl(remotepath)
            val server = connectionService.getServer("${url.username}@${url.host}") ?: return null
            log.debug("url:${remotepath}")
            fileCache.forEach {
                log.debug("cached files :${it.key}")
            }
            return fileCache.getOrPut(remotepath) {
                getFile(url.path,server)
            }
        } catch (ex: Exception){
            println(ex.message)
            return null
        }

    }
    @Synchronized
    private fun getFile(path: String,server: Server): SftpFile {
        return SftpFile(path, server)
    }
    @Synchronized
    fun  getFileStat(path: String,server: Server): FileAttributes?{
        val connection = getConnection(server) ?: return null
        return connection.withSftp {
            it.lstat(path)
        }
    }


    override fun refresh(asynchronous: Boolean) {

        fileCache.clear()
    }

    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        refresh(false)
        return findFileByPath(path)
    }

    private fun deleteRecursiveSftp(channel: SFTPClient, vFile: VirtualFile) {
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
        val sftpFile = vFile as? SftpFile ?: return
        val connection = getConnection(sftpFile.server) ?: return
        connection.withSftp {
            if(sftpFile.server.systemInfo.osName.isWindows() || !vFile.isDirectory){
                if (vFile.isDirectory) {
                    if (vFile.children.isNotEmpty()) {
                        // ✅ Reuse the same channel for the whole recursion
                        deleteRecursiveSftp(it, vFile)
                    } else {
                        it.rmdir(vFile.path)
                    }
                } else {
                    it.rm(vFile.path)
                }
            } else if(vFile.isDirectory){
                connection.execute("rm -rf '${vFile.path}'")
            }
            fileCache.remove(vFile.path)
        }
    }

    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val newParentSftp = newParent as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = getConnection(sftpFile.server) ?: return
        connection.withSftp {
            val newPath = "${newParentSftp.path}/${sftpFile.getName()}"
            it.rename(sftpFile.path, newPath)
            fileCache.remove(sftpFile.path)
            val attrs = it.lstat(newPath)
            fileCache[newPath] = SftpFile(newPath, sftpFile.server, attrs)
        }
    }

    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        val sftpFile = vFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = getConnection(sftpFile.server) ?: return
        connection.withSftp {
            val oldPath = sftpFile.path
            val newPath = "${sftpFile.getParent()?.path ?: ""}/$newName"
            it.rename(oldPath, newPath)
            fileCache.remove(oldPath)
            val attrs = it.lstat(newPath)
            val renamedFile = SftpFile(newPath, sftpFile.server, attrs)
            fileCache[newPath] = renamedFile
            requestor?.let {
                val callback = it as? (renamedFile: SftpFile) -> Unit
                callback?.invoke(renamedFile)
            }
        }

    }

    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = getConnection(sftpDir.server) ?: throw IOException("Failed to create file")
        return connection.withSftp {
            val newPath = "${sftpDir.path}/$fileName"
            val file = it.open(
                newPath,
                EnumSet.of(OpenMode.CREAT, OpenMode.WRITE, OpenMode.TRUNC)
            )
            val newFile = SftpFile(newPath, sftpDir.server, file.fetchAttributes())
            fileCache[newPath] = newFile
            return@withSftp newFile
        }
    }

    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        val sftpDir = vDir as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = getConnection(sftpDir.server) ?: throw IOException("Failed to create file")
        return connection.withSftp {
            val newPath = "${sftpDir.path}/$dirName"
            it.mkdir(newPath)
            val attrs = it.lstat(newPath)
            val newDir = SftpFile(newPath, sftpDir.server, attrs)
            fileCache[newPath] = newDir
            return@withSftp newDir
        }
    }

    override fun copyFile(requestor: Any?, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
        val sftpFile = virtualFile as? SftpFile ?: throw IOException("Not an SFTP file")
        val newParentSftp = newParent as? SftpFile ?: throw IOException("Not an SFTP file")
        val connection = getConnection(sftpFile.server) ?: throw IOException("Failed to copy file")
        return connection.withSftp {
            val newPath = "${newParentSftp.path}/$copyName"
            if (sftpFile.isDirectory) {
                it.mkdir(newPath)
            } else {
                it.open(virtualFile.path, EnumSet.of(OpenMode.READ)).use { srcFile ->
                    it.open(newPath, EnumSet.of(OpenMode.CREAT, OpenMode.WRITE, OpenMode.TRUNC)).use { destFile ->
                        // 8kb per chuck
                        val buffer = ByteArray(8192)
                        var offset = 0L
                        var read: Int
                        while (true) {
                            read = srcFile.read(offset, buffer, 0, buffer.size)
                            if (read <= 0) break
                            destFile.write(offset, buffer, 0, read)
                            offset += read
                        }
                    }
                }
            }
            val attrs = it.lstat(newPath)
            val newFile = SftpFile(newPath, sftpFile.server, attrs)
            fileCache[newPath] = newFile
            return@withSftp newFile
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


    internal fun getConnection(server: Server): SSHJConnection? {
        return connectionService.getConnection(server)
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

fun SFTPClient.mkdirIfNotExists(remoteDir: String) {
    val path = if (remoteDir.endsWith("/")) remoteDir.dropLast(1) else remoteDir
    val parts = path.split("/").filter { it.isNotEmpty() }

    var current = ""
    for (part in parts) {
        current += "/$part"

        try {
            // Check if directory exists
            val attrs = stat(current)
            if (attrs.type != FileMode.Type.DIRECTORY) {
                throw RuntimeException("Remote path exists but is not a directory: $current")
            }
        } catch (e: Exception) {
            // Directory does not exist → create it
            println("Creating remote directory: $current")
            mkdir(current)
        }
    }
}

fun SFTPClient.updateMTime(remotePath: String, millis: Long) {
    val seconds = (millis / 1000)

    val attrs = FileAttributes.Builder()
        .withAtimeMtime(seconds,seconds)
        .build()

    this.setattr(remotePath, attrs)
}


fun SFTPClient.uploadIfMTimeDifferent( localPath: String, remotePath: String) {
    val localTime = getLocalMTime(localPath)
    val remoteTime = getRemoteMTime(this, remotePath)
    if (remoteTime == null) {
        mkdirIfNotExists(remotePath)
        println("Remote file missing → Uploading")
        this.put(localPath, remotePath)
        return
    }
    if (remoteTime != localTime) {
        println("mtime differs → Uploading")
        this.put(localPath, remotePath)
        // Optional: update remote file mtime to match local
        this.updateMTime(remotePath, localTime)
    } else {
        println("mtime is same → Skipping upload")
    }
}

fun SFTPClient.getRemoteMTime(sftp: SFTPClient, remotePath: String): Long? {
    return try {
        val attrs = sftp.stat(remotePath)
        attrs.mtime * 1000   // convert seconds → milliseconds
    } catch (e: Exception) {
        null // file does not exist
    }
}

fun getLocalMTime(path: String): Long {
    return File(path).lastModified()
}