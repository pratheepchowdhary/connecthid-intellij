package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.vfs.SftpFile
import com.connecthid.intellij.vfs.SftpFileSystem
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath


class SftpPanel(val project: Project, val serverItem: Server) : JPanel(BorderLayout()),TreeSelectionListener,
    TreeExpansionListener {
    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode: SftpTreeNode
    private val fileSystem by lazy {
        println("Initializing SftpFileSystem for server: ${serverItem.host}")
        SftpFileSystem(project, serverItem)
    }
    private var loadingPanel: JBLoadingPanel? = null
    val rootPath by  lazy {
        if(serverItem.username.equals("root")) "/root" else "/home/${serverItem.username}"
    }
    val rootDir by lazy {
        SftpFile(rootPath,fileSystem)
    }

    init {
        println("Initializing SftpPanel for server: ${serverItem.host}")
        // Create the root node
        rootNode = SftpTreeNode(rootDir)
        rootNode.add(DefaultMutableTreeNode("Loading..."))
//        val fileEditorManager = FileEditorManager.getInstance(project)
//        fileEditorManager.openFile(SftpFile(rootPath,fileSystem), true)
        // Create the tree model
        treeModel = DefaultTreeModel(rootNode)
        // Create the tree
        tree = Tree(treeModel)
        tree.cellRenderer = SftpTreeCellRenderer()
        tree.addTreeSelectionListener(this)
        tree.addTreeExpansionListener(this)
        // Add the tree to a scroll pane
        add(JScrollPane(tree), BorderLayout.CENTER)
        // Expand the root node
        tree.expandPath(TreePath(rootNode))
        loadChildren(rootNode,rootDir)
    }

    private fun loadChildren(selectedNode:DefaultMutableTreeNode, file:VirtualFile){
        // Expand node dynamically if not already expanded
        if (selectedNode.childCount == 1 && (selectedNode.getChildAt(0) as? DefaultMutableTreeNode)?.userObject == "Loading...") {
            selectedNode.removeAllChildren()
            addChildren(selectedNode, file)
            treeModel.reload(selectedNode)
        }
    }


    private fun addChildren(parentNode: DefaultMutableTreeNode, dir: VirtualFile) {
        try {
            dir.children.forEach { child ->
                val childNode = SftpTreeNode(child)
                parentNode.add(childNode)

                // Preload directories with dummy node for lazy loading
                if (child.isDirectory) {
                    childNode.add(DefaultMutableTreeNode("Loading..."))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun valueChanged(e: TreeSelectionEvent) {
        val selectedNode = e.path?.lastPathComponent as? DefaultMutableTreeNode ?: return
        val file = selectedNode.userObject as? VirtualFile ?: return

        if (!file.isDirectory) {
            try {
                val content = String(file.contentsToByteArray())
                JOptionPane.showMessageDialog(this, content, "Preview: ${file.name}", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            loadChildren(selectedNode,file)
        }
    }

    override fun treeExpanded(event: TreeExpansionEvent) {
        val node = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val file = node.userObject as? VirtualFile ?: return

        if (file.isDirectory) {
            loadChildren(node,file)
        }
    }

    override fun treeCollapsed(event: TreeExpansionEvent) {

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
                
                // Set icon based on file type and expanded state
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
fun Project.openSFTP(server: Server){
    val manager = ToolWindowManager.getInstance(this)
    if (manager.getToolWindow(server.stmpName) == null) {
        val window  = manager.registerToolWindow(server.stmpName){
            icon=AllIcons.Nodes.WebFolder
            canCloseContent=false
            anchor= ToolWindowAnchor.RIGHT
        }

        val sftpPanel = SftpPanel(this,server)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(sftpPanel, "", false)
        window.contentManager.addContent(content)
        window.show()
    }
}