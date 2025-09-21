package com.connecthid.intellij.connection.sftp

import com.connecthid.intellij.utils.isWindows
import com.connecthid.intellij.utils.showNotification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import java.io.File
import java.io.IOException
import kotlin.to

class SftpDownloadTask private constructor(
    project: Project,
    private val downloads: List<Pair<SftpFile, File>>,
    private val callback: (Boolean, String) -> Unit,
) : Task.Backgroundable(
    project,
    buildTitle(downloads),
    true
) {

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false

        try {
            val total = downloads.size
            for ((index, pair) in downloads.withIndex()) {
                val (remote, local) = pair
                if (indicator.isCanceled) return

                indicator.text = "Downloading ${remote.name}"
                indicator.text2 = "File ${index + 1} of $total → ${local.name}"
                if(remote.isDirectory) {
                    // zip directory before download
                    val zipName = "${local.nameWithoutExtension}.zip"
                    val zipFile = File(local.parentFile, zipName)
                    //remote.zipToLocal(zipFile, indicator)
                } else {
                    downloadFile(remote, local,{ indicator.isCanceled }) { it ->
                        indicator.fraction = it
                        indicator.text2 = "File ${index + 1} of $total — ${(it*100).toInt()}%"
                    }
                }

            }
            if (!indicator.isCanceled) {
                callback(true, "Downloaded $total file(s)")
            }
        } catch (e: Exception) {
            callback(false, e.message ?: "Unknown error")
        }
    }
    private fun downloadFile(
        remote: SftpFile,
        local: File,
        cancelled: () -> Boolean,
        progress: (Double) -> Unit   // 0–100 %
    ) {
        val tempFile = File(local.parentFile, "${local.name}.part")

        val fileSystem = remote.fileSystem

        val channel = fileSystem.getChannelFromPool(remote.server)
            ?: throw IOException("Failed to get SFTP channel")

        try {
            val monitor = object : SftpProgressMonitor {
                private var transferred: Long = 0
                private var fileSize: Long = 0
                private var lastPercent = 0.0

                override fun init(op: Int, src: String?, dest: String?, max: Long) {
                    fileSize = max
                    transferred = 0
                    lastPercent = 0.0
                }

                override fun count(count: Long): Boolean {
                    if (cancelled()) return false // abort transfer
                    transferred += count
                    if (fileSize > 0) {
                        val percent = (transferred.toDouble() / fileSize)
                        println(percent)
                        if (percent > lastPercent) {
                            lastPercent = percent
                            progress(percent)
                        }
                    }
                    return true
                }

                override fun end() {
                    // called when done or aborted
                }
            }

            // synchronous → blocks until complete or cancelled
            channel.get(remote.path, tempFile.absolutePath, monitor)

            // replace final file atomically
            if (!cancelled()) {
                if (local.exists()) local.delete()
                if (!tempFile.renameTo(local)) {
                    throw IOException("Failed to rename ${tempFile.name} → ${local.name}")
                }
            } else {
                tempFile.delete()
            }

        } catch (e: Exception) {
            tempFile.delete()
            println(e.message)
        } finally {
            fileSystem.releaseChannelToPool(channel,remote.server)
        }
    }

    override fun onCancel() {
        super.onCancel()
        callback(false, "Cancelled")
    }

    override fun onSuccess() {
        super.onSuccess()
        // success already reported
    }

    companion object {
        private fun buildTitle(downloads: List<Pair<SftpFile, File>>): String =
            when {
                downloads.size == 1 -> "Downloading ${downloads.first().first.name}..."
                downloads.isNotEmpty() -> "Downloading ${downloads.size} files..."
                else -> "Downloading..."
            }
    }

    // ---------- Builder ----------
    class Builder(private val project: Project) {
        private var downloads: List<Pair<SftpFile, File>> = emptyList()
        private var callback: (Boolean, String) -> Unit = { _, _ -> }

        fun downloads(list: List<Pair<SftpFile, File>>) = apply { this.downloads = list }
        fun callback(cb: (Boolean, String) -> Unit) = apply { this.callback = cb }

        fun build(): SftpDownloadTask {
            require(downloads.isNotEmpty()) { "At least one download must be provided" }
            return SftpDownloadTask(project, downloads, callback)
        }
    }
}


