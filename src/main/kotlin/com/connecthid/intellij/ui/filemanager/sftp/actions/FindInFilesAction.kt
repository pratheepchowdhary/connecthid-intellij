package com.connecthid.intellij.ui.filemanager.sftp.actions

import com.connecthid.intellij.FindInFilesDialog
import com.connecthid.intellij.getSSHService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

class FindInFilesAction(val project: Project): AnAction({ "Find in Folder" }, AllIcons.Actions.Find) {
    private  val sshService = project.getSSHService()
    override fun actionPerformed(p0: AnActionEvent) {
//        val dataContext = p0.dataContext
//
//        val model = FindModel().apply {
//            stringToFind = "" // or prompt user for string
//            customScope = getCustomScope(project)
//            isCustomScope = true
//        }
//        model.addObserver { model ->
//            val searchString = model.getStringToFind()
//            println(searchString)
//            //FindInProjectManager.getInstance(project).startFindInProject(model)
//        }
//        FindInProjectManager.getInstance(project).findInProject(dataContext,model)

        val dialog = FindInFilesDialog(project)
//        if (dialog.showAndGet()) {
//            val findModel = dialog.getFindModel()
//            // Handle the search with findModel
//        }
    }


    // 1. Define a custom search scope using VFS (search only in .txt files under project)
    fun getCustomScope(project: Project): GlobalSearchScope {
        val baseDir = project.baseDir ?: return GlobalSearchScope.projectScope(project)
        val txtFiles = mutableListOf<VirtualFile>()

        baseDir.children.forEach { file ->
            if (!file.isDirectory && file.extension == "txt") {
                txtFiles.add(file)
            }
        }

        return GlobalSearchScope.filesScope(project, txtFiles)
    }
}