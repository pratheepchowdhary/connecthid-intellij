package com.connecthid.intellij.services

import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDRunConfiguration
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

@Service(Service.Level.PROJECT)
class ConnectHIDConfigService(project: Project):  RunManagerListener,FileEditorManagerListener{
    private var runManagerListener: RunManagerListener? = null
    private val service = getSSHService()

    private val sftpFileSystem by lazy {
        VirtualFileManager.getInstance().getFileSystem(SftpFileSystem.PROTOCOL) as SftpFileSystem
    }


    init {
        project.messageBus.connect().apply {
            subscribe(RunManagerListener.TOPIC, this@ConnectHIDConfigService)
            subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,this@ConnectHIDConfigService)
        }
    }

    override fun fileOpened(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
        val sftpFile = file as? SftpFile ?: return
        sftpFileSystem.fileCache.put(sftpFile.url,sftpFile)
        println("Opened remote file: ${sftpFile.pathLocation}")


    }

    override fun fileClosed(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
        val sftpFile = file as? SftpFile ?: return
        println("Closed remote file: ${sftpFile.pathLocation}")

        // Release the associated channel
    }

    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        super.runConfigurationAdded(settings)
        if(settings.configuration is ConnectHIDRunConfiguration){
           with(settings.configuration as ConnectHIDRunConfiguration){
               taskModel.executeInRunConfigurations = true
               service.addScript(taskModel = taskModel)
               runManagerListener?.runConfigurationChanged(settings)
           }
        }
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        super.runConfigurationRemoved(settings)
        if(settings.configuration is ConnectHIDRunConfiguration){
            with(settings.configuration as ConnectHIDRunConfiguration){
                taskModel.executeInRunConfigurations = false
                service.addScript(taskModel = taskModel)
                runManagerListener?.runConfigurationChanged(settings)
            }
            runManagerListener?.runConfigurationChanged(settings)
        }
    }

    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
        super.runConfigurationChanged(settings)
        if(settings.configuration is ConnectHIDRunConfiguration){
            with(settings.configuration as ConnectHIDRunConfiguration){
                taskModel.executeInRunConfigurations = true
                service.addScript(taskModel = taskModel)
                runManagerListener?.runConfigurationChanged(settings)
            }
        }
    }

    fun setRunManagerListener(runManagerListener:RunManagerListener){
        this.runManagerListener = runManagerListener
    }

}

