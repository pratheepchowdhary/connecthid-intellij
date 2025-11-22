package com.connecthid.intellij.ui.commons.ssh

import com.connecthid.intellij.connection.ssh.SSHJConnection
import com.intellij.execution.configurations.GeneralCommandLine

class SshGeneralCommandLine(val ssh: SSHJConnection): GeneralCommandLine() {
    override fun getParentEnvironment(): MutableMap<String, String> {
        return ssh.getEnvironmentVariables().toMutableMap()
    }
}