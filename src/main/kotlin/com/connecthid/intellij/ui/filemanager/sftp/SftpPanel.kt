package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.vfs.SftpFile
import com.connecthid.intellij.vfs.SftpFileSystem
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
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
    private var loadingPanel: JBLoadingPanel? = null
    val rootPath by  lazy {
        if(serverItem.username.equals("root")) "/root" else "/home/${serverItem.username}"
    }

    init {
        println("Initializing SftpPanel for server: ${serverItem.host}")
        // Create the root node
        rootNode = SftpTreeNode(SftpFile(rootPath,fileSystem))
        
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

    protected fun addTableLoadingLayer() {
//        loadingPanel = JBLoadingPanel(BorderLayout(), this, 200)
//        loadingPanel!!.setLoadingText("Loading...")
//        loadingPanel!!.setName("Remote SFTP Directories")
//        loadingPanel!!.add(this)
    }

    fun refresh() {
        println("Refreshing file list...")
        try {
            // Ensure file system is initialized
            fileSystem
            
            // Get root directory
            println("Finding root directory: $rootPath")
            val rootDir = fileSystem.findFileByPath(rootPath)
            println("Root directory found: ${rootDir != null}")
            
            if (rootDir != null) {
                // Clear existing nodes
                rootNode.removeAllChildren()
                
                // Get children and add them to the tree
                val children = rootDir.children
                println("Found ${children.size} children in root directory")
                
                children.forEach { child ->
                    println("Adding child to tree: ${child.name}")
                    val node = SftpTreeNode(child)
                    rootNode.add(node)
                }
                
                // Notify the model that the structure has changed
                model.reload()
                
                // Expand the root node
                tree.expandPath(TreePath(rootNode))
                
                // Update the tree UI
                tree.updateUI()
            }
        } catch (e: Exception) {
            println("Error refreshing file list: ${e.message}")
            e.printStackTrace()
        }
    }

    private inner class SftpTreeNode(val file: VirtualFile) : DefaultMutableTreeNode() {
        init {
            userObject = file
        }
        
        override fun toString(): String = file.name
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
            if (value is SftpTreeNode) {
                val file = value.file
                
                // Set icon based on file type
                icon = if (file.isDirectory) {
                    AllIcons.Nodes.Folder
                } else {
                    FileTypeManager.getInstance().getFileTypeByFileName(file.name).icon
                }
                
                // Set text with attributes
                append(file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }
} 