package com.connecthid.intellij.connection.terminal

import com.intellij.execution.process.ProcessHandler
import java.io.OutputStream

abstract class CProcessHandler: ProcessHandler() {

    abstract fun executeCommand()
}