package com.connecthid.intellij.ui.filemanager.sftp
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.TabsCustomizationStrategy

class SftpSeTabProvider: TabsCustomizationStrategy {
    override fun getSeparateTabContributors(contributors: List<SearchEverywhereContributor<*>>): List<SearchEverywhereContributor<*>> {
        return contributors.filter { it is SftpFileContributor }
    }
}