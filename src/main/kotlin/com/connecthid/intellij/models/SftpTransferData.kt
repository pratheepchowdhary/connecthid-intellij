package com.connecthid.intellij.models

import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.ui.filemanager.sftp.SftpTreeNode

data class SftpTransferData(
    val files: List<SftpFile>,
    val nodes: List<SftpTreeNode>,
    val cut: Boolean
)