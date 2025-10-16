package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.ui.MyIcons
import javax.swing.Icon

enum class RunConfigurationTaskType(val type: Int, val icon: Icon, val configName: String) {
    Script(0, MyIcons.Script,"Script"),
    SftpFileTransfer(1, MyIcons.FileTransfer,"SftpUploadAndDownload");
    //get enum by type
    companion object {
        fun fromType(type: Int): RunConfigurationTaskType {
            return entries.find { it.type == type }!!
        }
    }
}