package com.connecthid.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * This startup activity ensures that the ConnectHID plugin services are only initialized
 * after the IntelliJ platform is fully loaded, which helps prevent JavaLibraryModificationTracker warnings.
 */
class ConnectHIDStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // This method is called when a project is opened, after the platform is fully initialized
        // We don't need to do anything specific here - just having this class registered
        // helps ensure proper initialization order for services
    }
}
