package com.connecthid.intellij.ui.actions

import com.connecthid.intellij.services.DatabaseService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class ManageDatabaseAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val databaseService = DatabaseService(project)
        
        // Show input dialog for host
        val host = Messages.showInputDialog(
            project,
            "Enter server host:",
            "Manage Database",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for database type
        val dbTypes = arrayOf("MySQL", "PostgreSQL", "MongoDB")
        val dbType = Messages.showEditableChooseDialog(
            "Select database type:",
            "Manage Database",
            Messages.getQuestionIcon(),
            dbTypes,
            dbTypes[0],
            null
        ) ?: return

        // Show input dialog for database name
        val dbName = Messages.showInputDialog(
            project,
            "Enter database name:",
            "Manage Database",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for username
        val username = Messages.showInputDialog(
            project,
            "Enter username:",
            "Manage Database",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for password
        val password = Messages.showPasswordDialog(
            project,
            "Enter password:",
            "Manage Database",
            Messages.getQuestionIcon()
        ) ?: return

        try {
            // Create database
            databaseService.createDatabase(host, dbType, dbName, username, password)
            
            Messages.showInfoMessage(
                project,
                "Database '$dbName' has been created successfully",
                "Database Management"
            )
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to manage database: ${ex.message}",
                "Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
} 