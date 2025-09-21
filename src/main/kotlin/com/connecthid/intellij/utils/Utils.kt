package com.connecthid.intellij.utils

import com.connecthid.intellij.models.SftpUrl

object Utils {
    fun parseSftpUrl(url: String): SftpUrl {
        val uri = java.net.URI(url)

        // Extract username (before @ in userInfo)
        val userInfo = uri.userInfo      // "username" in your format
        val username = userInfo?.takeIf { it.isNotEmpty() }

        // Host and port
        val host = uri.host ?: throw IllegalArgumentException("Host missing in URL")
        val port = if (uri.port == -1) 22 else uri.port  // default SFTP port 22

        // Path
        val path = uri.path ?: "/"

        return SftpUrl(username, host, port, path)
    }
}