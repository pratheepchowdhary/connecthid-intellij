package com.connecthid.intellij.handler

import com.intellij.openapi.fileTypes.FileType

class ConnectHidUrlFileType : FileType {
    companion object {
        val INSTANCE = ConnectHidUrlFileType()
    }

    override fun getName() = "ConnectHID URL"
    override fun getDescription() = "ConnectHID URL protocol handler"
    override fun getDefaultExtension() = ""
    override fun getIcon() = null
    override fun isBinary() = false
}