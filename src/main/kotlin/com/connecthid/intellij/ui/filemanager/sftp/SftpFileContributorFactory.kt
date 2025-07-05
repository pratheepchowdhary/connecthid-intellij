package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.vfs.SftpFile
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vfs.VirtualFile

class SftpFileContributorFactory : SearchEverywhereContributorFactory<SftpFile> {
    override fun createContributor(p0: AnActionEvent): SearchEverywhereContributor<SftpFile> {
        val contributor = SftpFileContributor()
        contributor.setProject(p0.project!!)
        return contributor
    }

}