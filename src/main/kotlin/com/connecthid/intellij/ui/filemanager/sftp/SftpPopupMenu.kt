package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.terminal.openTerminal
import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.connection.sftp.downloadSftpFiles
import com.connecthid.intellij.connection.sftp.uploadSftpFiles
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.FindInFilesAction
import com.connecthid.intellij.utils.showNotification
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.Tree
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

fun showSftpPopupMenu(
    tree: Tree,
    project: Project,
    x: Int,
    y: Int
) {
    val selectedNodes = tree.getSelectedNodes(SftpTreeNode::class.java, null).toList()
    if (selectedNodes.size == 0) return
    val file = selectedNodes[0].userObject as? SftpFile ?: return
    val server = (file.fileSystem as? SftpFileSystem)?.server ?: return
    val multipleFiles = selectedNodes.size > 1
    val service = project.getSSHService()

    val actionGroup = DefaultActionGroup()
    val isDirectory = file.isDirectory
    if (!multipleFiles && isDirectory) {
        val newActionGroup = DefaultActionGroup("New", true)
        // File creation
        newActionGroup.add(object : AnAction({ "File" }, AllIcons.Actions.AddFile) {
            override fun actionPerformed(e: AnActionEvent) {
              tree.createFile(project)
            }
        })
        // Folder creation
        newActionGroup.add(object : AnAction({ "Folder" }, AllIcons.Actions.NewFolder) {
            override fun actionPerformed(e: AnActionEvent) {
                tree.createFolder(project)
            }
        })
        newActionGroup.addSeparator()
        actionGroup.add(newActionGroup)

    }
    // Move (not implemented, placeholder)
    actionGroup.add(object : AnAction({ "Move" }, AllIcons.Actions.MenuCut) {
        override fun actionPerformed(e: AnActionEvent) {
            Messages.showInfoMessage(tree, "Move action not implemented.", "Info")
        }
    })
    // Copy (not implemented, placeholder)
    actionGroup.add(object : AnAction({ "Copy" }, AllIcons.Actions.Copy) {
        override fun actionPerformed(e: AnActionEvent) {
            Messages.showInfoMessage(tree, "Copy action not implemented.", "Info")
        }
    })
    if (!multipleFiles) {
        // Copy Path/Reference
        actionGroup.add(object : AnAction({ "Copy Path/Reference.." }) {
            override fun actionPerformed(e: AnActionEvent) {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                val sel = StringSelection(file.path)
                clipboard.setContents(sel, sel)
            }
        })
        // Paste (not implemented, placeholder)
        actionGroup.add(object : AnAction({ "Paste" }, AllIcons.Actions.MenuPaste) {
            override fun actionPerformed(e: AnActionEvent) {
                Messages.showInfoMessage(tree, "Paste action not implemented.", "Info")
            }
        })
    }
    if(isDirectory){
        // add to workspace
        actionGroup.add(object : AnAction({ "Add to Workspace" }, AllIcons.Actions.AddDirectory){
            override fun actionPerformed(e: AnActionEvent) {
                // show dialog with workspace name as input with folder name
                invokeLater {
                    val folderName = Messages.showInputDialog(
                        tree,
                        "Enter workspace name:",
                        "Add to Workspace",
                        Messages.getQuestionIcon(),
                        file.name,
                        null
                    ) ?: return@invokeLater
                    if(folderName.isBlank()) return@invokeLater
                    service.addWorkspace(server = server.stmpName, path = file.path, folderName = folderName)
                }
            }
        })
    }
    actionGroup.addSeparator()
    if (!multipleFiles) {
//        actionGroup.add(object : AnAction({ "Find Usages" }) {
//            override fun actionPerformed(e: AnActionEvent) {
//                Messages.showInfoMessage(tree, "Find Usages action not implemented.", "Info")
//            }
//        })
        if (isDirectory) {
            // Find in Folder
            actionGroup.add(FindInFilesAction(project, file))
        }
        // Rename
        actionGroup.add(object : AnAction({ "Rename" }, AllIcons.Actions.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                   tree.renameNode(project)
            }
        })
        actionGroup.addSeparator()
        if (isDirectory) {
            // Open in Terminal
            actionGroup.add(object : AnAction({ "Open in Terminal" }, AllIcons.Debugger.Console) {
                override fun actionPerformed(e: AnActionEvent) {
                    project.openTerminal(server, file.path)

                }
            })

        }
        // Bookmarks (not implemented, placeholder)
        actionGroup.add(object : AnAction({ "Bookmarks" }) {
            override fun actionPerformed(e: AnActionEvent) {
                Messages.showInfoMessage(tree, "Bookmarks action not implemented.", "Info")
            }
        })

        // Local History
        actionGroup.add(object : AnAction({ "Local History" }, AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) {
                //LocalHistory.getInstance().startAction()
            }
        })

        // Reload from Disk
        actionGroup.add(object : AnAction({ "Reload from Remote" }, AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                tree.refreshSelectedNode()
            }
        });
    }
    actionGroup.addSeparator()
    // Delete
    if(multipleFiles || file.isWritable){
    actionGroup.add(object : AnAction({ "Delete" }, AllIcons.Actions.DeleteTag) {
        override fun actionPerformed(e: AnActionEvent) {
             tree.deleteSelectedNode(project)
        }
    })
    }
    // --- Upload and Download Actions ---
    if (isDirectory&&!multipleFiles) {
        actionGroup.add(object : AnAction({ "Upload..." }, AllIcons.Actions.Upload) {
            override fun actionPerformed(e: AnActionEvent) {
                tree.uploadFiles()
            }
        })
    }
    actionGroup.add(object : AnAction({ "Download..." }, AllIcons.Actions.Download) {
        override fun actionPerformed(e: AnActionEvent) {
            // Download handler (implemented below)
            tree.downloadFiles()
        }
    })
    // --- End Upload and Download Actions ---

    val popupMenu = ActionManager.getInstance().createActionPopupMenu("CustomPopup", actionGroup)
    popupMenu.component.show(tree, x, y)

}


