package com.connecthid.intellij.ui.filemanager.sftp

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SpinningProgressIcon
import javax.swing.JTree

class SftpTreeCellRenderer : ColoredTreeCellRenderer() {
        private val loadingIcon by lazy {
            SpinningProgressIcon()
        }
        override fun customizeCellRenderer(
            tree: JTree, value: Any, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            if (value is SftpTreeNode) {
                val file = value.file
                icon = if (file.isDirectory) {
                    AllIcons.Nodes.Folder
                } else {
                    FileTypeManager.getInstance().getFileTypeByFileName(file.name).icon
                }
                append(file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            } else{
                icon = loadingIcon
                append("Loading...", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }