package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SftpFolderPickerDialog(val project: Project, val serverItem: Server): DialogWrapper(true),TreeExpansionListener {
    // Use lazy initialization to defer service access until actually needed
    val sshConnection by lazy { project.getSSHService() }
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val fileSystem by lazy {
        println("Initializing SftpFileSystem for server: ${serverItem.host}")
        SftpFileSystem(project, serverItem)
    }
    val rootPath by lazy {
        if (serverItem.username == "root") "/root" else "/home/${serverItem.username}"
    }
    private val loadingIcon by lazy {
        SpinningProgressIcon()
    }
    private val loadingLabel by lazy {
        JLabel("Loading files...", loadingIcon, SwingConstants.LEADING).apply {
            isVisible = true
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

    lateinit var tree: Tree
    lateinit var treeModel: DefaultTreeModel
    lateinit var rootNode: SftpTreeNode
    lateinit var fileScrollView: com.intellij.ui.components.JBScrollPane
    private val layeredPane by lazy {
        JLayeredPane().apply {
            layout = null // We'll manually position components
        }
    }

    override fun createCenterPanel(): JComponent {
        rootNode = SftpTreeNode(fileSystem.findFileByPath(rootPath))
        rootNode.add(DefaultMutableTreeNode("Loading..."))
        // Create the tree model
        treeModel = DefaultTreeModel(rootNode)
        // Create the tree
        tree = Tree(treeModel)
        tree.cellRenderer = SftpTreeCellRenderer()
        tree.addTreeExpansionListener(this)
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
        val panel = panel {
            row(serverItem.stmpName) {
               cell(layeredPane)
               .align(com.intellij.ui.dsl.builder.Align.FILL)
            }
        }
       return panel
    }

    override fun treeExpanded(event: TreeExpansionEvent) {

    }

    override fun treeCollapsed(event: TreeExpansionEvent) {

    }

}