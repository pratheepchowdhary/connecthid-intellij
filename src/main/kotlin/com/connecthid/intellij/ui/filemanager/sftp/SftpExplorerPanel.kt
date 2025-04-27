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


class SftpExplorerPanel(val project: Project, val serverItem: Server) : JPanel(BorderLayout()), TreeSelectionListener, TreeExpansionListener {
    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode: SftpTreeNode
    private val fileSystem by lazy {
        println("Initializing SftpFileSystem for server: ${serverItem.host}")
        SftpFileSystem(project, serverItem)
    }
    val rootPath by lazy {
        if (serverItem.username == "root") "/root" else "/home/${serverItem.username}"
    }
    val rootDir by lazy {
        SftpFile(rootPath, fileSystem)
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val fileScrollView: com.intellij.ui.components.JBScrollPane
    private val loadingIcon by lazy {
        SpinningProgressIcon().apply {
            setIconColor(JBColor.BLUE)
        }
    }
    private val loadingLabel by lazy {
        JLabel("Loading files...", loadingIcon, SwingConstants.LEADING).apply {
            foreground = JBColor.BLUE
            isVisible = false
        }
    }

    private val loadingStates = mutableSetOf<String>() // To track loading directories

    init {
        println("Initializing SftpPanel for server: ${serverItem.host}")
        rootNode = SftpTreeNode(rootDir)
        rootNode.add(DefaultMutableTreeNode("Loading..."))
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)
        tree.cellRenderer = SftpTreeCellRenderer()
        tree.addTreeSelectionListener(this)
        tree.addTreeExpansionListener(this)
        fileScrollView = com.intellij.ui.components.JBScrollPane(tree)
        tree.expandPath(TreePath(rootNode))
        add(fileScrollView, BorderLayout.CENTER)
        add(loadingLabel, BorderLayout.CENTER)
    }

    private suspend fun getChildren(file: VirtualFile): Array<VirtualFile> = withContext(Dispatchers.IO) {
        return@withContext file.children
    }

    private suspend fun loadChildren(selectedNode: DefaultMutableTreeNode, file: VirtualFile) = withContext(Dispatchers.Main) {
        try {
            if (loadingStates.contains(file.path)) return@withContext // Skip if already loading
            loadingStates.add(file.path)
            loadingLabel.isVisible = true
            if (selectedNode.childCount == 1 && (selectedNode.getChildAt(0) as? DefaultMutableTreeNode)?.userObject == "Loading...") {
                selectedNode.removeAllChildren()
                addChildren(selectedNode, file)
                treeModel.reload(selectedNode)
            }
        } finally {
            loadingStates.remove(file.path)
            loadingLabel.isVisible = false
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
                loadChildren(selectedNode, file)
            }
        }
    }

    override fun treeExpanded(event: TreeExpansionEvent) {
        val node = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val file = node.userObject as? VirtualFile ?: return

        if (file.isDirectory) {
            coroutineScope.launch {
                loadChildren(node, file)
            }
        }
    }

    override fun treeCollapsed(event: TreeExpansionEvent) {}

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
            }
        }
    }
}

fun Project.openSFTP(server: Server) {
    val manager = ToolWindowManager.getInstance(this)
    if (manager.getToolWindow(server.stmpName) == null) {
        val window = manager.registerToolWindow(server.stmpName) {
            icon = AllIcons.Nodes.WebFolder
            canCloseContent = false
            anchor = ToolWindowAnchor.RIGHT
        }

        val sftpPanel = SftpExplorerPanel(this, server)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(sftpPanel, "", false)
        window.contentManager.addContent(content)
        window.show()
    }
}
