package com.connecthid.intellij.models

import com.intellij.util.xmlb.Converter

data class SystemInfo(
    val osName: String = "",
    val displayName: String = "",
    val osVersion: String = "",
    val cpuType: String = "",
    val architecture: String = "",
    val totalRam: String = "",
    val usedRam: String = "",
    val totalStorage: String = "",
    val usedStorage: String = "",
    val hostName: String = "",
    val defaultShell: String="",
    val connectHidDir: String="",
    val homePath: String=""
)

class SystemInfoConverter : Converter<SystemInfo>() {
    override fun toString(value: SystemInfo): String {
        return "${value.osName}|${value.displayName}|${value.osVersion}|${value.cpuType}|${value.totalRam}|" +
                "${value.usedRam}|${value.totalStorage}|${value.usedStorage}|${value.hostName}|${value.defaultShell}|${value.connectHidDir}|${value.homePath}"
    }

    override fun fromString(value: String): SystemInfo {
        if (value.isBlank()) return SystemInfo()
        val parts = value.split("|")
        return if (parts.size == 12) {
            SystemInfo(
                osName = parts[0],
                displayName = parts[1],
                osVersion = parts[2],
                cpuType = parts[3],
                totalRam = parts[4],
                usedRam = parts[5],
                totalStorage = parts[6],
                usedStorage = parts[7],
                hostName = parts[8],
                defaultShell = parts[9],
                connectHidDir = parts[10],
                homePath = parts[11]
            )
        } else {
            SystemInfo()
        }
    }
}