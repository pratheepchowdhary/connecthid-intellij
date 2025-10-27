package com.connecthid.intellij.ui.commons.ssh

import com.connecthid.intellij.connection.ssh.SSHConnection
import com.intellij.execution.configurations.GeneralCommandLine

class SshGeneralCommandLine(val ssh: SSHConnection): GeneralCommandLine() {
    override fun getParentEnvironment(): MutableMap<String, String> {
        return ssh.getEnvironmentVariables().toMutableMap()
    }
}