package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.vfs.SftpFileSystem
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class SftpPanel(val project: Project, val serverItem: Server) : JPanel(BorderLayout()) {
    private val tree: Tree
    private val model: DefaultTreeModel
    private val rootNode: SftpTreeNode
    private val fileSystem by lazy {
        SftpFileSystem(project,serverItem)
    }

    init {
        // Create the root node
        rootNode = SftpTreeNode(null, "SFTP")
        
        // Create the tree model
        model = DefaultTreeModel(rootNode)
        
        // Create the tree
        tree = Tree(model)
        tree.cellRenderer = SftpTreeCellRenderer()
        
        // Add the tree to a scroll pane
        add(JScrollPane(tree), BorderLayout.CENTER)
        
        // Expand the root node
        tree.expandPath(TreePath(rootNode))
    }

    fun refresh() {
        // Clear existing children
        rootNode.removeAllChildren()
        
        // Add connected servers as children
        fileSystem.fileCache.keys
            .map { it.split("/").first() }
            .distinct()
            .forEach { host ->
                val node = SftpTreeNode(rootNode, host)
                rootNode.add(node)
            }
        
        // Notify the model that the structure has changed
        model.reload()
        
        // Expand the root node
        tree.expandPath(TreePath(rootNode))
    }

    private inner class SftpTreeNode(
        parent: SftpTreeNode?,
        val name: String
    ) : DefaultMutableTreeNode(name) {
        init {
            if (parent != null) {
                parent.add(this)
            }
        }
    }

    private inner class SftpTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as SftpTreeNode
            val fileTypeManager = FileTypeManager.getInstance()
            
            if (node.parent == null) {
                // Root node
                icon = AllIcons.Nodes.Folder
                append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            } else if (node.isLeaf) {
                // File node
                val file = fileSystem.findFileByPath(node.name)
                if (file != null) {
                    icon = fileTypeManager.getFileTypeByFile(file).icon
                    append(file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            } else {
                // Directory node
                icon = AllIcons.Nodes.Folder
                append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }
} 