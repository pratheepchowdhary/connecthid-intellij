package com.connecthid.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project

abstract class AOSPAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) = actionPerformed(event, event.getData(PlatformDataKeys.PROJECT)!!)
    abstract fun actionPerformed(e: AnActionEvent, project: Project)
}