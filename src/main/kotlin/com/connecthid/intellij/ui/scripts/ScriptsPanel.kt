package com.connecthid.intellij.ui.scripts

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.getProjectService
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Script
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDConfigurationFactory
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDRunConfiguration
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDRunConfigurationType
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTask
import com.connecthid.intellij.utils.removeI
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunDialog
import com.intellij.execution.impl.SingleConfigurationConfigurable
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.options.ex.SingleConfigurableEditor
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
import javax.swing.JScrollPane

class ScriptsPanel(
    private val project: Project
) : JPanel(), ScriptItem.Listener,RunManagerListener {

    var scripts: MutableList<Script> = emptyList<Script>().toMutableList()
        set(value) {
            field = value
            rebuildUi()
        }
    private var header: JPanel? = null
    private var headerLabel: JBLabel? = null
    private var newFolderButton: JButton? = null
    private var scriptListPanel: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
        background = JBColor.background()
    }
    private var scrollPane: JScrollPane = JScrollPane(scriptListPanel).apply {
        border = null
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        preferredSize = Dimension(0, 300) // Adjust height as needed
    }
    val runManager by lazy {
         RunManager.getInstance(project)
    }
    val service = getSSHService()

    init {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
        background = JBColor.background()
        buildHeader()
        add(scrollPane)
        rebuildUi()
        updateScriptsList()
        project.getProjectService().setRunManagerListener(this)
    }

    private fun rebuildUi() {
        headerLabel?.text = "$title (${scripts.size})"
        scriptListPanel.removeAll()
        for (script in scripts) {
            val devicePanel = ScriptItem(script)
            devicePanel.listener=this
            scriptListPanel.add(devicePanel)
        }
        scriptListPanel.revalidate()
        scriptListPanel.repaint()
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

    fun createTask(task: RunConfigurationTask){
        val type = ConfigurationTypeUtil.findConfigurationType("ConnectHID") ?:return
        val factory = type.configurationFactories.firstOrNull { (it as ConnectHIDConfigurationFactory).task == task } ?: return
        val settings: RunnerAndConfigurationSettings = runManager.createConfiguration(generateUniqueConfigurationName(task.name), factory)
        val configurable = SingleConfigurationConfigurable.editSettings<RunConfiguration>(settings, null)
        val dialog: SingleConfigurableEditor =
            object : SingleConfigurableEditor(project, configurable, null, IdeModalityType.IDE) {
                override fun getInitialSize(): Dimension {
                    return Dimension(650, 500)
                }
            }
        dialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                updateScriptsList()
            }

            override fun windowClosing(e: java.awt.event.WindowEvent?) {

            }
        })
        dialog.show()
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

    fun editTask(project: Project, runConfiguration: RunConfiguration) {
        val runManager = RunManager.getInstance(project)
        val settings: RunnerAndConfigurationSettings =
            runManager.allSettings.firstOrNull { it.configuration == runConfiguration } ?: return
        RunDialog.editConfiguration(project, settings, "Edit Script")
        val configurable = SingleConfigurationConfigurable.editSettings<RunConfiguration?>(settings, null)
        val dialog: SingleConfigurableEditor =
            object : SingleConfigurableEditor(project, configurable, null, IdeModalityType.IDE) {
                override fun getInitialSize(): Dimension {
                    return Dimension(650, 500)
                }
            }

        dialog.setTitle(title)
        dialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                updateScriptsList()
            }

            override fun windowClosing(e: java.awt.event.WindowEvent?) {

            }
        })
        dialog.showAndGet()
    }


    private fun createTasks(button: JButton) {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_remote_script") }, AllIcons.Actions.RunAnything) {
            override fun actionPerformed(e: AnActionEvent) {
                 createTask(RunConfigurationTask.RemoteScript)
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_local_script") }, AllIcons.Actions.RunAnything) {
            override fun actionPerformed(e: AnActionEvent) {
                createTask(RunConfigurationTask.LocalScript)
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_upload") }, AllIcons.Actions.Upload) {
            override fun actionPerformed(e: AnActionEvent) {
                createTask(RunConfigurationTask.Upload)
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_download") }, AllIcons.Actions.Download) {
            override fun actionPerformed(e: AnActionEvent) {
                createTask(RunConfigurationTask.Download)
            }
        })
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("WorkspacePopup", actionGroup)
        popupMenu.component.show(button, button.width/2, button.height)
    }

    fun createRunConfigurations(script: Script): RunConfiguration{
        val type = ConfigurationTypeUtil.findConfigurationType("ConnectHID") ?: throw ExecutionException("Cannot find runner for ${script.scriptName}")
        val factory = type.configurationFactories.firstOrNull { (it as ConnectHIDConfigurationFactory).task == RunConfigurationTask.fromType(script.scriptType) } ?: throw ExecutionException("Cannot find runner for ${script.scriptName}")
        val settings: RunnerAndConfigurationSettings = runManager.createConfiguration(generateUniqueConfigurationName(script.scriptName), factory)
        with(settings.configuration as ConnectHIDRunConfiguration){
            setServer(script.server)
            setScriptId(script.scriptId)
            setScriptText(script.scriptText)
            setScriptPath(script.scriptFile)
            setScriptOptions(script.scriptOptions)
            setScriptWorkingDirectory(script.workingDir)
            setExecuteInTerminal(script.executeInTerminal)
            setExecuteScriptFile(script.isScriptFile)
            setInterpreterPath(script.interpreterPath)
            setInterpreterOptions(script.interpreterOptions)
            setShowInRunConfiguration(script.executeInRunConfigurations)
        }
        runManager.addConfiguration(settings)
        return settings.configuration

    }


    private fun updateScriptsList() {
        val scripts = service.getScripts()
        scripts.forEach {
            println("ConnectHID Run Config: ${it.scriptName}")
        }
        this.scripts=scripts.toMutableList()
    }

    override fun runTask(configuration: Script) {
        val scripts = runManager.getConfigurationsList(ConfigurationTypeUtil.findConfigurationType(ConnectHIDRunConfigurationType::class.java))
        val configuration  = scripts.firstOrNull { (it as ConnectHIDRunConfiguration).getScriptId() == configuration.scriptId } ?: createRunConfigurations(configuration)
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val builder = ExecutionEnvironmentBuilder.createOrNull(project, executor, configuration)
            ?: throw ExecutionException("Cannot find runner for ${configuration.name}")
        val environment = builder.build()
        environment.runner.execute(environment)
    }

    override fun editTask(
        configuration: Script,
        dataContext: DataContext
    ) {
        val scripts = runManager.getConfigurationsList(ConfigurationTypeUtil.findConfigurationType(ConnectHIDRunConfigurationType::class.java))
        val configuration  = scripts.firstOrNull { (it as ConnectHIDRunConfiguration).getScriptId() == configuration.scriptId } ?: createRunConfigurations(configuration)
        editTask(project,configuration)
    }

    override fun onDeleteTask(configuration: Script) {
        val confirm = Messages.showYesNoDialog(
            this,
            "Delete ${configuration.scriptName} ?",
            "Confirm Delete",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return
        service.removeScript(configuration)
        val configurations = runManager.getConfigurationsList(ConfigurationTypeUtil.findConfigurationType(ConnectHIDRunConfigurationType::class.java))
        val configuration =
            configurations.firstOrNull { (it as ConnectHIDRunConfiguration).getScriptId() == configuration.scriptId }
        configuration?.let {
            val settings=
                runManager.allSettings.firstOrNull { it.configuration == configuration }
            runManager.removeConfiguration(settings)
        }
        updateScriptsList()
    }

    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
        updateScriptsList()
    }

    private companion object {
        private const val HEADER_HEIGHT = 50
        private val title = "Scripts"
        private val addButton = "Create Script"
    }

}