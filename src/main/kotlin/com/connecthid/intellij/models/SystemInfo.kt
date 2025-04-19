package com.connecthid.intellij.models

import com.intellij.util.xmlb.Converter

data class SystemInfo(
    val osName: String = "",
    val osVersion: String = "",
    val cpuType: String = "",
    val architecture: String = "",
    val totalRam: String = "",
    val usedRam: String = "",
    val totalStorage: String = "",
    val usedStorage: String = "",
    val hostName: String = ""
)

class SystemInfoConverter : Converter<SystemInfo>() {
    override fun toString(value: SystemInfo): String? {
        return "${value.osName}|${value.osVersion}|${value.cpuType}|${value.totalRam}|" +
                "${value.usedRam}|${value.totalStorage}|${value.usedStorage}|${value.hostName}"
    }

    override fun fromString(value: String): SystemInfo {
        if (value.isBlank()) return SystemInfo()
        val parts = value.split("|")
        return if (parts.size == 8) {
            SystemInfo(
                osName = parts[0],
                osVersion = parts[1],
                cpuType = parts[2],
                totalRam = parts[3],
                usedRam = parts[4],
                totalStorage = parts[5],
                usedStorage = parts[6],
                hostName = parts[7]
            )
        } else {
            SystemInfo()
        }
    }
}