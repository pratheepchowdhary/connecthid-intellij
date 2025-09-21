package com.connecthid.intellij.ui.filemanager.sftp.search.actions

import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FindInFilesAction(val project: Project, val file: VirtualFile): AnAction({ "Find in Folder" }, AllIcons.Actions.Find) {
    // Use lazy initialization to defer service access until actually needed
    private val sshService by lazy { getSSHService() }

    override fun actionPerformed(p0: AnActionEvent) {
        val server=  (file as SftpFile).server
        server.lastSearchPath = file.path
        sshService.searchServers.add(server)
        SearchEverywhereManager.getInstance(project).show("com.connecthid.intellij.ui.filemanager.sftp.search.SftpFileContributor","",p0)
    }
}