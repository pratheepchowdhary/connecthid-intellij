package com.connecthid.intellij.ui.filemanager.sftp.search.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.DumbAwareToggleAction

/**
 * Toggle action to switch between searching by file name and searching within file contents.
 */
class TextSearchAction(
    private val findInFilesProperty: AtomicBooleanProperty,
    private val onChanged: Runnable
) : DumbAwareToggleAction(
    "Find in Files",
    "Toggle between searching by file name and searching within file contents",
    AllIcons.Actions.ListFiles
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = findInFilesProperty.get()

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        findInFilesProperty.set(state)
        onChanged.run()
    }
}