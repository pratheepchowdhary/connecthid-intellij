package com.connecthid.intellij.ui.filemanager.sftp

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

fun showSftpPopupMenu(
    tree: JTree,
    project: Project,
    treeModel: DefaultTreeModel,
    selectedNode: DefaultMutableTreeNode,
    x: Int,
    y: Int
) {

     fun findTreePathForFile(selectedNode: DefaultMutableTreeNode): TreePath? {
        val node = selectedNode.parent ?: return null
        return TreePath(treeModel.getPathToRoot(node))
    }

    val file = selectedNode.userObject as? VirtualFile ?: return
    val actionGroup = DefaultActionGroup()
    val isDirectory = file.isDirectory
    if (isDirectory) {
        val newActionGroup = DefaultActionGroup("New", true)
        // File creation
        newActionGroup.add(object : AnAction({ "File" }, AllIcons.Actions.AddFile) {
            override fun actionPerformed(e: AnActionEvent) {
                val fileName = Messages.showInputDialog(
                    tree,
                    "Enter new file name:",
                    "New File",
                    Messages.getQuestionIcon()
                ) ?: return
                if (fileName.isBlank()) return
                ApplicationManager.getApplication().runWriteAction {
                    try {
                        val newFile = file.createChildData(this, fileName)
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(newFile, true)
                        // Add only the new node to the tree if not already present
                        val parentPath =  TreePath(selectedNode.path)
                        val parentNode = parentPath.lastPathComponent as? DefaultMutableTreeNode
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
                                    tree.selectionPath = newPath
                                    tree.scrollPathToVisible(newPath)
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Messages.showErrorDialog(tree, "Failed to create file: ${ex.message}")
                    }
                }
            }
        })
        // Folder creation
        newActionGroup.add(object : AnAction({ "Folder" }, AllIcons.Actions.NewFolder) {
            override fun actionPerformed(e: AnActionEvent) {
                val folderName = Messages.showInputDialog(
                    tree,
                    "Enter new folder name:",
                    "New Folder",
                    Messages.getQuestionIcon()
                ) ?: return
                if (folderName.isBlank()) return
                ApplicationManager.getApplication().runWriteAction {
                    try {
                        val newDir = file.createChildDirectory(this, folderName)
                        val parentPath = TreePath(selectedNode.path)
                        val parentNode = parentPath.lastPathComponent as? DefaultMutableTreeNode
                        if (parentNode != null) {
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
                                    tree.selectionPath = newPath
                                    tree.scrollPathToVisible(newPath)
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Messages.showErrorDialog(tree, "Failed to create folder: ${ex.message}")
                    }
                }
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
    actionGroup.addSeparator()
    // Find Usages (not implemented, placeholder)
    actionGroup.add(object : AnAction({ "Find Usages" }) {
        override fun actionPerformed(e: AnActionEvent) {
            Messages.showInfoMessage(tree, "Find Usages action not implemented.", "Info")
        }
    })
    if(isDirectory){
        // Find in Folder
        actionGroup.add(object : AnAction({ "Find in Folder" }, AllIcons.Actions.Find) {
            override fun actionPerformed(e: AnActionEvent) {
                // Find in Folder logic (placeholder)
                Messages.showInfoMessage(tree, "Find in Folder action not implemented.", "Info")
            }
        })
    }
    // Rename
    actionGroup.add(object : AnAction({ "Rename" }, AllIcons.Actions.Edit) {
        override fun actionPerformed(e: AnActionEvent) {
            val oldName = file.name
            val newName = Messages.showInputDialog(
                tree,
                "Enter new name:",
                "Rename",
                Messages.getQuestionIcon(),
                oldName,
                null
            ) ?: return
            if (newName.isBlank() || newName == oldName) return
            ApplicationManager.getApplication().runWriteAction {
                try {
                    val parent = file.parent
                    val newPath = if (parent != null) "${parent.path}/$newName" else newName
                    file.rename(this, newName)
                    val renamedFile = file.fileSystem.findFileByPath(newPath)
                    if(selectedNode is SftpTreeNode && renamedFile != null) {
                        selectedNode.userObject = renamedFile
                        selectedNode.file = renamedFile
                        treeModel.reload(selectedNode)
                        if (renamedFile.isDirectory && tree.isExpanded(TreePath(selectedNode.path))) {
                            val path = TreePath(selectedNode.path)
                            tree.collapsePath(path)
                            selectedNode.removeAllChildren()
                            selectedNode.add(DefaultMutableTreeNode("Loading..."))
                            tree.expandPath(path)
                        }
                    }
                } catch (ex: Exception) {
                    Messages.showErrorDialog(tree, "Failed to rename: ${ex.message}")
                }
            }
        }
    })
    actionGroup.addSeparator()
    if(isDirectory){
    // Open in Terminal
    actionGroup.add(object : AnAction({ "Open in Terminal" }, AllIcons.Debugger.Console) {
        override fun actionPerformed(e: AnActionEvent) {
            // Open in Terminal logic (platform dependent, here just a placeholder)
            Messages.showInfoMessage(tree, "Open in Terminal action not implemented.", "Info")
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
    actionGroup.addSeparator()
    // Delete
    actionGroup.add(object : AnAction({ "Delete" }, AllIcons.Actions.DeleteTag) {
        override fun actionPerformed(e: AnActionEvent) {
            val confirm = Messages.showYesNoDialog(
                tree,
                "Delete ${file.name}?",
                "Confirm Delete",
                Messages.getQuestionIcon()
            )
            if (confirm != Messages.YES) return
            if(FileEditorManager.getInstance(project).isFileOpen(file)){
                FileEditorManager.getInstance(project).closeFile(file)
            }
            ApplicationManager.getApplication().runWriteAction {
                try {
                    val parent = file.parent
                    val parentPath = if (parent != null) findTreePathForFile(selectedNode = selectedNode) else null
                    val nodePath = TreePath(selectedNode.path)
                    file.delete(this)
                    if (parentPath != null) {
                        val parentNode = parentPath.lastPathComponent as? DefaultMutableTreeNode
                        val node = nodePath.lastPathComponent as? DefaultMutableTreeNode
                        if (parentNode != null && node != null) {
                            parentNode.remove(node)
                            treeModel.reload(parentNode)
                        }
                    }
                } catch (ex: Exception) {
                    Messages.showErrorDialog(tree, "Failed to delete: ${ex.message}")
                }
            }
        }
    })

    val popupMenu = ActionManager.getInstance().createActionPopupMenu("CustomPopup", actionGroup)
    popupMenu.component.show(tree, x, y)

}
