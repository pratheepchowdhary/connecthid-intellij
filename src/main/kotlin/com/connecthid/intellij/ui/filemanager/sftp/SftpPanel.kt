package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.models.AuthenticationMethod
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.services.SSHConnection
import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.jcraft.jsch.ChannelSftp
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class SftpPanel(val project: Project, val serverItem: Server) : JBPanel<SftpPanel>() {
    private val connectionService = ServerConnectionService()
    private val currentPath = "/"
    private val statusLabel = JLabel("Not connected")
    private val pathLabel = JLabel(currentPath)
    private val progressBar = JProgressBar()
    private val connectionStatusPanel = JPanel()
    private val fileOperationsPanel = JPanel()
    private val filePropertiesPanel = JPanel()
    private val fileTree = Tree()
    private val rootNode = DefaultMutableTreeNode("Root")

    init {
        layout = BorderLayout()
        background = JBColor.background()

        // Setup UI components
        setupConnectionStatusPanel()
        setupFileOperationsPanel()
        setupFileTree()
        setupFilePropertiesPanel()

        // Add components to main panel
        add(connectionStatusPanel, BorderLayout.NORTH)
        add(JScrollPane(fileTree), BorderLayout.CENTER)
        add(fileOperationsPanel, BorderLayout.SOUTH)
        add(filePropertiesPanel, BorderLayout.EAST)

        // Connect to server
        connectToServer()
    }

    private fun setupConnectionStatusPanel() {
        connectionStatusPanel.layout = BoxLayout(connectionStatusPanel, BoxLayout.Y_AXIS)
        connectionStatusPanel.border = JBUI.Borders.empty(5)

        val statusPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0

        // Path navigation
        gbc.gridx = 0
        gbc.gridy = 0
        statusPanel.add(pathLabel, gbc)

        // Status label
        gbc.gridy = 1
        statusPanel.add(statusLabel, gbc)

        // Progress bar
        gbc.gridy = 2
        progressBar.isVisible = false
        statusPanel.add(progressBar, gbc)

        connectionStatusPanel.add(statusPanel)
    }

    private fun setupFileOperationsPanel() {
        fileOperationsPanel.layout = BoxLayout(fileOperationsPanel, BoxLayout.X_AXIS)
        fileOperationsPanel.border = JBUI.Borders.empty(5)

        val uploadButton = JButton("Upload", AllIcons.Actions.Upload)
        val downloadButton = JButton("Download", AllIcons.Actions.Download)
        val newFolderButton = JButton("New Folder", AllIcons.Actions.NewFolder)
        val deleteButton = JButton("Delete", AllIcons.Actions.DeleteTag)
        val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)

        uploadButton.addActionListener { uploadFile() }
        downloadButton.addActionListener { downloadFile() }
        newFolderButton.addActionListener { createNewFolder() }
        deleteButton.addActionListener { deleteSelected() }
        refreshButton.addActionListener { refreshFileList() }

        fileOperationsPanel.add(uploadButton)
        fileOperationsPanel.add(downloadButton)
        fileOperationsPanel.add(newFolderButton)
        fileOperationsPanel.add(deleteButton)
        fileOperationsPanel.add(refreshButton)
    }

    private fun setupFileTree() {
        fileTree.model = DefaultTreeModel(rootNode)
        fileTree.showsRootHandles = true
        fileTree.isRootVisible = false
        fileTree.addTreeSelectionListener { updateFileProperties() }
    }

    private fun setupFilePropertiesPanel() {
        filePropertiesPanel.layout = BoxLayout(filePropertiesPanel, BoxLayout.Y_AXIS)
        filePropertiesPanel.preferredSize = Dimension(200, 0)
        filePropertiesPanel.border = JBUI.Borders.empty(5)
    }

    private fun getOrCreateConnection(): SSHConnection? {
        var connection = connectionService.getConnection(serverItem.host)
        if (connection == null || !connection.isConnected()) {
            val success = connectionService.connect(
                host = serverItem.host,
                username = serverItem.username,
                port = serverItem.port,
                password = if (serverItem.authMethod == AuthenticationMethod.PASSWORD) "aA1pradeep" else null,
                privateKeyPath = if (serverItem.authMethod == AuthenticationMethod.PRIVATE_KEY) serverItem.privateKeyPath else null
            )
            
            if (!success) {
                return null
            }
            
            connection = connectionService.getConnection(serverItem.host)
        }
        return connection
    }

    private fun connectToServer() {
        try {
            val connection = getOrCreateConnection()
            if (connection != null && connection.isConnected()) {
                statusLabel.text = "Connected to ${serverItem.host}"
                refreshFileList()
            } else {
                statusLabel.text = "Failed to connect to ${serverItem.host}"
            }
        } catch (e: Exception) {
            statusLabel.text = "Error: ${e.message}"
        }
    }

    private fun refreshFileList() {
        rootNode.removeAllChildren()
        try {
            val connection = getOrCreateConnection()
            if (connection != null) {
                val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
                channel?.connect()
                
                println("Connected to SFTP channel")
                println("Current path: $currentPath")
                
                try {
                    channel?.cd(currentPath)
                    println("Changed to directory: $currentPath")
                    
                    val fileList = channel?.ls(currentPath)
                    println("Found ${fileList?.size ?: 0} entries")
                    
                    fileList?.forEach { entry ->
                        try {
                            val lsEntry = entry as com.jcraft.jsch.ChannelSftp.LsEntry
                            val filename = lsEntry.filename
                            
                            if (filename != "." && filename != "..") {
                                val attrs = lsEntry.attrs
                                val node = SftpFileNode(filename, attrs)
                                rootNode.add(node)
                            }
                        } catch (e: Exception) {
                            println("Error processing entry: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    
                    (fileTree.model as DefaultTreeModel).reload()
                    fileTree.expandPath(TreePath(rootNode))
                } catch (e: Exception) {
                    println("Error listing directory: ${e.message}")
                    e.printStackTrace()
                    Messages.showErrorDialog(project, "Failed to list directory: ${e.message}", "Error")
                } finally {
                    channel?.disconnect()
                }
            } else {
                Messages.showErrorDialog(project, "Failed to connect to ${serverItem.host}", "Connection Error")
            }
        } catch (e: Exception) {
            println("General error: ${e.message}")
            e.printStackTrace()
            Messages.showErrorDialog(project, "Failed to list files: ${e.message}", "Error")
        }
    }

    private fun uploadFile() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
        val files = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
        val virtualFiles = files.choose(project)
        
        if (virtualFiles.isNotEmpty()) {
            progressBar.isVisible = true
            progressBar.isIndeterminate = true
            
            try {
                val connection = getOrCreateConnection()
                if (connection != null) {
                    for (file in virtualFiles) {
                        connection.uploadFile(file.path, "$currentPath/${file.name}")
                    }
                    refreshFileList()
                } else {
                    Messages.showErrorDialog(project, "Failed to connect to ${serverItem.host}", "Connection Error")
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to upload file: ${e.message}", "Error")
            } finally {
                progressBar.isVisible = false
            }
        }
    }

    private fun downloadFile() {
        val selectedNode = fileTree.selectionPath?.lastPathComponent as? SftpFileNode
        if (selectedNode != null && !selectedNode.isDirectory) {
            val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            val files = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val virtualFile = files.choose(project).firstOrNull()
            
            if (virtualFile != null) {
                progressBar.isVisible = true
                progressBar.isIndeterminate = true
                
                try {
                    val connection = getOrCreateConnection()
                    if (connection != null) {
                        connection.downloadFile("$currentPath/${selectedNode.name}", virtualFile.path)
                    } else {
                        Messages.showErrorDialog(project, "Failed to connect to ${serverItem.host}", "Connection Error")
                    }
                } catch (e: Exception) {
                    Messages.showErrorDialog(project, "Failed to download file: ${e.message}", "Error")
                } finally {
                    progressBar.isVisible = false
                }
            }
        }
    }

    private fun createNewFolder() {
        val folderName = Messages.showInputDialog(
            project,
            "Enter folder name:",
            "New Folder",
            Messages.getQuestionIcon()
        )
        
        if (!folderName.isNullOrBlank()) {
            try {
                val connection = getOrCreateConnection()
                if (connection != null) {
                    val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
                    channel?.connect()
                    channel?.mkdir("$currentPath/$folderName")
                    channel?.disconnect()
                    refreshFileList()
                } else {
                    Messages.showErrorDialog(project, "Failed to connect to ${serverItem.host}", "Connection Error")
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to create folder: ${e.message}", "Error")
            }
        }
    }

    private fun deleteSelected() {
        val selectedNode = fileTree.selectionPath?.lastPathComponent as? SftpFileNode
        if (selectedNode != null) {
            val result = Messages.showYesNoDialog(
                project,
                "Are you sure you want to delete ${selectedNode.name}?",
                "Confirm Delete",
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                try {
                    val connection = getOrCreateConnection()
                    if (connection != null) {
                        val channel = connection.getSession()?.openChannel("sftp") as? ChannelSftp
                        channel?.connect()
                        
                        if (selectedNode.isDirectory) {
                            channel?.rmdir("$currentPath/${selectedNode.name}")
                        } else {
                            channel?.rm("$currentPath/${selectedNode.name}")
                        }
                        
                        channel?.disconnect()
                        refreshFileList()
                    } else {
                        Messages.showErrorDialog(project, "Failed to connect to ${serverItem.host}", "Connection Error")
                    }
                } catch (e: Exception) {
                    Messages.showErrorDialog(project, "Failed to delete: ${e.message}", "Error")
                }
            }
        }
    }

    private fun updateFileProperties() {
        filePropertiesPanel.removeAll()
        
        val selectedNode = fileTree.selectionPath?.lastPathComponent as? SftpFileNode
        if (selectedNode != null) {
            val properties = listOf(
                "Name" to selectedNode.name,
                "Size" to formatFileSize(selectedNode.size),
                "Type" to if (selectedNode.isDirectory) "Directory" else "File",
                "Modified" to formatDate(selectedNode.modified),
                "Permissions" to formatPermissions(selectedNode.permissions)
            )
            
            properties.forEach { (name, value) ->
                val label = JLabel("$name: $value")
                filePropertiesPanel.add(label)
            }
        }
        
        filePropertiesPanel.revalidate()
        filePropertiesPanel.repaint()
    }

    private fun formatFileSize(size: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = size.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.2f %s", size, units[unitIndex])
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return formatter.format(date)
    }

    private fun formatPermissions(permissions: Int): String {
        val owner = permissions and 0x1C0 shr 6
        val group = permissions and 0x38 shr 3
        val others = permissions and 0x7
        
        return "${getPermissionString(owner)}${getPermissionString(group)}${getPermissionString(others)}"
    }

    private fun getPermissionString(permissions: Int): String {
        val read = if (permissions and 4 != 0) "r" else "-"
        val write = if (permissions and 2 != 0) "w" else "-"
        val execute = if (permissions and 1 != 0) "x" else "-"
        return read + write + execute
    }

    private inner class SftpFileNode(
        val name: String,
        val attrs: com.jcraft.jsch.SftpATTRS
    ) : DefaultMutableTreeNode(name) {
        val isDirectory: Boolean get() = attrs.isDir
        val size: Long get() = attrs.size
        val modified: Long get() = attrs.getMTime().toLong() * 1000
        val permissions: Int get() = attrs.permissions

        override fun toString(): String = name
    }
}