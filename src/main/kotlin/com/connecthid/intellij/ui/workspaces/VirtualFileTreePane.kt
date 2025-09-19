package com.connecthid.intellij.ui.workspaces

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon
import javax.swing.UIManager

class VirtualFileTreePane(
    val project: Project,
    private val rootDir: VirtualFile
) : ProjectViewPane(project) {

    override fun getId(): String = "VIRTUAL_VFS_PANE"

    override fun getTitle(): String = "Virtual Files"

    override fun getIcon(): Icon =
        UIManager.getIcon("FileView.directoryIcon")

    override fun getWeight(): Int = 10

    override fun createStructure(): ProjectAbstractTreeStructureBase {
        return object : ProjectAbstractTreeStructureBase(project) {
            val viewSettings: ViewSettings = object : ViewSettings {
                override fun isShowMembers() = false
                override fun isShowModules() = true
            }

            override fun getRootElement(): Any =
                VirtualFileDirectoryNode(myProject, rootDir, viewSettings)

            override fun commit() {}
            override fun hasSomethingToCommit(): Boolean = false
        }
    }
}

class VirtualFileLeafNode(
    project: Project,
    private val file: VirtualFile,
    private val settings: ViewSettings
) : AbstractTreeNode<VirtualFile>(project, file) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = file.name
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun canNavigate() = true
    override fun canNavigateToSource() = true

    override fun navigate(requestFocus: Boolean) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}

class VirtualFileDirectoryNode(
    project: Project,
    private val dir: VirtualFile,
    private val settings: ViewSettings
) : AbstractTreeNode<VirtualFile>(project, dir) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = dir.name
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        return dir.children.map { child ->
            if (child.isDirectory) {
                VirtualFileDirectoryNode(project, child, settings)
            } else {
                VirtualFileLeafNode(project, child, settings)
            }
        }
    }
}

