package com.connecthid.intellij.connection.ssh.system

data class SystemInformation(
    var osName: String? = null,
    var displayName: String? = null,
    var osVersion: String? = null,
    var cpuModel: String? = null,
    var cpuCores: Int = 0,
    var architecture: String? = null,
    var totalRam: String? = null,
    var usedRam: String? = null,
    var totalStorage: String? = null,
    var usedStorage: String? = null,
    var hostName: String? = null,
    var kernelVersion: String? = null,
    var uptime: String? = null,
    var defaultShell: String? = null,
    var connectHidDir: String? = null,
    var homePath: String? = null
)