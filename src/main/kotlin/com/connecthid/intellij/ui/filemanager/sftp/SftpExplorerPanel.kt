package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.ui.filemanager.sftp.actions.SftpToolbarActions
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import java.util.function.Supplier

class SftpExplorerPanel(val project: Project, val serverItem: Server) : JPanel(BorderLayout()), TreeExpansionListener, TreeSelectionListener, com.intellij.openapi.Disposable {
    val tree: Tree
    val treeModel: DefaultTreeModel
    val rootNode: SftpTreeNode
    val fileSystem by lazy {
        println("Initializing SftpFileSystem for server: ${serverItem.host}")
        SftpFileSystem(project, serverItem)
    }

    val rootPath by lazy {
        serverItem.rootPath
    }

    val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var fileScrollView: com.intellij.ui.components.JBScrollPane
    val loadingIcon by lazy {
        SpinningProgressIcon()
    }
    val loadingPanel by lazy {
        JPanel(BorderLayout()).apply {
            isOpaque = false
            background = Color(0, 0, 0, 0)
            add(loadingLabel, BorderLayout.CENTER)
            isVisible = false
        }
    }
    val loadingStates = mutableSetOf<String>() // To track loading directories

    private val layeredPane by lazy {
        JLayeredPane().apply {
            layout = null // We'll manually position components
        }
    }

    private val loadingLabel by lazy {
        JLabel("Loading files...", loadingIcon, SwingConstants.LEADING).apply {
            isVisible = true
        }
    }

    var lastSelectedNode: DefaultMutableTreeNode? = null

    init {
        println("Initializing SftpPanel for server: ${serverItem.host}")
        // Create the root node
        rootNode = SftpTreeNode(SftpFile(rootPath,fileSystem))
        rootNode.add(DefaultMutableTreeNode("Loading..."))
        // Create the tree model
        treeModel = DefaultTreeModel(rootNode)

        // Create the tree
        tree = Tree(treeModel)
        tree.cellRenderer = SftpTreeCellRenderer()
        tree.addTreeExpansionListener(this)
        tree.addTreeSelectionListener(this)

        // Use the new toolbar actions class
        val toolbarActions = SftpToolbarActions(this, serverItem = serverItem)
        add(toolbarActions.createToolbar(), BorderLayout.NORTH)

        // Add the tree to a scroll pane
        fileScrollView = com.intellij.ui.components.JBScrollPane(tree)

        // Configure loading label
        loadingLabel.apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            foreground = JBColor.BLUE
            isOpaque = true
        }
        loadingPanel.isVisible=true
        // Add components to layered pane
        layeredPane.add(loadingPanel, JLayeredPane.POPUP_LAYER)
        layeredPane.add(fileScrollView, JLayeredPane.DEFAULT_LAYER)
        
        // Add layered pane to main panel
        add(layeredPane, BorderLayout.CENTER)
        
        // Expand the root node
        tree.expandPath(TreePath(rootNode))

