package com.connecthid.intellij.connection.sftp

import com.connecthid.intellij.connection.ssh.SSHJConnection
import com.connecthid.intellij.models.Server
import com.connecthid.sshjpool.SSHConnection
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import net.schmizz.sshj.sftp.*
import net.schmizz.sshj.xfer.FilePermission
import org.slf4j.LoggerFactory
import java.io.*
import java.util.concurrent.atomic.AtomicInteger

class SftpFile(
    var pathLocation: String,
    val server: Server,
    var fileEntry: FileAttributes? = null,
    var fileType:FileMode.Type?=null
) : VirtualFile() {
    private val log = LoggerFactory.getLogger(SftpFile::class.java)
    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192
    }

    private val sftpFileSystem by lazy {
        VirtualFileManager.getInstance().getFileSystem(SftpFileSystem.PROTOCOL) as SftpFileSystem
    }
    private val activeStreams = AtomicInteger(0)  // Tracks open streams for coordinated release

    private var children: Array<VirtualFile>? = null
    private var connectionPool: Pair<SSHConnection, SFTPClient>? = null
    private var connection: SSHJConnection? = null
    private var inputHandle: RemoteFile? = null
    private var outputHandle: RemoteFile? = null

    override fun getFileSystem(): SftpFileSystem = sftpFileSystem

    override fun getName(): String = pathLocation.substringAfterLast('/')

    override fun getUrl(): String =
        "${fileSystem.protocol}://${server.username}@${server.host}:${server.port}$pathLocation"

    override fun isWritable(): Boolean {
        if (fileEntry == null)
            fileEntry = runReadAction { fileSystem.getFileStat(pathLocation, server) }
        return fileEntry?.isWritable() ?: false
    }

    override fun isDirectory(): Boolean {
        if (fileType == null){
            fileEntry = fileEntry ?: runReadAction { fileSystem.getFileStat(pathLocation, server) }
            fileType = fileEntry?.type
        }
        return fileType == FileMode.Type.DIRECTORY
    }

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? {
        val parentPath = pathLocation.substringBeforeLast("/", "")
        return if (parentPath.isEmpty()) null else SftpFile(parentPath, server,fileType = FileMode.Type.DIRECTORY)
    }

    override fun getChildren(): Array<VirtualFile> {
        log.debug("getChildren $pathLocation")
        return run {
            children?.forEach {
                fileSystem.fileCache.remove(it.url)
            }
            children ?: run {
                val connection = fileSystem.connectionService.getConnection(server)
                    ?: return emptyArray()
                connection.withSftp { sftp ->
                    val currentPath = if (pathLocation == "/") "." else pathLocation
                    val files = sftp.ls(currentPath)

                    val entries = files
                        .filter { it.name != "." && it.name != ".." }
                        .filter { fileSystem.showHiddenFiles || !it.name.startsWith(".") }

                    val sorted = entries.sortedWith(
                        compareBy<RemoteResourceInfo>(
                            { !it.isDirectory },
                            { it.name.lowercase() })
                    )

                    children = sorted.map {
                        val childPath =
                            if (pathLocation == "/") "/${it.name}" else "$pathLocation/${it.name}"
                        SftpFile(childPath, server, it.attributes,it.attributes.type).also {
                            sftpFileSystem.fileCache.put(it.url,it)
                        }
                    }.toTypedArray()
                }
                children ?: emptyArray()
            }
        }
    }

    override fun getTimeStamp(): Long {
        if (fileEntry == null)
            fileEntry = runReadAction { fileSystem.getFileStat(pathLocation, server) }
        return fileEntry?.atime ?: 0L
    }

    override fun getModificationStamp(): Long {
        if (fileEntry == null)
            fileEntry = runReadAction { fileSystem.getFileStat(pathLocation, server) }
        return fileEntry?.mtime ?: 0L
    }

    override fun getLength(): Long {
        if (fileEntry == null)
            fileEntry = runReadAction { fileSystem.getFileStat(pathLocation, server) }
        return fileEntry?.size ?: 0L
    }

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        log.debug("Refreshing $pathLocation")
        children?.forEach {
          fileSystem.fileCache.remove(it.url)
        }
        children = null
