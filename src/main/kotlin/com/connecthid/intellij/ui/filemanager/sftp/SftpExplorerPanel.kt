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
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.*
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.*
import java.awt.*


class SftpExplorerPanel(val project: Project, val serverItem: Server) : JPanel(BorderLayout()),TreeSelectionListener,
    TreeExpansionListener {
    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode: SftpTreeNode
    private val fileSystem by lazy {
        println("Initializing SftpFileSystem for server: ${serverItem.host}")
        SftpFileSystem(project, serverItem)
    }
    val rootPath by  lazy {
        if(serverItem.username.equals("root")) "/root" else "/home/${serverItem.username}"
    }
    val rootDir by lazy {
        SftpFile(rootPath,fileSystem)
    }
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val fileScrollView:com.intellij.ui.components.JBScrollPane
    private val progressBar by lazy {
        JProgressBar().apply {
           isIndeterminate = true
           foreground = JBColor.BLUE
        }
    }



    init {

        println("Initializing SftpPanel for server: ${serverItem.host}")
        add(progressBar, BorderLayout.CENTER)
        // Create the root node
        rootNode = SftpTreeNode(rootDir)
        rootNode.add(DefaultMutableTreeNode("Loading..."))
        // Create the tree model
        treeModel = DefaultTreeModel(rootNode)
        // Create the tree
        tree = Tree(treeModel)
        tree.cellRenderer = SftpTreeCellRenderer()
        tree.addTreeSelectionListener(this)
        tree.addTreeExpansionListener(this)
        // Add the tree to a scroll pane
        fileScrollView = com.intellij.ui.components.JBScrollPane(tree)
        //add(fileScrollView, BorderLayout.CENTER)
        fileScrollView.isVisible=false
        // Expand the root node
        tree.expandPath(TreePath(rootNode))
//        coroutineScope.launch {
//            loadChildren(rootNode,rootDir)
//        }
    }

    private suspend fun getChildren(file:VirtualFile):Array<VirtualFile> = withContext(Dispatchers.IO){
        return@withContext file.children
    }



    private suspend fun loadChildren(selectedNode:DefaultMutableTreeNode, file:VirtualFile) = withContext(Dispatchers.Main){
        // Expand node dynamically if not already expanded
        if (selectedNode.childCount == 1 && (selectedNode.getChildAt(0) as? DefaultMutableTreeNode)?.userObject == "Loading...") {
            selectedNode.removeAllChildren()
            addChildren(selectedNode, file)
            treeModel.reload(selectedNode)
        }
    }


    private suspend fun addChildren(parentNode: DefaultMutableTreeNode, dir: VirtualFile) = withContext(Dispatchers.Main) {
        try {
            val children = getChildren(dir)
            children.forEach { child ->
                val childNode = SftpTreeNode(child)
                parentNode.add(childNode)
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
            coroutineScope.launch {
                loadChildren(selectedNode,file)
            }
        }
    }

    override fun treeExpanded(event: TreeExpansionEvent) {
        val node = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val file = node.userObject as? VirtualFile ?: return

        if (file.isDirectory) {
            coroutineScope.launch {
               loadChildren(node,file)
            }
        }
    }

    override fun treeCollapsed(event: TreeExpansionEvent) {

    }

    override fun removeNotify() {
        super.removeNotify()
        coroutineScope.cancel()

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


//    fun getPanel(){
//        val icon = SpinningProgressIcon()
//        val iconBig = bigSpinningProgressIcon()
//        val panel = panel {
//            row {
//                icon(icon)
//                link("Change color") {
//                    ColorChooserService.instance.showPopup(null, icon.getIconColor(), { color, _ -> color?.let {
//                        icon.setIconColor(it)
//                        iconBig.setIconColor(it)
//                    }})
//                }
//                icon(iconBig)
//            }
//        }
//    }
}
fun Project.openSFTP(server: Server){
    val manager = ToolWindowManager.getInstance(this)
    if (manager.getToolWindow(server.stmpName) == null) {
        val window  = manager.registerToolWindow(server.stmpName){
            icon=AllIcons.Nodes.WebFolder
            canCloseContent=false
            anchor= ToolWindowAnchor.RIGHT
        }

        val sftpPanel = SftpExplorerPanel(this,server)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(sftpPanel, "", false)
        window.contentManager.addContent(content)
        window.show()
    }
}