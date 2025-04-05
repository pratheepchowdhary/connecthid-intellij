package com.connecthid.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class DashboardWindowFactory: ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val content = ContentFactory.getInstance().createContent(ToolsGridPanel(), "", false)
        toolWindow.contentManager.addContent(content)
    }

}