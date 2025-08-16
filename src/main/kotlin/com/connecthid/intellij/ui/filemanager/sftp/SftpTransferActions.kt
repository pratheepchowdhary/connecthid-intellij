package com.connecthid.intellij.ui.filemanager.sftp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object SftpTransferActions {
    private val coroutineScope = CoroutineScope(SupervisorJob())

    fun downloadFromRemote(project: Project, remoteFileOrDir: VirtualFile) {
        val isDirectory = remoteFileOrDir.isDirectory
        val fileName = remoteFileOrDir.name
        val descriptor = if (isDirectory) {
            FileSaverDescriptor("Download Folder as Zip", "Select location to save the zip file", "zip")
        } else {
            FileSaverDescriptor("Download File", "Select location to save the file")
        }
        val fileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val fileWrapper = fileDialog.save(null as VirtualFile?, if (isDirectory) "$fileName.zip" else fileName) ?: return
        val targetFile = fileWrapper.file
        val taskCancellation = TaskCancellation.cancellable()
            .withButtonText("Cancel Download")
            .withTooltipText("Cancel the current download task")
        coroutineScope.launch {
            withBackgroundProgress(project, "Downloading from SFTP", taskCancellation) {
                try {
                    val indicator = com.intellij.openapi.progress.ProgressManager.getInstance().progressIndicator
                    indicator?.text = if (isDirectory) "Zipping and downloading folder..." else "Downloading file..."
                    val sftpFile = remoteFileOrDir
                    val fileSystem = sftpFile.fileSystem
                    val connection = (fileSystem as? com.connecthid.intellij.connection.vfs.SftpFileSystem)?.getConnection()
                    if (connection == null) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(project, "No SFTP connection.", "Download Failed")
                        }
                        return@withBackgroundProgress
                    }
                    val channel = connection.getChannelFromPool()
                    try {
                        if (isDirectory) {
                            val zipName = "${fileName}_${System.currentTimeMillis()}.zip"
                            val remoteZipPath = "/tmp/$zipName"
                            val zipCommand = "cd '${remoteFileOrDir.path}'; cd ..; zip -r '$remoteZipPath' '${fileName}'"
                            val execChannel = connection.getExecChannelFromPool()
                            try {
                                execChannel?.setCommand(zipCommand)
                                execChannel?.connect()
                                while (execChannel != null && !execChannel.isClosed) {
                                    Thread.sleep(200)
                                }
                                execChannel?.disconnect()
                            } finally {
                                connection.releaseExecChannelToPool(execChannel)
                            }
                            val sftpChannel = connection.getChannelFromPool()
                            try {
                                val inputStream = sftpChannel?.get(remoteZipPath)
                                FileOutputStream(targetFile).use { out: FileOutputStream ->
                                    inputStream?.copyTo(out)
                                }
                            } finally {
                                connection.releaseChannelToPool(sftpChannel)
                            }
                            val cleanupChannel = connection.getChannelFromPool()
                            try {
                                cleanupChannel?.rm(remoteZipPath)
                            } finally {
                                connection.releaseChannelToPool(cleanupChannel)
                            }
                        } else {
                            val sftpChannel = channel
                            val inputStream = sftpChannel?.get(remoteFileOrDir.path)
                            FileOutputStream(targetFile).use { out: FileOutputStream ->
                                inputStream?.copyTo(out)
                            }
                        }
                        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)?.let {
                            VfsUtil.markDirtyAndRefresh(true, false, false, it)
                        }
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage(project, "Download completed!", "SFTP Download")
                        }
                    } catch (ex: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(project, "Download failed: ${ex.message}", "SFTP Download")
                        }
                    } finally {
                        connection.releaseChannelToPool(channel)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Download failed: ${ex.message}", "SFTP Download")
                    }
                }
            }
        }
    }

    fun uploadToRemote(project: Project, remoteDir: VirtualFile) {
        val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
        descriptor.title = "Select File or Folder to Upload"
        descriptor.description = "Choose a file or folder to upload to the remote directory."
        // Use builder methods instead of direct assignment
        val files = FileChooser.chooseFiles(descriptor.withFileFilter { true }.withShowHiddenFiles(true).withTreeRootVisible(true), project, null)
        if (files.isEmpty()) return
        val localFile = files.first()
        val isDirectory = localFile.isDirectory
        val taskCancellation = TaskCancellation.cancellable()
            .withButtonText("Cancel Upload")
            .withTooltipText("Cancel the current upload task")
        coroutineScope.launch {
            withBackgroundProgress(project, "Uploading to SFTP", taskCancellation) {
                try {
                    val indicator = com.intellij.openapi.progress.ProgressManager.getInstance().progressIndicator
                    indicator?.text = if (isDirectory) "Zipping and uploading folder..." else "Uploading file..."
                    val fileSystem = remoteDir.fileSystem
                    val connection = (fileSystem as? com.connecthid.intellij.connection.vfs.SftpFileSystem)?.getConnection()
                    if (connection == null) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(project, "No SFTP connection.", "Upload Failed")
                        }
                        return@withBackgroundProgress
                    }
                    val channel = connection.getChannelFromPool()
                    try {
                        if (isDirectory) {
                            // Zip the folder locally
                            val zipFile = File.createTempFile(localFile.name, ".zip")
                            zipFile.deleteOnExit()
                            zipFolder(localFile, zipFile)
                            // Upload the zip
                            val sftpChannel = channel
                            val remoteZipPath = remoteDir.path + "/" + zipFile.name
                            FileInputStream(zipFile).use { input ->
                                sftpChannel?.put(input, remoteZipPath)
                            }
                            // Extract the zip on remote
                            val unzipCommand = "cd '${remoteDir.path}'; unzip -o '${zipFile.name}' && rm '${zipFile.name}'"
                            val execChannel = connection.getExecChannelFromPool()
                            try {
                                execChannel?.setCommand(unzipCommand)
                                execChannel?.connect()
                                while (execChannel != null && !execChannel.isClosed) {
                                    Thread.sleep(200)
                                }
                                execChannel?.disconnect()
                            } finally {
                                connection.releaseExecChannelToPool(execChannel)
                            }
                        } else {
                            // Upload file
                            val sftpChannel = channel
                            FileInputStream(localFile.path).use { input ->
                                sftpChannel?.put(input, remoteDir.path + "/" + localFile.name)
                            }
                        }
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage(project, "Upload completed!", "SFTP Upload")
                        }
                    } catch (ex: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(project, "Upload failed: ${ex.message}", "SFTP Upload")
                        }
                    } finally {
                        connection.releaseChannelToPool(channel)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Upload failed: ${ex.message}", "SFTP Upload")
                    }
                }
            }
        }
    }

    private fun zipFolder(sourceFolder: VirtualFile, zipFile: File) {
        java.util.zip.ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            fun addFileToZip(file: VirtualFile, basePath: String) {
                if (file.isDirectory) {
                    val children = file.children
                    for (child in children) {
                        addFileToZip(child, basePath + file.name + "/")
                    }
                } else {
                    val entryName = basePath + file.name
                    zipOut.putNextEntry(java.util.zip.ZipEntry(entryName))
                    file.inputStream.use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }
            addFileToZip(sourceFolder, "")
        }
    }
}
