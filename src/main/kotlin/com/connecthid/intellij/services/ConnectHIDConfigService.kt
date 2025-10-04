package com.connecthid.intellij.services

import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Script
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDRunConfiguration
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ConnectHIDConfigService(project: Project):  RunManagerListener{
    private var runManagerListener: RunManagerListener? = null
    private var service = getSSHService()

    init {
        project.messageBus.connect().subscribe(RunManagerListener.TOPIC, this)
    }

    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        super.runConfigurationAdded(settings)
        if(settings.configuration is ConnectHIDRunConfiguration){
            println(settings.configuration.name)
            with(settings.configuration as ConnectHIDRunConfiguration){
                val script = Script(this.getScriptId(),name,task.type,getServer(),getScriptWorkingDirectory(),getScriptPath(),getScriptText(),isExecuteScriptFile(),getScriptOptions(),getInterpreterPath(),getInterpreterOptions(),isExecuteInTerminal())
                service.addScript(script)
            }
            runManagerListener?.runConfigurationChanged(settings)
        }
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        super.runConfigurationRemoved(settings)
        if(settings.configuration is ConnectHIDRunConfiguration){
            println(settings.configuration.name)
            with(settings.configuration as ConnectHIDRunConfiguration){
                service.removeScript(getScriptId())
            }
            runManagerListener?.runConfigurationChanged(settings)
        }
    }

    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
        super.runConfigurationChanged(settings)
        if(settings.configuration is ConnectHIDRunConfiguration){
            println(settings.configuration.name)
            with(settings.configuration as ConnectHIDRunConfiguration){
                val script = Script(this.getScriptId(),name,task.type,getServer(),getScriptWorkingDirectory(),getScriptPath(),getScriptText(),isExecuteScriptFile(),getScriptOptions(),getInterpreterPath(),getInterpreterOptions(),isExecuteInTerminal())
                service.addScript(script)
            }
            runManagerListener?.runConfigurationChanged(settings)
        }
    }

    fun setRunManagerListener(runManagerListener:RunManagerListener){
        this.runManagerListener = runManagerListener
    }

}

