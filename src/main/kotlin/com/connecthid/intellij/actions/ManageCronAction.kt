package com.connecthid.intellij.actions

import com.connecthid.intellij.services.CronService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class ManageCronAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cronService = CronService(project)
        
        // Show input dialog for host
        val host = Messages.showInputDialog(
            project,
            "Enter server host:",
            "Manage Cron Jobs",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for schedule
        val schedule = Messages.showInputDialog(
            project,
            "Enter cron schedule (e.g., '0 * * * *'):",
            "Manage Cron Jobs",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for command
        val command = Messages.showInputDialog(
            project,
            "Enter command to execute:",
            "Manage Cron Jobs",
            Messages.getQuestionIcon()
        ) ?: return

        try {
            // Create or update cron job
            cronService.createCronJob(host, schedule, command)
            
            Messages.showInfoMessage(
                project,
                "Cron job has been created/updated successfully",
                "Cron Job Management"
            )
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to manage cron job: ${ex.message}",
                "Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
} 