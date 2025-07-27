package com.connecthid.intellij.connection.vfs

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SftpFile(
    var pathLocation: String,
    private val fileSystem: SftpFileSystem,
    var fileEntry:SftpATTRS? = null
) : VirtualFile() {
    private var children: Array<VirtualFile>? = null
    override fun getFileSystem(): VirtualFileSystem = fileSystem

    override fun getName(): String = pathLocation.split("/").last()

    override fun isWritable(): Boolean {
        if(fileEntry == null){
            fileEntry = runReadAction {fileSystem.getFileStat(pathLocation)}
            return fileEntry != null && fileEntry!!.isWritable()
        }
        return fileEntry!!.isWritable()
    }

    override fun getUrl(): String {
        return "${fileSystem.protocol}://${fileSystem.server.username}@${fileSystem.server.host}:${fileSystem.server.port}${pathLocation}"
    }


    override fun isDirectory(): Boolean {
        if (fileEntry == null) {
            fileEntry = runReadAction {fileSystem.getFileStat(pathLocation)}
            return fileEntry == null || fileEntry!!.isDir
        }
        return fileEntry!!.isDir
    }

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? {
        val parentPath = pathLocation.substringBeforeLast("/", "")
        return if (parentPath.isEmpty()) null else SftpFile(parentPath, fileSystem)
    }


    override fun getChildren(): Array<VirtualFile> {
        return runReadAction {
        if (children == null) {
            var channel: ChannelSftp? = null
            try {
                channel = fileSystem.getChannelFromPool()
                if (channel == null || !channel.isConnected) {
                    println("Failed to establish SFTP channel")
                    return@runReadAction emptyArray()
                }
                val currentPath = if (pathLocation == "/") "." else pathLocation
                val files = channel.ls(currentPath) ?: return@runReadAction emptyArray()
                children = files
                    .mapNotNull { it as? ChannelSftp.LsEntry }
                    .filter { it.filename != "." && it.filename != ".." }
                    .map {
                        val childPath = if (pathLocation == "/") "/${it.filename}" else "$pathLocation/${it.filename}"
                        SftpFile(childPath, fileSystem, it.attrs)
                    }
                    .toTypedArray()
            } catch (e: Exception) {
                println("Error listing directory: ${e.message}")
                e.printStackTrace()
                return@runReadAction emptyArray()
            } finally {
                fileSystem.releaseChannelToPool(channel)
            }
        }
         children ?: emptyArray()
        }
    }


    override fun getTimeStamp(): Long {
        if (fileEntry == null) {
            fileEntry = runReadAction {fileSystem.getFileStat(pathLocation)}
            return  fileEntry ?.mTime?.toLong() ?: 0L
        }
        return fileEntry!!.aTime.toLong()

    }

    override fun getModificationStamp(): Long {
        if (fileEntry == null) {
            fileEntry = runReadAction {fileSystem.getFileStat(pathLocation)}
            return  fileEntry ?.mTime?.toLong() ?: 0L
        }
        return fileEntry!!.mTime.toLong()
    }

    override fun getLength(): Long {
        if (fileEntry == null) {
            fileEntry = runReadAction {fileSystem.getFileStat(pathLocation)}
            return  fileEntry?.size?.toLong() ?: 0L
        }
        return fileEntry!!.size.toLong()
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
        var channel: ChannelSftp? = null
        try {
            channel = fileSystem.getChannelFromPool() ?: throw IOException("Failed to get SFTP channel")
            return channel.put(pathLocation) ?: throw IOException("Failed to get output stream")
        } catch (e: Exception) {
            throw IOException("Failed to get output stream: ${e.message}", e)
        } finally {
            fileSystem.releaseChannelToPool(channel)
        }
    }

    override fun contentsToByteArray(): ByteArray {
        return runReadAction {
            var channel: ChannelSftp? = null
            try {
                channel = fileSystem.getChannelFromPool() ?: throw IOException("Failed to get SFTP channel")
                val inputStream = channel.get(pathLocation) ?: throw IOException("Failed to get input stream")
                return@runReadAction inputStream.readBytes()
            } catch (e: Exception) {
                throw IOException("Failed to read file contents: ${e.message}", e)
            } finally {
                fileSystem.releaseChannelToPool(channel)
            }
        }
    }

    override fun getInputStream(): InputStream {
        return runReadAction {
            var channel: ChannelSftp? = null
            try {
                channel = fileSystem.getChannelFromPool() ?: throw IOException("Failed to get SFTP channel")
                val inputStream = channel.get(pathLocation) ?: throw IOException("Failed to get input stream")
                return@runReadAction object : InputStream() {
                    private var isClosed = false

                    override fun read(): Int = inputStream.read()

                    override fun read(b: ByteArray): Int = inputStream.read(b)

                    override fun read(b: ByteArray, off: Int, len: Int): Int = inputStream.read(b, off, len)

                    override fun skip(n: Long): Long = inputStream.skip(n)

                    override fun available(): Int = inputStream.available()

                    override fun close() {
                        if (isClosed) return
                        isClosed = true
                        try {
                            inputStream.close()
                        } catch (e: Exception) {
                            println("Error closing input stream: ${e.message}")
                        } finally {
                            fileSystem.releaseChannelToPool(channel)
                        }
                    }
                }
            } catch (e: Exception) {
                fileSystem.releaseChannelToPool(channel)
                throw IOException("Failed to get input stream: ${e.message}", e)
            }
        }
    }

    override fun getPath(): String = pathLocation


}

fun SftpATTRS.isWritable(): Boolean {
    val permissions = this.permissions // This is an int
    // Check if owner has write permission (bitmask 0o200 == 128)
    val S_IWUSR = 0b10_0000_000 // Octal 0200 == decimal 128
    return (permissions and S_IWUSR) != 0
}
