package com.connecthid.intellij.ui.filemanager.sftp

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vfs.VirtualFile

class SftpFileContributorFactory : SearchEverywhereContributorFactory<VirtualFile> {
    override fun createContributor(p0: AnActionEvent): SearchEverywhereContributor<VirtualFile> {
        val contributor = SftpFileContributor()
        contributor.setProject(p0.project!!)
        return contributor
    }

}