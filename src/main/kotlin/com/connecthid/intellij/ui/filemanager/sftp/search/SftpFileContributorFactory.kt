package com.connecthid.intellij.ui.filemanager.sftp.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent

class SftpFileContributorFactory : SearchEverywhereContributorFactory<SftpPsiElement> {
    override fun createContributor(p0: AnActionEvent): SearchEverywhereContributor<SftpPsiElement> {
        return SftpFileContributor(p0)
    }
}