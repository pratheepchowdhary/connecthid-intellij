package com.connecthid.intellij.ui.filemanager.sftp.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.JOptionPane

class SearchAction : AnAction("Search", "Search something", AllIcons.Actions.Search) {
    override fun actionPerformed(e: AnActionEvent) {
        JOptionPane.showMessageDialog(null, "Search clicked!")
    }
}