private fun findTreePathForFile(selectedNode: DefaultMutableTreeNode,treeModel: DefaultTreeModel): TreePath? {
    val node = selectedNode.parent ?: return null
    return TreePath(treeModel.getPathToRoot(node))
}

fun Tree.createFolder(project: Project){
    val selectedNodes = this.getSelectedNodes(SftpTreeNode::class.java, null).toList()
    if (selectedNodes.size != 1) return
    val directory = selectedNodes[0].userObject as? SftpFile ?: return
    if(!directory.isDirectory) return
    ApplicationManager.getApplication().invokeLater {
        val folderName = Messages.showInputDialog(
            this,
            "Enter new folder name:",
            "New Folder",
            Messages.getQuestionIcon()
        ) ?: return@invokeLater
        if (folderName.isBlank()) return@invokeLater
        runWriteAction {
            try {
                val newDir = directory.createChildDirectory(this, folderName)
                val parentPath = TreePath(selectedNodes[0].path)
                val parentNode = parentPath.lastPathComponent as? DefaultMutableTreeNode
                val treeModel = this.model as? DefaultTreeModel ?: return@runWriteAction
                if (parentNode != null) {
                    // Check if node already exists (avoid duplicates)
                    val exists = (0 until parentNode.childCount).any {
                        val child = parentNode.getChildAt(it) as? DefaultMutableTreeNode
                        (child?.userObject as? VirtualFile)?.name == newDir.name
                    }
                    if (!exists) {
                        val newNode = SftpTreeNode(newDir)
                        newNode.add(DefaultMutableTreeNode("Loading..."))
                        parentNode.add(newNode)
                        treeModel.nodesWereInserted(parentNode, intArrayOf(parentNode.getIndex(newNode)))
                        // Select and scroll to the new node
                        SwingUtilities.invokeLater {
                            val newPath = parentPath.pathByAddingChild(newNode)
                            this.selectionPath = newPath
                            this.scrollPathToVisible(newPath)
                        }
                    }
                }
                project.showNotification("Folder Created", "Successfully created folder: ${newDir.name}",com.intellij.notification.NotificationType.INFORMATION)
            } catch (ex: Exception) {
                project.showNotification("Folder Creation Failed", "Failed to create folder: ${ex.message}",com.intellij.notification.NotificationType.ERROR)
            }
        }
    }
}