class SftpUploadTask private constructor(
    project: Project,
    private val uploads: List<File>,   // unified list of local→remoteDir
    private val remoteDir: SftpFile,
    private val callback: (Boolean, String) -> Unit,
) : Task.Backgroundable(
    project,
    buildTitle(uploads),
    true
) {

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false

        try {
            val totalFiles = uploads.size
            var processedFiles = 0
            for (local in uploads) {
                if (indicator.isCanceled) return

                indicator.text = "Uploading ${local.name}"
                indicator.text2 = "File ${processedFiles + 1} of $totalFiles → ${remoteDir.name}"

                uploadFile(local, remoteDir,{ indicator.isCanceled }) { it ->
                    indicator.fraction = it
                    indicator.text2 = "File ${processedFiles + 1} of $totalFiles — ${(it*100).toInt()}%"
                }
                processedFiles++
            }
            if (!indicator.isCanceled){
                callback(true, "Uploaded $totalFiles file(s)")
            }
        } catch (e: Exception) {
            callback(false, e.message ?: "Unknown error")
        }
    }



    private fun uploadFile(local: File, remoteDir: SftpFile,cancelled: () -> Boolean, progress: (Double) -> Unit) {
        if (!local.exists() || !local.isFile) throw IllegalArgumentException("Local file does not exist: ${local.path}")
        val fileSystem  = remoteDir.fileSystem
        var channel: ChannelSftp? = null
        try {
            channel = fileSystem.getChannelFromPool(remoteDir.server) ?: throw IOException("Failed to get SFTP channel")
            val outputStream = channel.put("${remoteDir.pathLocation}/${local.name}") ?: throw IOException("Failed to get output stream")
            local.inputStream().use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied: Long = 0
                    val totalBytes = local.length()

                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        if (cancelled()) return
                        output.write(buffer, 0, bytes)
                        bytesCopied += bytes

                        // update fraction if size is known
                        if (totalBytes > 0) {
                            val fileProgress = bytesCopied.toDouble() / totalBytes
                            progress((fileProgress))
                        }
                        bytes = input.read(buffer)
                    }
                }
            }
        }catch (e: Exception) {
            println(e.message)
        } finally {
            fileSystem.releaseChannelToPool(channel,remoteDir.server)
        }
    }
    override fun onCancel() {
        super.onCancel()
        callback(false, "Cancelled")
    }
    override fun onSuccess() {
        super.onSuccess()
        // success already reported
    }
    companion object {
        private fun buildTitle(uploads: List<File>): String =
            when {
                uploads.size== 1 -> "Uploading ${uploads.first().name}..."
                uploads.isNotEmpty() -> "Uploading ${uploads.size} files..."
                else -> "Uploading..."
            }
    }
    // ---------- Builder ----------
    class Builder(private val project: Project) {
        private var uploads: List<File> = emptyList()
        private lateinit var remoteDir: SftpFile
        private var callback: (Boolean, String) -> Unit = { _, _ -> }
        fun uploads(list: List<File>) = apply { this.uploads = list }
        fun remoteDir(dir: SftpFile) = apply {
            this.remoteDir= dir
        }
        fun callback(cb: (Boolean, String) -> Unit) = apply {
            this.callback =
                cb
        }
        fun build(): SftpUploadTask {
            require(uploads.isNotEmpty()) { "At least one upload must be provided" }
            return SftpUploadTask(project, uploads,remoteDir, callback)
        }
    }
}


class SftpCopyTask private constructor(
    project: Project,
    private val filesToCopy: List<SftpFile>,   // unified list of local→remoteDir
    private val remoteDir: SftpFile,
    private val callback: (Boolean, String) -> Unit,
) : Task.Backgroundable(
    project,
    buildTitle(filesToCopy),
    true
) {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        try {
            val total = filesToCopy.size
            for ((index, remote) in filesToCopy.withIndex()) {
                if (indicator.isCanceled) return

                indicator.text = "Copying ${remote.name}"
                indicator.text2 = "File ${index + 1} of $total → ${remoteDir.name}"

                copyFile(remote, remoteDir,{ indicator.isCanceled }) { it ->
                    indicator.fraction = it
                    indicator.text2 = "File ${index + 1} of $total — ${(it*100).toInt()}%"
                }

                indicator.fraction = (index + 1).toDouble() / total
            }
            if (!indicator.isCanceled) {
                callback(true, "Copied $total file(s)")
            }
        } catch (e: Exception) {
            callback(false, e.message ?: "Unknown error")
        }

    }

    private fun copyFile(targetFile: SftpFile, destinationFile: SftpFile,cancelled: () -> Boolean, progress: (Double) -> Unit) {
        val fileSystem  = remoteDir.fileSystem
        if(!targetFile.server.systemInfo.osName.isWindows()){
            fileSystem.copyFile(targetFile,destinationFile)
            progress(1.0)
        } else {

        }
    }


    companion object {
        private fun buildTitle(filesToCopy: List<SftpFile>): String =
            when {
                filesToCopy.size== 1 -> "Copying ${filesToCopy.first().name}..."
                filesToCopy.isNotEmpty() -> "Copying ${filesToCopy.size} files..."
                else -> "Copying..."
            }
    }

    // ---------- Builder ----------
    class Builder(private val project: Project) {
        private var filesToCopy: List<SftpFile> = emptyList()
        private lateinit var remoteDir: SftpFile
        private var callback: (Boolean, String) -> Unit = { _, _ -> }
        fun filesToCopy(list: List<SftpFile>) = apply { this.filesToCopy = list }
        fun remoteDir(dir: SftpFile) = apply {
            this.remoteDir= dir
        }
        fun callback(cb: (Boolean, String) -> Unit) = apply {
            this.callback =
                cb
        }
        fun build(): SftpCopyTask {
            require(filesToCopy.isNotEmpty()) { "At least one upload must be provided" }
            return SftpCopyTask(project, filesToCopy,remoteDir, callback)
        }
    }

}