        coroutineScope.launch {
            loadChildren(rootNode, rootNode.file)
        }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if(SwingUtilities.isRightMouseButton(e)){
                    lastSelectedNode?.let {
                        val selectedNodes: List<SftpTreeNode> = tree.getSelectedNodes(SftpTreeNode::class.java, null).toList()

                        showPopupMenu(e.x, e.y, selectedNodes)
                    }
                }
                else if(e.clickCount == 2){
                    lastSelectedNode?.let {
                        val file = it.userObject as? VirtualFile ?: return
                        if(!file.isDirectory){
                            if(!fileSystem.openFileInIDE(file)){
                                //todo show error
                            }
                        }
                    }
                }
            }
        })
    }

    fun showPopupMenu(x: Int, y: Int, selectedNodes: List<SftpTreeNode>) {
        showSftpPopupMenu(
            tree = tree,
            project = project,
            x = x,
            y = y
        )
    }


    override fun doLayout() {
        super.doLayout()
        // Position components in the layered pane
        val size = layeredPane.size
        fileScrollView.bounds = Rectangle(0, 0, size.width, size.height)
        loadingPanel.bounds = Rectangle(0, 0, size.width, size.height)
    }

    private suspend fun getChildren(file: VirtualFile): Array<VirtualFile> = withContext(Dispatchers.IO) {
        return@withContext file.children
    }

    suspend fun loadChildren(selectedNode: DefaultMutableTreeNode, file: VirtualFile) = withContext(Dispatchers.Main) {
        try {
            if (loadingStates.contains(file.path)) return@withContext // Skip if already loading
            loadingStates.add(file.path)
            // Expand node dynamically if not already expanded
            if (selectedNode.childCount == 1 && (selectedNode.getChildAt(0) as? DefaultMutableTreeNode)?.userObject == "Loading...") {
                selectedNode.removeAllChildren()
                addChildren(selectedNode, file)
                treeModel.reload(selectedNode)
            }
        } finally {
            loadingStates.remove(file.path)
            loadingPanel.isVisible = false
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

    override fun valueChanged(event: TreeSelectionEvent?) {
        val selectedPath = event?.path
        val selectedNode = selectedPath?.lastPathComponent as? DefaultMutableTreeNode
        if (selectedNode != null) {
            lastSelectedNode = selectedNode
            println("Tree item selected: ${selectedNode.userObject}")
            // You can add more logic here, e.g., update UI, load file/folder details, etc.
        }
    }

    override fun dispose() {
        // Cleanup resources
        coroutineScope.cancel()
        fileSystem.getConnection()?.disconnectAllChannels()
    }
}

fun Project.closeSFTP(panel: SftpExplorerPanel) {
    val manager = ToolWindowManager.getInstance(this)
    val toolWindow = manager.getToolWindow("ConnectHID:SFTP")
    toolWindow?.let { window ->
        // Find and remove all contents
        val existingPanel = toolWindow.contentManager.contents.firstOrNull { content ->
            (content.component as? SftpExplorerPanel)?.serverItem?.host == panel.fileSystem.server.host &&
                    (content.component as? SftpExplorerPanel)?.serverItem?.username == panel.fileSystem.server.username
        }
        if (existingPanel != null) {
            toolWindow.contentManager.removeContent(existingPanel, true)
        }
        if (window.contentManager.contentCount == 0) {
            window.hide()
        }
    }
}

fun Project.openSFTP(server: Server) {
    val manager = ToolWindowManager.getInstance(this)
    val toolWindow = manager.getToolWindow("ConnectHID:SFTP")
    if (toolWindow == null) {
        val window = manager.registerToolWindow("ConnectHID:SFTP") {
            icon = AllIcons.Nodes.WebFolder
            canCloseContent = true
            anchor = ToolWindowAnchor.RIGHT
            stripeTitle = Supplier{"SFTP Explorer"}
        }
        val sftpPanel = SftpExplorerPanel(this, server)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(sftpPanel, server.stmpName, false).apply {
            isCloseable = true
            setDisposer(sftpPanel)  // Ensure proper cleanup
        }
        window.contentManager.addContent(content)
        window.contentManager.setSelectedContent(content, true)
        window.activate { window.show() }
    } else {
        // Check if panel for this server already exists
        val existingPanel = toolWindow.contentManager.contents.firstOrNull { content ->
            (content.component as? SftpExplorerPanel)?.serverItem?.host == server.host &&
            (content.component as? SftpExplorerPanel)?.serverItem?.username == server.username
        }

        if (existingPanel != null) {
            toolWindow.contentManager.setSelectedContent(existingPanel, true)
            toolWindow.activate { toolWindow.show() }
        } else {
            val sftpPanel = SftpExplorerPanel(this, server)
            val contentFactory = ContentFactory.getInstance()
            val content = contentFactory.createContent(sftpPanel, server.stmpName, false).apply {
                isCloseable = true
                setDisposer(sftpPanel)  // Ensure proper cleanup
            }
            toolWindow.contentManager.addContent(content)
            toolWindow.contentManager.setSelectedContent(content, true)
            toolWindow.activate { toolWindow.show() }
        }
    }
}

