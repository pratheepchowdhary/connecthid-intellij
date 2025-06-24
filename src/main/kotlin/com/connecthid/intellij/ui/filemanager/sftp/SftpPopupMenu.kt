package com.connecthid.intellij.ui.filemanager.sftp

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
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
    val newActionGroup = DefaultActionGroup("New", true)
    // File creation
    newActionGroup.add(object : AnAction({ "File" }, AllIcons.Actions.AddFile) {
        override fun actionPerformed(e: AnActionEvent) {
            val fileName = JOptionPane.showInputDialog(tree, "Enter new file name:") ?: return
            if (fileName.isBlank()) return
            com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
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
                    JOptionPane.showMessageDialog(tree, "Failed to create file: ${ex.message}")
                }
            }
        }
    })
    // Folder creation
    newActionGroup.add(object : AnAction({ "Folder" }, AllIcons.Actions.NewFolder) {
        override fun actionPerformed(e: AnActionEvent) {
            val folderName = JOptionPane.showInputDialog(tree, "Enter new folder name:") ?: return
            if (folderName.isBlank()) return
            com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
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
                    JOptionPane.showMessageDialog(tree, "Failed to create folder: ${ex.message}")
                }
            }
        }
    })
    newActionGroup.addSeparator()
    actionGroup.add(newActionGroup)
    // Move (not implemented, placeholder)
    actionGroup.add(object : AnAction({ "Move" }, AllIcons.Actions.MenuCut) {
        override fun actionPerformed(e: AnActionEvent) {
            JOptionPane.showMessageDialog(tree, "Move action not implemented.")
        }
    })
    // Copy (not implemented, placeholder)
    actionGroup.add(object : AnAction({ "Copy" }, AllIcons.Actions.Copy) {
        override fun actionPerformed(e: AnActionEvent) {
            JOptionPane.showMessageDialog(tree, "Copy action not implemented.")
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
            JOptionPane.showMessageDialog(tree, "Paste action not implemented.")
        }
    })
    actionGroup.addSeparator()
    // Find Usages (not implemented, placeholder)
    actionGroup.add(object : AnAction({ "Find Usages" }) {
        override fun actionPerformed(e: AnActionEvent) {
            JOptionPane.showMessageDialog(tree, "Find Usages action not implemented.")
        }
    })
    // Rename
    actionGroup.add(object : AnAction({ "Rename" }, AllIcons.Actions.Edit) {
        override fun actionPerformed(e: AnActionEvent) {
            val newName = JOptionPane.showInputDialog(tree, "Enter new name:", file.name) ?: return
            if (newName.isBlank() || newName == file.name) return
            com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
                try {
                    val parent = file.parent
                    file.rename(this, newName)


                    val parentPath = if (parent != null) findTreePathForFile(selectedNode = selectedNode) else null
                    if (parentPath != null) {
                        val parentNode = parentPath.lastPathComponent as? DefaultMutableTreeNode
                        if (parentNode != null) {
                            // Optionally, reload children asynchronously if needed
                            treeModel.reload(parentNode)
                        }
                    }
                } catch (ex: Exception) {
                    JOptionPane.showMessageDialog(tree, "Failed to rename: ${ex.message}")
                }
            }
        }
    })
    actionGroup.addSeparator()
    // Bookmarks (not implemented, placeholder)
    actionGroup.add(object : AnAction({ "Bookmarks" }) {
        override fun actionPerformed(e: AnActionEvent) {
            JOptionPane.showMessageDialog(tree, "Bookmarks action not implemented.")
        }
    })
    actionGroup.addSeparator()
    // Delete
    actionGroup.add(object : AnAction({ "Delete" }, AllIcons.Actions.DeleteTag) {
        override fun actionPerformed(e: AnActionEvent) {
            val confirm = JOptionPane.showConfirmDialog(tree, "Delete ${file.name}?", "Confirm Delete", JOptionPane.YES_NO_OPTION)
            if (confirm != JOptionPane.YES_OPTION) return
            com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
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
                    JOptionPane.showMessageDialog(tree, "Failed to delete: ${ex.message}")
                }
            }
        }
    })

    val popupMenu = ActionManager.getInstance().createActionPopupMenu("CustomPopup", actionGroup)
    popupMenu.component.show(tree, x, y)
}

