package com.connecthid.intellij.ui.filemanager.sftp.search.actions

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SearchEverywhereAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class SearchAction (val project: Project,val server: Server): SearchEverywhereAction() {
    // Use lazy initialization to defer service access until actually needed
    private val sshService by lazy { getSSHService() }

    override fun actionPerformed(e: AnActionEvent) {
        sshService.searchServers.clear()
        server.lastSearchPath = server.rootPath
        sshService.searchServers.add(server)
        super.actionPerformed(e)
        val searchUI = SearchEverywhereManager.getInstance(e.project).currentlyShownUI
        searchUI.switchToTab("com.connecthid.intellij.ui.filemanager.sftp.search.SftpFileContributor")
    }

    override fun createCustomComponent(
        presentation: Presentation,
        place: String
    ): JComponent {
        presentation.apply { icon = AllIcons.Actions.Search }
        return super.createCustomComponent(presentation, place)
    }
}
