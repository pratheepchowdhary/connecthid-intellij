package com.connecthid.intellij

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity


/**
 * This startup activity ensures that the ConnectHID plugin services are only initialized
 * after the IntelliJ platform is fully loaded, which helps prevent JavaLibraryModificationTracker warnings.
 */
class ConnectHIDStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val restoredFiles = FileEditorManager.getInstance(project).getOpenFiles()
        restoredFiles.forEach {
            // Check if the file is of type ConnectHidUrlFileType
            if (it.fileType == com.connecthid.intellij.handler.ConnectHidUrlFileType.INSTANCE) {
                // Open the file in the editor
                FileEditorManager.getInstance(project).openFile(it, true)
            }
        }

    }
}
