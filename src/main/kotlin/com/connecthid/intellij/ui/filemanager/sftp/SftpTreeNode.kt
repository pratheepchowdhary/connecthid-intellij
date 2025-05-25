package com.connecthid.intellij.ui.filemanager.sftp

import com.intellij.openapi.vfs.VirtualFile
import javax.swing.tree.DefaultMutableTreeNode

class SftpTreeNode(val file: VirtualFile) : DefaultMutableTreeNode() {
        init {
            userObject = file
        }

        override fun toString(): String = file.name
    }