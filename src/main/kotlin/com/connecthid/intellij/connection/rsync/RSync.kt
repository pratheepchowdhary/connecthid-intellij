package com.connecthid.intellij.connection.rsync

import java.io.BufferedReader
import java.io.InputStreamReader

class RSync {
    private val rsyncPath: String = "rsync"
    val baseExcludes = listOf(
        "--exclude=.git",
        "--exclude=*/.git",
        "--exclude=build",
        "--exclude=*/build"
    )

    interface RsyncCallback {
        fun onOutput(line: String)
        fun onError(line: String)
        fun onComplete(success: Boolean)
    }

    /**
     * Base rsync execution.
     */
    fun sync(
        source: String,
        destination: String,
        sshPrivateKeyPath: String? = null,
        additionalOptions: List<String> = listOf("-avz"),
        dryRun: Boolean = false,
        callback: RsyncCallback? = null
    ): Boolean {
        val command = mutableListOf(rsyncPath)
        command.addAll(baseExcludes)
        command.addAll(additionalOptions)
        if (dryRun) {
            command.add("--dry-run")
        }

        if (!sshPrivateKeyPath.isNullOrBlank()) {
            command.add("-e")
            command.add("ssh -i $sshPrivateKeyPath -o StrictHostKeyChecking=no")
        }

        command.add(source)
        command.add(destination)

        println("Executing: ${command.joinToString(" ")}")

        val processBuilder = ProcessBuilder(command)
        val process = processBuilder.start()

        val stdout = Thread {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { callback?.onOutput(it) ?: println("[rsync] $it") }
            }
        }

        val stderr = Thread {
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                reader.forEachLine { callback?.onError(it) ?: System.err.println("[rsync:err] $it") }
            }
        }

        stdout.start()
        stderr.start()

        val exitCode = process.waitFor()
        stdout.join()
        stderr.join()

        callback?.onComplete(exitCode == 0)
        return exitCode == 0
    }

    /**
     * Upload from local to remote.
     */
    fun syncLocalToRemote(
        localPath: String,
        remoteUserHost: String,
        remotePath: String,
        sshPrivateKeyPath: String? = null,
        additionalOptions: List<String> = listOf("-avz"),
        dryRun: Boolean = false,
        callback: RsyncCallback? = null
    ): Boolean {
        val remote = "$remoteUserHost:$remotePath"
        return sync(localPath, remote, sshPrivateKeyPath, additionalOptions, dryRun, callback)
    }

    /**
     * Download from remote to local.
     */
    fun syncRemoteToLocal(
        remoteUserHost: String,
        remotePath: String,
        localPath: String,
        sshPrivateKeyPath: String? = null,
        additionalOptions: List<String> = listOf("-avz"),
        dryRun: Boolean = false,
        callback: RsyncCallback? = null
    ): Boolean {
        val remote = "$remoteUserHost:$remotePath"
        return sync(remote, localPath, sshPrivateKeyPath, additionalOptions, dryRun, callback)
    }

}