package com.connecthid.intellij.ui.filemanager.sftp.search.actions

import com.connecthid.intellij.models.Server
import com.intellij.openapi.project.Project
import com.intellij.util.Processor

class SelectServerAction(val onChanged: () -> Unit,project: Project) : ServerChooserAction(project) {

    override fun onServerSelected(o: Server) {
        sshService.searchServers.add(o)
        onChanged()
    }

    override val selectedSever: Server
        get() = sshService.getSavedConnections().get(0)

    override fun onProjectServerToggled() {

    }

    override fun isEverywhere(): Boolean {
        return true
    }

    override fun setEverywhere(p0: Boolean) {

    }

    override fun canToggleEverywhere(): Boolean {
        return true
    }

}