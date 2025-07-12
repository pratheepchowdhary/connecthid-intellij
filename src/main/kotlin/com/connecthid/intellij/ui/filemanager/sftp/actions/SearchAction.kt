package com.connecthid.intellij.ui.filemanager.sftp.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SearchEverywhereAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import javax.swing.JComponent

class SearchAction : SearchEverywhereAction() {
    override fun actionPerformed(e: AnActionEvent) {
        super.actionPerformed(e)
        val searchUI = SearchEverywhereManager.getInstance(e.project).currentlyShownUI
        searchUI.switchToTab("com.connecthid.intellij.ui.filemanager.sftp.SftpFileContributor")

    }

    override fun createCustomComponent(
        presentation: Presentation,
        place: String
    ): JComponent {
        presentation.apply { icon = AllIcons.Actions.Search }
        return super.createCustomComponent(presentation, place)
    }
}