class SftpMoveTask private constructor(
    project: Project,
    private val filesToMove: List<SftpFile>,   // unified list of local→remoteDir
    private val remoteDir: SftpFile,
    private val callback: (Boolean, String) -> Unit,
) : Task.Backgroundable(
    project,
    buildTitle(filesToMove),
    true
){
    override fun run(p0: ProgressIndicator) {
        p0.isIndeterminate = false
        try {
            val total = filesToMove.size
            for ((index, remote) in filesToMove.withIndex()) {
                if (p0.isCanceled) return

                p0.text = "Moving ${remote.name}"
                p0.text2 = "File ${index + 1} of $total → ${remoteDir.name}"

                moveFile(remote, remoteDir,{ p0.isCanceled }) { it ->
                    p0.fraction = it
                    p0.text2 = "File ${index + 1} of $total — ${(it*100).toInt()}%"
                }

                p0.fraction = (index + 1).toDouble() / total
            }
            if (!p0.isCanceled) {
                callback(true, "Moved $total file(s)")
            }
        } catch (e: Exception) {
            callback(false, e.message ?: "Unknown error")
        }

    }

    private fun moveFile(targetFile: SftpFile, destinationFile: SftpFile,cancelled: () -> Boolean, progress: (Double) -> Unit) {
        val fileSystem  = remoteDir.fileSystem
        if(!targetFile.server.systemInfo.osName.isWindows()){
            fileSystem.moveFile(targetFile,destinationFile)
            progress(1.0)
        } else {

        }
    }

    companion object {
        private fun buildTitle(filesToMove: List<SftpFile>): String =
            when {
                filesToMove.size== 1 -> "Moving ${filesToMove.first().name}..."
                filesToMove.isNotEmpty() -> "Moving ${filesToMove.size} files..."
                else -> "Moving..."
            }
    }

    // ---------- Builder ----------
    class Builder(private val project: Project) {
        private var filesToMove: List<SftpFile> = emptyList()
        private lateinit var remoteDir: SftpFile
        private var callback: (Boolean, String) -> Unit = { _, _ -> }
        fun filesToCopy(list: List<SftpFile>) = apply { this.filesToMove = list }
        fun remoteDir(dir: SftpFile) = apply {
            this.remoteDir= dir
        }
        fun callback(cb: (Boolean, String) -> Unit) = apply {
            this.callback =
                cb
        }
        fun build(): SftpMoveTask {
            require(filesToMove.isNotEmpty()) { "At least one upload must be provided" }
            return SftpMoveTask(project, filesToMove,remoteDir, callback)
        }
    }

}


fun copyFiles(project: Project,files: List<SftpFile>, targetDir: SftpFile, callback: (Boolean) -> Unit = {}) {
    if (files.isEmpty()) return
    val task = SftpCopyTask.Builder(project)
        .filesToCopy(files)
        .remoteDir(targetDir)
        .callback { success, msg ->
            if (success) {
                println("✅ Copied successfully: $msg")
                notifyLater(
                    project,
                    "Copied",
                    "Copy completed: ${files.size} file(s) to ${targetDir.pathLocation}",
                    NotificationType.INFORMATION
                )
                callback(true)
            } else {
                val title = if (msg == "Cancelled") "Copy Cancelled" else "Copy Failed"
                val message = if (msg == "Cancelled") {
                    "Copy of ${files.size} file(s) to ${targetDir.pathLocation} was cancelled."
                } else {
                    "Copy failed: $msg"
                }
                val type = if (msg == "Cancelled") NotificationType.WARNING else NotificationType.ERROR
                notifyLater(project, title, message, type)
                callback(false)
            }
        }
        .build()
    task.queue()

}

