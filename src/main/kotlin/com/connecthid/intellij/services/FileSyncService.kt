package com.connecthid.intellij.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jcraft.jsch.ChannelExec
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class FileSyncService(private val project: Project) {
    private val syncStatus = ConcurrentHashMap<String, SyncStatus>()
    private val connectionService = ServerConnectionService(project)

    data class SyncStatus(
        val isSyncing: Boolean = false,
        val lastSyncTime: Long = 0,
        val error: String? = null
    )

    fun syncToRemote(host: String, localPath: String, remotePath: String, excludePatterns: List<String> = emptyList()) {
        if (!connectionService.isConnected(host)) {
            throw IllegalStateException("Not connected to host: $host")
        }

        val session = connectionService.getSession(host)
        val channel = session!!.openChannel("exec") as ChannelExec
        val rsyncCommand = buildRsyncCommand(localPath, remotePath, excludePatterns)
        channel.setCommand(rsyncCommand)

        try {
            syncStatus[host] = SyncStatus(isSyncing = true)
            channel.connect()
            
            val inputStream = channel.inputStream
            val errorStream = channel.errStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val errorReader = BufferedReader(InputStreamReader(errorStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Process output if needed
            }

            val error = errorReader.readText()
            if (error.isNotEmpty()) {
                throw RuntimeException("Rsync error: $error")
            }

            syncStatus[host] = SyncStatus(isSyncing = false, lastSyncTime = System.currentTimeMillis())
        } finally {
            channel.disconnect()
        }
    }

    fun syncFromRemote(host: String, remotePath: String, localPath: String, excludePatterns: List<String> = emptyList()) {
        if (!connectionService.isConnected(host)) {
            throw IllegalStateException("Not connected to host: $host")
        }

        val session = connectionService.getSession(host)
        val channel = session!!.openChannel("exec") as ChannelExec
        val rsyncCommand = buildRsyncCommand(remotePath, localPath, excludePatterns)
        channel.setCommand(rsyncCommand)

        try {
            syncStatus[host] = SyncStatus(isSyncing = true)
            channel.connect()
            
            val inputStream = channel.inputStream
            val errorStream = channel.errStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val errorReader = BufferedReader(InputStreamReader(errorStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Process output if needed
            }

            val error = errorReader.readText()
            if (error.isNotEmpty()) {
                throw RuntimeException("Rsync error: $error")
            }

            syncStatus[host] = SyncStatus(isSyncing = false, lastSyncTime = System.currentTimeMillis())
        } finally {
            channel.disconnect()
        }
    }

    private fun buildRsyncCommand(source: String, destination: String, excludePatterns: List<String>): String {
        val excludes = excludePatterns.joinToString(" ") { "--exclude='$it'" }
        return "rsync -avz --delete $excludes $source $destination"
    }

    fun getSyncStatus(host: String): SyncStatus {
        return syncStatus[host] ?: SyncStatus()
    }

    fun cancelSync(host: String) {
        // Implement cancellation logic if needed
        syncStatus[host] = SyncStatus(isSyncing = false)
    }
} 