package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.ui.MyIcons
import com.intellij.icons.AllIcons
import javax.swing.Icon

enum class RunConfigurationTask(val type: Int, val icon: Icon,val configName: String) {
    Script(0,AllIcons.Actions.RunAnything,"Script"),
    SftpFileTransfer(1, MyIcons.FileTransfer,"SftpUploadAndDownload");
    //get enum by type
    companion object {
        fun fromType(type: Int): RunConfigurationTask {
            return entries.find { it.type == type }!!
        }
    }
}