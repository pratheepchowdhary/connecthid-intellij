package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.vfs.SftpFile
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent

class SftpFileContributorFactory : SearchEverywhereContributorFactory<SftpFile> {
    override fun createContributor(p0: AnActionEvent): SearchEverywhereContributor<SftpFile> {
        return SftpFileContributor(p0)
    }

}