package com.connecthid.intellij.ui.filemanager.sftp.search

import com.connecthid.intellij.connection.vfs.SftpFile
import com.intellij.find.impl.TextSearchContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiFile

class SftpFileContributorFactory : SearchEverywhereContributorFactory<PsiFile> {
    override fun createContributor(p0: AnActionEvent): SearchEverywhereContributor<PsiFile> {
        return SftpFileContributor(p0)
    }

}