fun moveFiles(project: Project,files: List<SftpFile>, targetDir: SftpFile, callback: (Boolean) -> Unit = {}) {
    if (files.isEmpty()) return
    val task = SftpMoveTask.Builder(project)
        .filesToCopy(files)
        .remoteDir(targetDir)
        .callback { success, msg ->
            if (success) {
                println("✅ Moved successfully: $msg")
                notifyLater(
                    project,
                    "Moved",
                    "Move completed: ${files.size} file(s) to ${targetDir.pathLocation}",
                    NotificationType.INFORMATION
                )
                callback(true)
            } else {
                val title = if (msg == "Cancelled") "Move Cancelled" else "Move Failed"
                val message = if (msg == "Cancelled") {
                    "Move of ${files.size} file(s) to ${targetDir.pathLocation} was cancelled."
                } else {
                    "Move failed: $msg"
                }
                val type = if (msg == "Cancelled") NotificationType.WARNING else NotificationType.ERROR
                notifyLater(project, title, message, type)
                callback(false)
            }
        }
        .build()
    task.queue()

}


fun uploadSftpFiles(project: Project,remoteDir: SftpFile, needUpload: List<File> = emptyList(), callback: (Boolean) -> Unit = {}) {
    var files = needUpload.filter { it.exists() && it.isFile }
    if (files.isEmpty()) {
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
        descriptor.title = "Select Files or Folders to Upload"
        descriptor.description = "Choose files or folders to upload to the remote directory."
        val localFiles = FileChooser.chooseFiles(descriptor, project, null).toList()
        if (localFiles.isEmpty()) return
        else files = localFiles.map {
            File(it.path)
        }
    }
    val task = SftpUploadTask.Builder(project)
        .uploads(files)
        .remoteDir(remoteDir)
        .callback { success, msg ->
            if (success) {
                println("✅ Uploaded successfully: $msg")
                notifyLater(
                    project,
                    "Uploaded",
                    "Upload completed: ${files.size} file(s) to ${remoteDir.pathLocation}",
                    NotificationType.INFORMATION
                )
                callback(true)
            } else {
                val title = if (msg == "Cancelled") "Upload Cancelled" else "Upload Failed"
                val message = if (msg == "Cancelled") {
                    "Upload of ${files.size} file(s) to ${remoteDir.pathLocation} was cancelled."
                } else {
                    "Upload failed: $msg"
                }
                val type = if (msg == "Cancelled") NotificationType.WARNING else NotificationType.ERROR
                notifyLater(project, title, message, type)
                callback(false)
            }
        }
        .build()

    task.queue()
}

fun downloadSftpFiles(project: Project,files: List<SftpFile>) {
    if (files.isEmpty()) return
    val isSingle = files.size == 1
    val first = files.first()
    val fileDialog = if (isSingle) {
        val isDirectory = first.isDirectory
        val descriptor = if (isDirectory) {
            FileSaverDescriptor("Download Folder as Zip", "Select location to save the zip file", "zip")
        } else {
            FileSaverDescriptor("Download File", "Select location to save the file")
        }
        FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    } else null

    val targetDir: File? = if (!isSingle) {
        val dirDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        dirDescriptor.title = "Select Target Directory for Downloaded Files"
        FileChooser.chooseFiles(dirDescriptor, project, null).firstOrNull()?.let { File(it.path) }
    } else null


    val downloads:List<Pair<SftpFile, File>> = if (isSingle) {
        val saved = fileDialog?.save(null as VirtualFile?, if (first.isDirectory) "${first.name}.zip" else first.name)
        val pairs = if (saved != null) listOf(first to saved.file) else emptyList()
        pairs
    } else if(targetDir != null) {
        files.map { it to targetDir.let { dir -> File(dir, if (it.isDirectory) "${it.name}.zip" else it.name) } }
    } else {emptyList()}


    if (downloads.isEmpty()) return

    val task = SftpDownloadTask.Builder(project)
        .downloads(downloads)
        .callback { success, msg ->
            if (success) {
                println("✅ Downloaded successfully: $msg")
                if(isSingle){
                    notifyLater(
                        project,
                        "Downloaded",
                        "Download completed: ${downloads.get(0).second.path}",
                        NotificationType.INFORMATION
                    )
                }else{
                    notifyLater(
                        project,
                        "Downloaded",
                        if(targetDir !=null )"Download completed: ${targetDir.path}" else "Download completed",
                        NotificationType.INFORMATION
                    )
                }

            } else {
                val title = if (msg == "Cancelled") "Download Cancelled" else "Download Failed"
                val message = if (msg == "Cancelled") {
                    "Download was cancelled."
                } else {
                    "Download failed: $msg"
                }
                val type = if (msg == "Cancelled") NotificationType.WARNING else NotificationType.ERROR
                notifyLater(project, title, message, type)
                println("❌ Failed: $msg")
            }
        }
        .build()

    task.queue()
}

private fun notifyLater(project: Project, title: String, message: String, type: NotificationType) {
    ApplicationManager.getApplication().invokeLater {
        project.showNotification(title, message, type)
    }
}