fun Tree.createFile(project: Project){
    val selectedNodes = this.getSelectedNodes(SftpTreeNode::class.java, null).toList()
    if (selectedNodes.size != 1) return
    val directory = selectedNodes[0].userObject as? SftpFile ?: return
    if(!directory.isDirectory) return
    ApplicationManager.getApplication().invokeLater {
        val fileName = Messages.showInputDialog(
            this,
            "Enter new file name:",
            "New File",
            Messages.getQuestionIcon()
        ) ?: return@invokeLater
        if (fileName.isBlank()) return@invokeLater
        runWriteAction {
            try {
                val newFile = directory.createChildData(this, fileName)
                FileEditorManager.getInstance(project).openFile(newFile, true)
                // Add only the new node to the tree if not already present
                val parentPath = TreePath(selectedNodes[0].path)
                val parentNode = parentPath.lastPathComponent as? DefaultMutableTreeNode
                val treeModel = this.model as? DefaultTreeModel ?: return@runWriteAction

                if (parentNode != null) {
                    // Check if node already exists (avoid duplicates)
                    val exists = (0 until parentNode.childCount).any {
                        val child = parentNode.getChildAt(it) as? DefaultMutableTreeNode
                        (child?.userObject as? VirtualFile)?.name == newFile.name
                    }
                    if (!exists) {
                        val newNode = SftpTreeNode(newFile)
                        parentNode.add(newNode)
                        treeModel.nodesWereInserted(parentNode, intArrayOf(parentNode.getIndex(newNode)))
                        // Select and scroll to the new node
                        SwingUtilities.invokeLater {
                            val newPath = parentPath.pathByAddingChild(newNode)
                            this.selectionPath = newPath
                            this.scrollPathToVisible(newPath)
                        }
                    }
                }
                project.showNotification("File Created", "Successfully created file: ${newFile.name}",com.intellij.notification.NotificationType.INFORMATION)
            } catch (ex: Exception) {
                project.showNotification("File Creation Failed", "Failed to create file: ${ex.message}",com.intellij.notification.NotificationType.ERROR)
            }
        }
    }
}

fun Tree.downloadFiles(){
    val selectedNodes = this.getSelectedNodes(SftpTreeNode::class.java, null).toList()
    if (selectedNodes.size == 0) return
    val files = selectedNodes.mapNotNull { it.userObject as? SftpFile }
    if (files.isEmpty()) return
    val fileSystem = files[0].fileSystem as? SftpFileSystem ?: return
    fileSystem.downloadSftpFiles(files)
}

fun Tree.uploadFiles(){
    val selectedNodes = this.getSelectedNodes(SftpTreeNode::class.java, null).toList()
    if (selectedNodes.size != 1) return
    val file = selectedNodes[0].userObject as? SftpFile ?: return
    if (!file.isDirectory) return
    val fileSystem = file.fileSystem as? SftpFileSystem ?: return
    fileSystem.uploadSftpFiles(file)

}

