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
        println("Initializing SftpFileSystem for server: ${serverItem.host}")
        SftpFileSystem(project, serverItem)
    }

    init {
        println("Initializing SftpPanel for server: ${serverItem.host}")
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
        
        // Initial refresh
        refresh()
    }

    fun refresh() {
        println("Refreshing file list...")
        try {
            // Get root directory
            val rootPath = "/"
            println("Finding root directory: $rootPath")
            val rootDir = fileSystem.findFileByPath(rootPath)
            println("Root directory found: ${rootDir != null}")
            
            if (rootDir != null) {
                val children = rootDir.children
                println("Found ${children.size} children in root directory")
                
                // Clear existing nodes
                rootNode.removeAllChildren()
                
                // Add each child as a node
                children.forEach { child ->
                    println("Adding child: ${child.name}")
                    val node = SftpTreeNode(rootNode, child.name)
                    rootNode.add(node)
                }
                
                // Expand the root node
                tree.expandPath(TreePath(rootNode))
            }
            
            // Update the tree
            tree.updateUI()
        } catch (e: Exception) {
            println("Error refreshing file list: ${e.message}")
            e.printStackTrace()
        }
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
            } else {
                // File or directory node
                val file = fileSystem.findFileByPath("${serverItem.host}/${node.name}")
                if (file != null) {
                    if (file.isDirectory) {
                        icon = AllIcons.Nodes.Folder
                    } else {
                        icon = fileTypeManager.getFileTypeByFile(file).icon
                    }
                    append(file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        }
    }
} 