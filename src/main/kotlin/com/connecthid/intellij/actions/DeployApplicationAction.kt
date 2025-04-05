package com.connecthid.intellij.actions

import com.connecthid.intellij.services.DeploymentService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory

class DeployApplicationAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val deploymentService = DeploymentService(project)
        
        // Show input dialog for host
        val host = Messages.showInputDialog(
            project,
            "Enter server host:",
            "Deploy Application",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for application name
        val appName = Messages.showInputDialog(
            project,
            "Enter application name:",
            "Deploy Application",
            Messages.getQuestionIcon()
        ) ?: return

        // Show input dialog for version
        val version = Messages.showInputDialog(
            project,
            "Enter version:",
            "Deploy Application",
            Messages.getQuestionIcon()
        ) ?: return

        // Show file chooser for artifact
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
        val files = chooser.choose(project)
        if (files.isEmpty()) return
        val artifactPath = files[0].path

        try {
            // Deploy application
            deploymentService.deploy(host, appName, version, mapOf("artifact_path" to artifactPath))
            
            Messages.showInfoMessage(
                project,
                "Application '$appName' has been deployed successfully",
                "Application Deployment"
            )
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to deploy application: ${ex.message}",
                "Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
} 