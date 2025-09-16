package com.connecthid.intellij.models

import com.connecthid.intellij.connection.sftp.SftpFile

data class SftpTransferData(
    val files: List<SftpFile>,
    val cut: Boolean
)