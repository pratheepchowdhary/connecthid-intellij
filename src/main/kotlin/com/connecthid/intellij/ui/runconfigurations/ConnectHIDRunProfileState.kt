package com.connecthid.intellij.ui.runconfigurations

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment

class ConnectHIDRunProfileState(
    private val environment: ExecutionEnvironment
) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        val cmd = GeneralCommandLine("echo", "Hello IntelliJ Run Config!")
        return KillableProcessHandler(cmd)
    }
}