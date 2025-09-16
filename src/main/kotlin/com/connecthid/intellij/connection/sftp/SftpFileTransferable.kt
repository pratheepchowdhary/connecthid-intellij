package com.connecthid.intellij.connection.sftp

import com.connecthid.intellij.models.SftpTransferData
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File


class SftpFileTransferable(
    private val sftpFiles: List<SftpFile>, val cut: Boolean = false
) : Transferable {
    companion object {
         val SFTP_FLAVOR by lazy {
            DataFlavor(SftpTransferData::class.java, "application/x-sftpfilelist")
        }
    }
    private val stringRepresentation: String =
        sftpFiles.joinToString(separator = "\n") { it.name }

    override fun getTransferDataFlavors(): Array<DataFlavor> =
        // add DataFlavor.javaFileListFlavor  to support copy to local file system
        arrayOf(DataFlavor.stringFlavor,SFTP_FLAVOR)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        (flavor == DataFlavor.javaFileListFlavor || flavor == SFTP_FLAVOR)

    override fun getTransferData(flavor: DataFlavor): Any {
        println("Getting transfer data for flavor: $flavor")
        if (flavor == DataFlavor.javaFileListFlavor) {
                // Download files from SFTP to a temp dir
                val tempDir = File.createTempFile("sftpclip", "").apply { delete(); mkdir() }
                val localFiles = sftpFiles.map { sftpFile ->
                    val localFile = File(tempDir, sftpFile.name)
                    localFile.createNewFile()
                    localFile
                }
            return localFiles
        } else if (flavor == DataFlavor.stringFlavor) {
            return stringRepresentation
        }
        else if (flavor == SFTP_FLAVOR) {
           return SftpTransferData(sftpFiles,cut)
        }
        throw UnsupportedFlavorException(flavor)
    }
}