//        if (recursive && isDirectory) {
//            getChildren().forEach { it.refresh(asynchronous, recursive, null) }
//        }
        fileEntry = null
        postRunnable?.run()
    }

    override fun equals(other: Any?): Boolean =
        other is SftpFile && other.pathLocation == pathLocation && other.server == server

    override fun hashCode(): Int = (pathLocation + server.host).hashCode()

    override fun toString(): String = "SftpFile: $pathLocation"

    /**
     * Opens and returns a RemoteFile.
     * Ensures a pooled SFTPClient is used and returned properly.
     */
    @Synchronized
    private fun getSftpClient(): Pair<SSHConnection, SFTPClient>? {
        if (connectionPool == null) {
            connection = fileSystem.connectionService.getConnection(server)
                ?: throw IOException("Unable to connect to server")
            connectionPool = connection!!.getSftpClient()

        }
        return connectionPool
    }

    override fun getInputStream(): InputStream {
        log.debug("Opening input stream for $pathLocation")
        return ReadAction.compute<InputStream, RuntimeException> {
            synchronized(this) {
                activeStreams.incrementAndGet()
            }
            val sftp = getSftpClient()?.second ?: throw IOException("Unable to connect to server")
            this.inputHandle = sftp.open(pathLocation, setOf(OpenMode.READ))
            object : FilterInputStream(inputHandle!!.RemoteFileInputStream()) {
                private var closed = false
                override fun close() {
                    if (closed) return
                    closed = true
                    try {
                        super.close()
                        inputHandle?.close()
                        inputHandle = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        maybeDispose()
                    }
                }
            }
        }
    }

    override fun getOutputStream(requestor: Any?, modStamp: Long, timeStamp: Long): OutputStream {
        log.debug("Opening output stream for $pathLocation")
        return WriteAction.compute<OutputStream, RuntimeException> {
            synchronized(this) {
                activeStreams.incrementAndGet()
            }
            val sftp = getSftpClient()?.second ?: throw IOException("Unable to connect to server")
            this.outputHandle = sftp.open(pathLocation, setOf(OpenMode.WRITE))

            object : FilterOutputStream(outputHandle!!.RemoteFileOutputStream()) {
                private var closed = false
                override fun close() {
                    if (closed) return
                    closed = true
                    try {
                        super.close()
                        outputHandle?.close()
                        outputHandle = null
                    } finally {
                        maybeDispose()
                    }
                }
            }
        }
    }

    override fun contentsToByteArray(): ByteArray {
        log.debug("Reading file contents from $pathLocation")
        return ReadAction.compute<ByteArray, RuntimeException> {
            try {
                val sftp = getSftpClient()?.second ?: throw IOException("Unable to connect to server")
                // Open temporary handle for direct read (no stream wrapper or activeStreams impact)
                sftp.open(pathLocation, setOf(OpenMode.READ)).use { handle ->
                    // Wrap input stream in use for proper closure
                    handle.RemoteFileInputStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        val output = ByteArrayOutputStream()
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.toByteArray()
                    }
                }
            } catch (e: Exception) {
                throw IOException("Failed to read file contents from $pathLocation", e)
            }
        }
    }

    override fun getPath(): String = pathLocation

    /**
     * Public cleanup method: Force release SFTP client (ignores active streams).
     * Call this manually (e.g., via VFS listeners) or from parent Disposable.
     * Set isValid = false afterward if invalidating the file.
     */
    fun dispose() {  // No 'override' – it's a custom method
        activeStreams.set(0)  // Force to zero
        internalDispose()
    }

    /**
     * Releases client only if no active streams remain.
     * Call this from stream closes or manual dispose.
     */
    private fun maybeDispose() {
        log.debug("Maybe releasing SFTP client for $pathLocation")
        if (activeStreams.decrementAndGet() == 0) {
            internalDispose()
        }
    }

    private fun internalDispose() {
        log.debug("Releasing SFTP client for $pathLocation")
        synchronized(this) {
            connectionPool?.let { (conn, client) ->
                connection!!.sshPool.returnSftpClient(conn, client)
            }
            connectionPool = null
            connection = null
        }
    }
}

/**
 * Extension helpers for readability
 */
fun FileAttributes.isWritable(): Boolean =
    this.permissions?.contains(FilePermission.USR_W) ?: false

fun FileAttributes.isDirectory(): Boolean =
    this.type == FileMode.Type.DIRECTORY

fun FileAttributes.isRegularFile(): Boolean =
    this.type == FileMode.Type.REGULAR

fun FileAttributes.isSymlink(): Boolean =
    this.type == FileMode.Type.SYMLINK
