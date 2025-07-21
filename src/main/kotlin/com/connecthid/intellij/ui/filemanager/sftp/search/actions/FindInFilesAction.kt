package com.connecthid.intellij.ui.filemanager.sftp.search.actions

import com.connecthid.intellij.connection.vfs.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FindInFilesAction(val project: Project, val file: VirtualFile): AnAction({ "Find in Folder" }, AllIcons.Actions.Find) {
    private  val sshService: ServerConnectionService  = project.getSSHService()
    override fun actionPerformed(p0: AnActionEvent) {
        val server=  (file.fileSystem as SftpFileSystem).server
        server.lastSearchPath = file.path
        sshService.searchServers.add(server)
        SearchEverywhereManager.getInstance(project).show("com.connecthid.intellij.ui.filemanager.sftp.search.SftpFileContributor","",p0)
    }
}