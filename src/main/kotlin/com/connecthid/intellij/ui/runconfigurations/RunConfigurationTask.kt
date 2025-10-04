package com.connecthid.intellij.ui.runconfigurations

import com.intellij.icons.AllIcons
import javax.swing.Icon

enum class RunConfigurationTask(val type: Int, val icon: Icon,val configName: String) {
    LocalScript(0,AllIcons.Actions.RunAnything,"LocalScript"),
    RemoteScript(1,AllIcons.Actions.RunAnything,"RemoteScript"),
    Upload(2,AllIcons.Actions.Upload,"Upload"),
    Download(3,AllIcons.Actions.Download,"Download");

    //get enum by type
    companion object {
        fun fromType(type: Int): RunConfigurationTask {
            return entries.find { it.type == type }!!
        }
    }
}