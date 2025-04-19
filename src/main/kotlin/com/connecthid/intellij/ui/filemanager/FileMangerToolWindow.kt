package com.connecthid.intellij.ui.filemanager

import com.connecthid.intellij.models.Server
import com.connecthid.intellij.ui.filemanager.sftp.SftpPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

fun Project.openSFTP(server: Server){
    val manager = ToolWindowManager.getInstance(this)
    if (manager.getToolWindow("sftp") == null) {
        val toolWindow = manager.registerToolWindow(
            RegisterToolWindowTask(
                id = "sftp", anchor = ToolWindowAnchor.RIGHT, canCloseContent = true
            )
        )
        val sftpPanel = SftpPanel(this,server)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(sftpPanel, "", false)
        toolWindow.contentManager.addContent(content)
        toolWindow.show()
    }
}