package com.connecthid.intellij.models

data class SftpUrl(
    val username: String?,
    val host: String,
    val port: Int,
    val path: String
)