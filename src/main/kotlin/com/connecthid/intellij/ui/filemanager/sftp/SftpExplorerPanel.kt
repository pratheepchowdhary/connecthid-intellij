package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.Workspace
import com.connecthid.intellij.ui.filemanager.sftp.actions.SftpToolbarActions
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.ide.DataManager
import com.intellij.ide.DeleteProvider
import com.intellij.ide.PasteProvider
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.openapi.actionSystem.*
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
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class SftpExplorerPanel(val project: Project, val serverItem: Server, showToolbar: Boolean = true,val panelId: String="ConnectHID:SFTP",val rootPath: String = serverItem.rootPath) : JPanel(BorderLayout()), TreeExpansionListener, TreeSelectionListener, com.intellij.openapi.Disposable ,CopyProvider,
    DeleteProvider,
    PasteProvider, CutProvider{
    val tree: Tree
    val treeModel: DefaultTreeModel
    val rootNode: SftpTreeNode
    val fileSystem by lazy {
        println("Initializing SftpFileSystem for server: ${serverItem.host}")
        SftpFileSystem(project, serverItem)
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
        if (showToolbar) {
            val toolbarActions = SftpToolbarActions(this, serverItem = serverItem)
            add(toolbarActions.createToolbar(), BorderLayout.NORTH)
        }

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
                        showPopupMenu(e.x, e.y)
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
        DataManager.registerDataProvider(tree, DataProvider { dataId: String? ->
            when {
                PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
                PlatformDataKeys.PASTE_PROVIDER.`is`(dataId) -> this
                PlatformDataKeys.CUT_PROVIDER.`is`(dataId) -> this
                PlatformDataKeys.DELETE_ELEMENT_PROVIDER.`is`(dataId) -> this
                else -> null
            }
        })
        DnDSupport.createBuilder(tree).setDisposableParent(this).setBeanProvider { info ->
            val sel = tree.selectionPaths?.toList().orEmpty()
            if (sel.isEmpty()) null else DnDDragStartBean(
                DraggedNodes(sel)
            )
        }.setTargetChecker { event ->
            val tp = tree.getPathForLocation(event.point.x, event.point.y)
            val target = tp?.lastPathComponent as? SftpTreeNode
            val dragged = (event.attachedObject as? DraggedNodes)
                ?.paths?.map { it.lastPathComponent as SftpTreeNode }
                ?: emptyList()

            val ok = dragged.isNotEmpty() && canDrop(dragged, target)

            if (ok && tp != null) {
                val bounds = tree.getPathBounds(tp)
                if (bounds != null) {
                    event.setHighlighting(tree, DnDEvent.DropTargetHighlightingType.RECTANGLE)
                }
            } else {
                event.hideHighlighter()
            }

            ok
        }.setDropHandler { event ->
            val dragged = (event.attachedObject as? DraggedNodes)
                ?.paths?.map { it.lastPathComponent as SftpTreeNode } ?: return@setDropHandler
            val tp = tree.getPathForLocation(event.point.x, event.point.y) ?: return@setDropHandler
            val target = tp.lastPathComponent as? SftpTreeNode ?: return@setDropHandler

            if (!canDrop(dragged, target)) return@setDropHandler

            // 🔹 Update the tree model
            for (node in dragged) {
                val parent = node.parent as? SftpTreeNode ?: continue
                treeModel.removeNodeFromParent(node)
                treeModel.insertNodeInto(node, target, target.childCount)
            }

            // 🔹 Expand the drop target so user sees changes
            tree.expandPath(tp)
        }.install()
    }
    fun canDrop(dragged: List<SftpTreeNode>, target: SftpTreeNode?): Boolean {
        if (target == null || !target.file.isDirectory) return false
        // prevent dropping into itself/descendants
        if (dragged.any { it == target || it.isNodeDescendant(target) }) return false
        return true
    }

    fun showPopupMenu(x: Int, y: Int) {
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



    override fun valueChanged(event: TreeSelectionEvent?) {
        val selectedPath = event?.path
        val selectedNode = selectedPath?.lastPathComponent as? DefaultMutableTreeNode
        if (selectedNode != null) {
            lastSelectedNode = selectedNode
            println("Tree item selected: ${selectedNode.userObject}")
        } else {
            println("No tree item selected")
            lastSelectedNode = null
        }
    }

    override fun dispose() {
        // Cleanup resources
        coroutineScope.cancel()
        DataManager.removeDataProvider(tree)
        fileSystem.getConnection()?.disconnectAllChannels()
    }

    override fun performCopy(p0: DataContext) {
        println("Copy action triggered")
        tree.copy()
    }

    override fun isCopyEnabled(p0: DataContext): Boolean {
        val sftpFiles = tree.selectionPaths?.mapNotNull { path ->
            (path.lastPathComponent as? SftpTreeNode)?.file
        } ?: return false

        return   sftpFiles.size > 0
    }

    override fun isCopyVisible(p0: DataContext): Boolean {
        return true
    }

    override fun performPaste(p0: DataContext) {
        println("Paste action triggered")
        tree.paste()
    }

    override fun isPastePossible(p0: DataContext): Boolean {
        val sftpFiles = tree.selectionPaths?.mapNotNull { path ->
            (path.lastPathComponent as? SftpTreeNode)?.file
        } ?: return false

        return   sftpFiles.size == 1 && sftpFiles[0].isDirectory && sftpFiles[0].isWritable

    }

    override fun isPasteEnabled(p0: DataContext): Boolean {
        return true
    }

    override fun performCut(p0: DataContext) {
        println("Cut action triggered")
        tree.cut()
    }

    override fun isCutEnabled(p0: DataContext): Boolean {
        val sftpFiles = tree.selectionPaths?.mapNotNull { path ->
            (path.lastPathComponent as? SftpTreeNode)?.file
        } ?: return false
        // check all files are writable
        if(sftpFiles.any { !it.isWritable }){
            return false
        }
        return   sftpFiles.size > 0
    }

    override fun isCutVisible(p0: DataContext): Boolean {
        return  true
    }

    override fun deleteElement(dataContext: DataContext) {
        tree.deleteSelectedNode(project)
    }

    override fun canDeleteElement(dataContext: DataContext): Boolean {
        val sftpFiles = tree.selectionPaths?.mapNotNull { path ->
            (path.lastPathComponent as? SftpTreeNode)?.file
        } ?: return false
        // check all files are writable
        if(sftpFiles.any { !it.isWritable }){
            return false
        }
        return   sftpFiles.size > 0
    }
}

fun Project.closeSFTP(panel: SftpExplorerPanel) {
    val manager = ToolWindowManager.getInstance(this)
    val toolWindow = manager.getToolWindow(panel.panelId)
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

fun Project.openProject(server: Server, workspace: Workspace )  {

    val manager = ToolWindowManager.getInstance(this)
    val panelId = server.stmpName+":"+workspace.folderName
    val toolWindow = manager.getToolWindow(panelId)
    if (toolWindow == null) {
        val window = manager.registerToolWindow(panelId) {
            icon = AllIcons.Nodes.WebFolder
            canCloseContent = false
            anchor = ToolWindowAnchor.LEFT
            stripeTitle = Supplier{workspace.folderName}
        }
        val sftpPanel = SftpExplorerPanel(this, server,false,panelId,workspace.path)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(sftpPanel, "", true).apply {
            isCloseable = false
            setDisposer(sftpPanel)  // Ensure proper cleanup
        }
        window.setTitleActions(listOf(object : AnAction({ "Stop" }, AllIcons.Actions.Suspend) {
            override fun actionPerformed(e: AnActionEvent) {
              sftpPanel.project.closeSFTP(sftpPanel)
            }
        }))
        window.contentManager.addContent(content)
        window.activate { window.show() }
    } else {
        // Check if panel for this server already exists
        val existingPanel = toolWindow.contentManager.contents.firstOrNull { content ->
            (content.component as? SftpExplorerPanel)?.serverItem?.host == server.host &&
                    (content.component as? SftpExplorerPanel)?.serverItem?.username == server.username
        }

        if (existingPanel != null) {
            toolWindow.activate { toolWindow.show() }
        } else {
            val sftpPanel = SftpExplorerPanel(this, server,false,panelId,workspace.path)
            val contentFactory = ContentFactory.getInstance()
            val content = contentFactory.createContent(sftpPanel, "", true).apply {
                isCloseable = false
                setDisposer(sftpPanel)  // Ensure proper cleanup
            }
            toolWindow.setTitleActions(listOf(object : AnAction({ "Stop" }, AllIcons.Actions.Suspend) {
                override fun actionPerformed(e: AnActionEvent) {
                    sftpPanel.project.closeSFTP(sftpPanel)
                }
            }))
            toolWindow.contentManager.addContent(content)
            toolWindow.activate { toolWindow.show() }
        }
    }

    fun showEditConfigurationsDialog(project: Project) {
        val dialog = EditConfigurationsDialog(project)
        dialog.show()  // Opens the dialog
    }
}



fun Project.openSFTP(server: Server,side: ToolWindowAnchor = ToolWindowAnchor.RIGHT) {
    val manager = ToolWindowManager.getInstance(this)
    val toolWindow = manager.getToolWindow("ConnectHID:SFTP")
    if (toolWindow == null) {
        val window = manager.registerToolWindow("ConnectHID:SFTP") {
            icon = AllIcons.Nodes.WebFolder
            canCloseContent = true
            anchor = side
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

// What gets carried around in drag event
data class DraggedNodes(val paths: List<TreePath>)



