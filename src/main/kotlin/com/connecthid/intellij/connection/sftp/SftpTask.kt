package com.connecthid.intellij.connection.sftp

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
import java.io.File
import kotlin.to

class SftpDownloadTask private constructor(
    project: Project,
    private val downloads: List<Pair<SftpFile, File>>,   // unified list of remote→local
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
    private fun downloadFile(remote: SftpFile, local: File,canclelled: () -> Boolean, progress: (Double) -> Unit) {
        // temp file to avoid exposing partial downloads
        val tempFile = File(local.parentFile, "${local.name}.part")
        try {
            remote.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied: Long = 0
                    val totalBytes = remote.length  // size of remote file if available

                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        if (canclelled()) return
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
            // replace final file atomically
            if (local.exists()) local.delete()
            if (!tempFile.renameTo(local)) {
                throw RuntimeException("Failed to rename ${tempFile.name} → ${local.name}")
            }
        }catch (e: Exception) {
            tempFile.delete()
            throw e
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

    private fun uploadFile(local: File, remoteDir: SftpFile,canclelled: () -> Boolean, progress: (Double) -> Unit) {
        if (!local.exists() || !local.isFile) throw IllegalArgumentException("Local file does not exist: ${local.path}")
        val remoteFile = SftpFile("${remoteDir.pathLocation}/${local.name}",remoteDir.fileSystem as SftpFileSystem)
        try {
            local.inputStream().use { input ->
                remoteFile.getOutputStream(null, -1, -1).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied: Long = 0
                    val totalBytes = local.length()

                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        if (canclelled()) return
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
            throw e
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

fun SftpFileSystem.uploadSftpFiles(remoteDir: SftpFile) {
    val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
    descriptor.title = "Select Files or Folders to Upload"
    descriptor.description = "Choose files or folders to upload to the remote directory."
    val localFiles = FileChooser.chooseFiles(descriptor, project, null).toList()
    if (localFiles.isEmpty()) return
    val task = SftpUploadTask.Builder(project)
        .uploads(localFiles.map { File(it.path) })
        .remoteDir(remoteDir)
        .callback { success, msg ->
            if (success) {
                println("✅ Uploaded successfully: $msg")
                notifyLater(
                    project,
                    "Uploaded",
                    "Upload completed: ${localFiles.size} file(s) to ${remoteDir.pathLocation}",
                    NotificationType.INFORMATION
                )
            } else {
                if (msg == "Cancelled"){
                    notifyLater(
                        project,
                        "Upload ",
                        "Upload Canceled: ${localFiles.size} file(s) to ${remoteDir.pathLocation}",
                        NotificationType.ERROR)
                } else {
                    notifyLater(
                        project,
                        "Upload Failed",
                        "Upload failed: $msg",
                        NotificationType.ERROR
                    )
                }
            }

        }
        .build()

    task.queue()
}

fun SftpFileSystem.downloadSftpFiles(files: List<SftpFile>) {
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
                if (msg == "Cancelled"){
                    notifyLater(
                        project,
                        "Download",
                        "Download Canceled",
                        NotificationType.ERROR)
                } else {
                    notifyLater(
                        project,
                        "Download Failed",
                        "Download failed: $msg",
                        NotificationType.ERROR
                    )
                }
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
