package com.connecthid.intellij.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import java.nio.file.Files
import java.nio.file.Path


fun Project.getDefaultShell(): String {
    val shellPath  = TerminalProjectOptionsProvider.getInstance(this).shellPath
    return shellPath
    //return findDefaultShellPath()
}


private fun findDefaultShellPath(): String? {
    if (SystemInfo.isWindows) {
        return "powershell.exe"
    }
    val shell = System.getenv("SHELL")
    val shellPath: Path? = if (shell != null) NioFiles.toPath(shell) else null
    if (shellPath != null && Files.exists(shellPath)) {
        return shell
    }
    val bashPath: Path? = NioFiles.toPath("/bin/bash")
    if (bashPath != null && Files.exists(bashPath)) {
        return bashPath.toString()
    }
    return "/bin/sh"
}