fun Tree.renameNode(project: Project){
    val selectedNodes = this.getSelectedNodes(SftpTreeNode::class.java, null).toList()
    if (selectedNodes.size != 1) return
    val file = selectedNodes[0].userObject as? SftpFile ?: return
    val oldName = file.name
    val newName = Messages.showInputDialog(
        this,
        "Enter new name:",
        "Rename",
        Messages.getQuestionIcon(),
        oldName,
        null
    ) ?: return
    if (newName.isBlank() || newName == oldName) return
     runWriteAction {
        try {
            val parent = file.parent
            val newPath = if (parent != null) "${parent.path}/$newName" else newName
            file.rename(this, newName)
            val renamedFile = file.fileSystem.findFileByPath(newPath)
            if (renamedFile != null) {
                selectedNodes[0].userObject = renamedFile
                selectedNodes[0].file = renamedFile
                val treeModel = this.model as? DefaultTreeModel ?: return@runWriteAction
                treeModel.reload(selectedNodes[0])
                if (renamedFile.isDirectory && this.isExpanded(TreePath(selectedNodes[0].path))) {
                    val path = TreePath(selectedNodes[0].path)
                    this.collapsePath(path)
                    selectedNodes[0].removeAllChildren()
                    selectedNodes[0].add(DefaultMutableTreeNode("Loading..."))
                    this.expandPath(path)
                }
            }
            project.showNotification("Rename Successful", "Successfully renamed to $newName",com.intellij.notification.NotificationType.INFORMATION)
        } catch (ex: Exception) {
            project.showNotification("Rename Failed", "Failed to rename: ${ex.message}",com.intellij.notification.NotificationType.ERROR)
        }
    }

}

fun Tree.deleteSelectedNode(project: Project) {
   val selectedNodes = this.getSelectedNodes(SftpTreeNode::class.java, null).toList()
    if (selectedNodes.size == 0) return
    val file = selectedNodes[0].userObject as? SftpFile ?: return
    val treeModel = this.model as? DefaultTreeModel ?: return
    val multipleFiles = selectedNodes.size > 1
    val confirm = Messages.showYesNoDialog(
        this,
        "Delete ${if(!multipleFiles) file.name else "${selectedNodes.size} files"} ?",
        "Confirm Delete",
        Messages.getQuestionIcon()
    )
    if (confirm != Messages.YES) return


    runWriteAction {
        try {
            selectedNodes.forEach {
                val f = (it.userObject as? SftpFile)?: return@forEach
                if (!f.isWritable) return@forEach
                if (FileEditorManager.getInstance(project).isFileOpen(f)) {
                    FileEditorManager.getInstance(project).closeFile(f)
                }
                val parent = f.parent
                val parentPath = if (parent != null) findTreePathForFile(selectedNode = it,treeModel) else null
                val nodePath = TreePath(it.path)
                f.delete(this)
                if (parentPath != null) {
                    val parentNode = parentPath.lastPathComponent as? DefaultMutableTreeNode
                    val node = nodePath.lastPathComponent as? DefaultMutableTreeNode
                    if (parentNode != null && node != null) {
                        parentNode.remove(node)
                        treeModel.reload(parentNode)
                    }
                }
            }
            project.showNotification("Delete Successful", "Successfully deleted ${if(!multipleFiles) file.name else "${selectedNodes.size} files"}",com.intellij.notification.NotificationType.INFORMATION)
        } catch (ex: Exception) {
            project.showNotification("Delete Failed", "Failed to delete: ${ex.message}",com.intellij.notification.NotificationType.ERROR)
        }
    }


}

fun Tree.refreshSelectedNode() {
    val selectedNodes: List<SftpTreeNode> = this.getSelectedNodes(SftpTreeNode::class.java, null).toList()
    if(selectedNodes.size == 1){
        val node = selectedNodes.first()
        val file = node.userObject as? SftpFile ?: return
        if (!file.isDirectory) return
        file.refresh(false,true){

        }
        node.removeAllChildren()
        node.add(DefaultMutableTreeNode("Loading...")) // Placeholder for loading
        (this.model as? DefaultTreeModel)?.reload(node)
         val path = TreePath(node.path)
         collapsePath(path)
         expandPath(path)
        return
    }
}