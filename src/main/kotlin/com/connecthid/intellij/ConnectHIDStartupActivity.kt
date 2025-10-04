package com.connecthid.intellij

import com.connecthid.intellij.ui.runconfigurations.ConnectHIDRunConfiguration
import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ConnectHIDStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val runManager = RunManager.getInstance(project)
        println(project.getProjectService())
        val configs = runManager.allConfigurationsList.filterIsInstance<ConnectHIDRunConfiguration>()

        println("🟢 Project started — Found ${configs.size} ConnectHID configs")
        configs.forEach {
            println(" - ${it.name}")
        }
    }
}
