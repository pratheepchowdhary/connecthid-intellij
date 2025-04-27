package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.ui.filemanager.sftp.actions.SearchAction
import com.connecthid.intellij.ui.menu.MenuItem
import com.connecthid.intellij.ui.menu.MenuItemRenderer
import com.connecthid.intellij.vfs.FileChangeWatcher
import com.connecthid.intellij.vfs.SftpFile
import com.connecthid.intellij.vfs.SftpFileSystem
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath


class SftpExplorerPanel(val project: Project, val serverItem: Server) : JPanel(BorderLayout()), TreeExpansionListener {
    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode: SftpTreeNode
    private val fileSystem by lazy {
        println("Initializing SftpFileSystem for server: ${serverItem.host}")
        SftpFileSystem(project, serverItem)
    }
    private val fileWatcher by lazy {
        FileChangeWatcher(project)
    }
    val rootPath by lazy {
        if (serverItem.username == "root") "/root" else "/home/${serverItem.username}"
    }
    val rootDir by lazy {
        SftpFile(rootPath, fileSystem)
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var fileScrollView: com.intellij.ui.components.JBScrollPane
    private val loadingIcon by lazy {
        SpinningProgressIcon()
    }
    private val loadingLabel by lazy {
        JLabel("Loading files...", loadingIcon, SwingConstants.LEADING).apply {
            isVisible = true
        }
    }

    private val loadingStates = mutableSetOf<String>() // To track loading directories

    private val layeredPane by lazy {
        JLayeredPane().apply {
            layout = null // We'll manually position components
        }
    }

    private val loadingPanel by lazy {
        JPanel(BorderLayout()).apply {
            isOpaque = false
            background = Color(0, 0, 0, 0)
            add(loadingLabel, BorderLayout.CENTER)
            isVisible = false
        }
    }

    init {
        println("Initializing SftpPanel for server: ${serverItem.host}")
        val actionGroup = DefaultActionGroup()
        actionGroup.add(object : AnAction({ "Upload" }, AllIcons.Actions.Upload) {
                override fun actionPerformed(e: AnActionEvent) {
                    // Handle Move action
                    println("Move action triggered")
                }
        })
        actionGroup.add(object : AnAction({ "Create File" }, AllIcons.Actions.AddFile) {
                override fun actionPerformed(e: AnActionEvent) {
                    // Handle Move action
                    println("Move action triggered")
                }
        })
        actionGroup.add(object : AnAction({ "New Folder" }, AllIcons.Actions.NewFolder) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Move action
                println("Move action triggered")
            }
        })
        actionGroup.add(SearchAction())
        actionGroup.add(object : AnAction({ "Refresh" }, AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Move action
                println("Move action triggered")
            }
        })
        val actionManager = ActionManager.getInstance()
        val actionToolbar: ActionToolbar = actionManager.createActionToolbar("MyToolbar", actionGroup, true)

        actionToolbar.targetComponent = this
        val toolbarPanel = JBPanel<JBPanel<*>>(BorderLayout())
        toolbarPanel.add(actionToolbar.component, BorderLayout.EAST)
        add(toolbarPanel, BorderLayout.NORTH)
        // Create the root node
        rootNode = SftpTreeNode(rootDir)
        rootNode.add(DefaultMutableTreeNode("Loading..."))
        
        // Create the tree model
        treeModel = DefaultTreeModel(rootNode)
        
        // Create the tree
        tree = Tree(treeModel)
        tree.cellRenderer = SftpTreeCellRenderer()
        tree.addTreeExpansionListener(this)

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

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if(SwingUtilities.isRightMouseButton(e)){
                    tree.getPathForLocation(e.x,e.y)?.let {
                        val selectedNode = it.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val file = selectedNode.userObject as? VirtualFile ?: return
                        showPopupMenu(e.getX(), e.getY(), file);
                    }
                }
                else if(e.clickCount == 2){
                    tree.getPathForLocation(e.x,e.y)?.let {
                        val selectedNode = it.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val file = selectedNode.userObject as? VirtualFile ?: return
                        if(!file.isDirectory){
                            try {
                                project.openFileInIDE(file)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

        })
    }

    fun showPopupMenu(x: Int, y: Int, file: VirtualFile) {
        // Prepare the action group for menu items
        val actionGroup = DefaultActionGroup()

        // Add menu items as actions
        val newActionGroup = DefaultActionGroup("New", true)

        // Add submenu items under "New"
        newActionGroup.add(object : AnAction({ "File" },AllIcons.Actions.AddFile) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle File creation
                println("File action triggered")
            }
        })
        newActionGroup.add(object : AnAction({ "Folder" },AllIcons.Actions.NewFolder) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Folder creation
                println("Folder action triggered")
            }
        })
        newActionGroup.addSeparator()

        // Add the "New" action with its submenu to the main action group
        actionGroup.add(newActionGroup)
        actionGroup.add(object : AnAction({ "Move" }, AllIcons.Actions.MenuCut) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Move action
                println("Move action triggered")
            }
        })
        actionGroup.add(object : AnAction({ "Copy" }, AllIcons.Actions.Copy) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Copy action
                println("Copy action triggered")
            }
        })
        actionGroup.add(object : AnAction({ "Copy Path/Reference.." }) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Copy Path/Reference action
                println("Copy Path/Reference action triggered")
            }
        })
        actionGroup.add(object : AnAction({ "Paste" }, AllIcons.Actions.MenuPaste) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Paste action
                println("Paste action triggered")
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ "Find Usages" }) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Find Usages action
                println("Find Usages action triggered")
            }
        })
        actionGroup.add(object : AnAction({ "Rename" }, AllIcons.Actions.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Rename action
                println("Rename action triggered")
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ "Bookmarks" }) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Bookmarks action
                println("Bookmarks action triggered")
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ "Delete" }, AllIcons.Actions.DeleteTag) {
            override fun actionPerformed(e: AnActionEvent) {
                // Handle Delete action
                println("Delete action triggered")
            }
        })

        // Create the popup menu with the action group
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("CustomPopup", actionGroup)

        // Show the popup at the specified x, y location
        popupMenu.component.show(tree,x,y)
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

    private suspend fun loadChildren(selectedNode: DefaultMutableTreeNode, file: VirtualFile) = withContext(Dispatchers.Main) {
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
            } else{
                icon = loadingIcon
                append("Loading...", SimpleTextAttributes.REGULAR_ATTRIBUTES)
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
fun Project.openFileInIDE(file: VirtualFile){
    FileEditorManager.getInstance(this).openFile(file,true)
}


