package com.connecthid.intellij.ui.scripts

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDConfigurationFactory
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDRunConfigurationType
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTask
import com.connecthid.intellij.utils.removeI
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.impl.ProjectRunConfigurationConfigurable
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class ScriptsPanel(
    private val project: Project
) : JPanel(), ScriptItem.Listener {

    var scripts: MutableList<RunConfiguration> = emptyList<RunConfiguration>().toMutableList()
        set(value) {
            field = value
            rebuildUi()
        }
    private var header: JPanel? = null
    private var headerLabel: JBLabel? = null
    private var newFolderButton: JButton? = null
    val runManager by lazy {
         RunManager.getInstance(project)
    }

    init {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
        background = JBColor.background()
        buildHeader()
        rebuildUi()
        updateScriptsList()
        val tes : RunManagerImpl
    }

    private fun rebuildUi() {
        headerLabel?.text = "$title (${scripts.size})"
        removeI { child -> child is ScriptItem }
        for (script in scripts) {
            val devicePanel = ScriptItem(script)
            devicePanel.listener=this
            add(devicePanel)
        }
        revalidate()
        repaint()
    }
    private fun buildHeader() {
        val header = OpaquePanel(GridBagLayout())
        this.header = header

        headerLabel = JBLabel()
        header.add(
            headerLabel!!,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsLeft(10)
            }
        )

        newFolderButton = JButton(addButton).apply {
            icon = AllIcons.General.Add
        }
        newFolderButton!!.addActionListener {
            createTasks(newFolderButton!!)
        }


        header.add(
            newFolderButton!!,
            GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                insets = JBUI.insetsRight(10)
            }
        )

        header.minimumSize = Dimension(0, HEADER_HEIGHT)
        header.maximumSize = Dimension(Int.MAX_VALUE, HEADER_HEIGHT)
        header.preferredSize = Dimension(0, HEADER_HEIGHT)
        add(header)

    }

    fun createTask(task: RunConfigurationTask,dataContext: DataContext){
        val type = ConfigurationTypeUtil.findConfigurationType("ConnectHID") ?:return
        val factory = type.configurationFactories.firstOrNull { (it as ConnectHIDConfigurationFactory).task == task } ?: return
        val settings: RunnerAndConfigurationSettings = runManager.createConfiguration(generateUniqueConfigurationName(task.name), factory)
        runManager.addConfiguration(settings)
        val configurable = object : ProjectRunConfigurationConfigurable(project) {
            override fun getInitialSelectedConfiguration(): RunnerAndConfigurationSettings {
                return settings
            }
        }
        val dialog = EditConfigurationsDialog(project, configurable, dataContext)

        dialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                updateScriptsList()
            }

            override fun windowClosing(e: java.awt.event.WindowEvent?) {

            }
        })
        dialog.show()
       if(!dialog.isOK){
           runManager.removeConfiguration(settings)
       }
    }

    fun generateUniqueConfigurationName(baseName: String): String {
        var name = baseName
        var index = 1

        while (runManager.allSettings.any { it.name == name }) {
            name = "$baseName ($index)"
            index++
        }

        return name
    }

    fun editTask(project: Project, runConfiguration: RunConfiguration, dataContext: DataContext) {
        val runManager = RunManager.getInstance(project)
        val settings: RunnerAndConfigurationSettings =
            runManager.allSettings.firstOrNull { it.configuration == runConfiguration } ?: return
        val dialog = EditConfigurationsDialog(project,object : ProjectRunConfigurationConfigurable(project) {
            override fun getInitialSelectedConfiguration(): RunnerAndConfigurationSettings {
                return settings
            }
        },dataContext)
        dialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                updateScriptsList()
            }
            override fun windowClosing(e: java.awt.event.WindowEvent?) {

            }
        })
        dialog.show()
    }

    private fun createTasks(button: JButton) {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_remote_script") }, AllIcons.Actions.RunAnything) {
            override fun actionPerformed(e: AnActionEvent) {
                 createTask(RunConfigurationTask.RemoteScript,e.dataContext)
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_local_script") }, AllIcons.Actions.RunAnything) {
            override fun actionPerformed(e: AnActionEvent) {
                createTask(RunConfigurationTask.LocalScript,e.dataContext)
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_upload") }, AllIcons.Actions.Upload) {
            override fun actionPerformed(e: AnActionEvent) {
                createTask(RunConfigurationTask.Upload,e.dataContext)
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_download") }, AllIcons.Actions.Download) {
            override fun actionPerformed(e: AnActionEvent) {
                createTask(RunConfigurationTask.Download,e.dataContext)
            }
        })
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("WorkspacePopup", actionGroup)
        popupMenu.component.show(button, button.width/2, button.height)
    }


    private fun updateScriptsList() {
        val scripts = runManager.getConfigurationsList(ConfigurationTypeUtil.findConfigurationType(ConnectHIDRunConfigurationType::class.java))
        scripts.forEach {
            println("ConnectHID Run Config: ${it.name}")
        }
        this.scripts=scripts.toMutableList()


    }

    override fun runTask(configuration: RunConfiguration) {

    }

    override fun editTask(
        configuration: RunConfiguration,
        dataContext: DataContext
    ) {
        editTask(project,configuration,dataContext)
    }

    override fun onDeleteTask(configuration: RunConfiguration) {

        val confirm = Messages.showYesNoDialog(
            this,
            "Delete ${configuration.name} ?",
            "Confirm Delete",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        val settings: RunnerAndConfigurationSettings =
            runManager.allSettings.firstOrNull { it.configuration == configuration } ?: return
        runManager.removeConfiguration(settings)
        updateScriptsList()
    }

    private companion object {
        private const val HEADER_HEIGHT = 50
        private val title = "Scripts"
        private val addButton = "Create Script"
    }

}