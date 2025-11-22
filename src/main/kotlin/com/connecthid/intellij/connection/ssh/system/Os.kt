package com.connecthid.intellij.connection.ssh.system

enum class Os(val os: String) {
    Linux("linux"),
    MacOS("darwin"),
    Windows("windows"),

    Unknown("unknown")
}