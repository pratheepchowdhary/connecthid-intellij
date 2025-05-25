package com.connecthid.intellij.ui.actions

import com.connecthid.intellij.services.ScriptService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class ManageScriptsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val scriptService = ScriptService(project)
        
        // Show input dialog for host
        val host = Messages.showInputDialog(
            project,
            "Enter server host:",
            "Manage Scripts",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for script name
        val scriptName = Messages.showInputDialog(
            project,
            "Enter script name:",
            "Manage Scripts",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for script content
        val scriptContent = Messages.showMultilineInputDialog(
            project,
            "Enter script content:",
            "Manage Scripts",
            "",
            Messages.getQuestionIcon(),
            null
        ) ?: return

        try {
            // Create or update script
            scriptService.createScript(host, scriptName, scriptContent)
            
            Messages.showInfoMessage(
                project,
                "Script '$scriptName' has been created/updated successfully",
                "Script Management"
            )
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to manage script: ${ex.message}",
                "Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
} 