package com.connecthid.intellij.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class ManageDockerAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("ASOP Tools")
        toolWindow?.show {
            val contentManager = toolWindow.contentManager
            val content = contentManager.contents.find { it.displayName == "Docker" }
            content?.let { contentManager.setSelectedContent(it) }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
} 