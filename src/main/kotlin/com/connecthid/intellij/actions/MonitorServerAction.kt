package com.connecthid.intellij.actions

import com.connecthid.intellij.services.ServerMonitoringService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class MonitorServerAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val monitoringService = ServerMonitoringService(project)
        
        // Show input dialog for host
        val host = Messages.showInputDialog(
            project,
            "Enter server host:",
            "Monitor Server",
            Messages.getQuestionIcon()
        ) ?: return

        try {
            // Start monitoring
            monitoringService.startMonitoring(host)
            
            // Show current metrics
            val metrics = monitoringService.getCurrentMetrics(host)
            val message = metrics.joinToString("\n") { 
                "${it.name}: ${it.value}${it.unit}"
            }
            
            Messages.showInfoMessage(
                project,
                "Server Metrics:\n$message",
                "Server Monitoring"
            )
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to monitor server: ${ex.message}",
                "Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
} 