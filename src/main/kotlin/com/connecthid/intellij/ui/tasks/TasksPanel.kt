package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.getProjectService
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.MyIcons
import com.connecthid.intellij.ui.commons.TabSelectedListener
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDConfigurationFactory
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDRunConfiguration
import com.connecthid.intellij.ui.runconfigurations.ConnectHIDRunConfigurationType
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTaskType
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

class TasksPanel(
    private val project: Project
) : JPanel(), TaskItem.Listener,RunManagerListener , TabSelectedListener {

    var taskModels: MutableList<TaskModel> = emptyList<TaskModel>().toMutableList()
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
        headerLabel?.text = "$title (${taskModels.size})"
        scriptListPanel.removeAll()
        for (script in taskModels) {
            val devicePanel = TaskItem(script,project)
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



    fun createTask(task: RunConfigurationTaskType){
        val taskDialog = TaskDialog(project, task)
        taskDialog.window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                updateScriptsList()
            }

            override fun windowClosing(e: java.awt.event.WindowEvent?) {

            }
        })
        taskDialog.show()
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
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_script") }, AllIcons.Actions.RunAnything) {
            override fun actionPerformed(e: AnActionEvent) {
                createTask(RunConfigurationTaskType.Script)
            }
        })
        actionGroup.addSeparator()
        actionGroup.add(object : AnAction({ PluginBundle.message("task_sftp_file_transfer") }, MyIcons.FileTransfer) {
            override fun actionPerformed(e: AnActionEvent) {
                createTask(RunConfigurationTaskType.ScpFileTransfer)
            }
        })
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("WorkspacePopup", actionGroup)
        popupMenu.component.show(button, button.width/2, button.height)
    }

    fun createRunConfigurations(taskModel: TaskModel,addToConfiguration: Boolean = false): RunConfiguration{
        val type = ConfigurationTypeUtil.findConfigurationType("ConnectHID") ?: throw ExecutionException("Cannot find runner for ${taskModel.scriptName}")
        val factory = type.configurationFactories.firstOrNull { (it as ConnectHIDConfigurationFactory).task == RunConfigurationTaskType.fromType(taskModel.scriptType) } ?: throw ExecutionException("Cannot find runner for ${taskModel.scriptName}")
        val settings: RunnerAndConfigurationSettings = runManager.createConfiguration(generateUniqueConfigurationName(taskModel.scriptName), factory)
        with(settings.configuration as ConnectHIDRunConfiguration){
            setTask(taskModel)
        }
        if (addToConfiguration) {
            runManager.addConfiguration(settings)
        }
        return settings.configuration

    }

    private fun updateScriptsList() {
        val scripts = service.getTasks()
        scripts.forEach {
            println("ConnectHID Run Config: ${it.scriptName}")
        }
        this.taskModels=scripts.toMutableList()
    }

    override fun runTask(taskModel: TaskModel) {
        val scripts =
            runManager.getConfigurationsList(ConfigurationTypeUtil.findConfigurationType(ConnectHIDRunConfigurationType::class.java))
        val configuration =
            scripts.firstOrNull { (it as ConnectHIDRunConfiguration).getTask().scriptId == taskModel.scriptId }
                ?: createRunConfigurations(taskModel)
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val builder = ExecutionEnvironmentBuilder.createOrNull(project, executor, configuration)
            ?: throw ExecutionException("Cannot find runner for ${configuration.name}")
        val environment = builder.build()
        environment.runner.execute(environment)
    }

    override fun editTask(
        taskModel: TaskModel,
        dataContext: DataContext
    ) {
        val task = TaskDialog(project, RunConfigurationTaskType.fromType(taskModel.scriptType), taskModel)
        task.window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                updateScriptsList()
            }

            override fun windowClosing(e: java.awt.event.WindowEvent?) {

            }
        })
        task.show()
    }

    override fun onDeleteTask(taskModel: TaskModel) {
        val confirm = Messages.showYesNoDialog(
            this,
            "Delete ${taskModel.scriptName} ?",
            "Confirm Delete",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return
        val configurations =
            runManager.getConfigurationsList(ConfigurationTypeUtil.findConfigurationType(ConnectHIDRunConfigurationType::class.java))
        val configuration =
            configurations.firstOrNull { (it as ConnectHIDRunConfiguration).getTask().scriptId == taskModel.scriptId }
        configuration?.let {
            val settings =
                runManager.allSettings.firstOrNull { it.configuration == configuration }
            runManager.removeConfiguration(settings)
        }
        service.removeScript(taskModel)
        updateScriptsList()
    }

    override fun addToRunConfiguration(taskModel: TaskModel) {
        val confirm = Messages.showYesNoDialog(
            this,
            "Add ${taskModel.scriptName} to run configuration?",
            "Confirm Add",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        createRunConfigurations(taskModel,true)
        updateScriptsList()
    }

    override fun removeRunConfiguration(taskModel: TaskModel) {
        val confirm = Messages.showYesNoDialog(
            this,
            "Delete ${taskModel.scriptName} from run configuration?",
            "Confirm Delete",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return
        val configurations =
            runManager.getConfigurationsList(ConfigurationTypeUtil.findConfigurationType(ConnectHIDRunConfigurationType::class.java))
        val configuration =
            configurations.firstOrNull { (it as ConnectHIDRunConfiguration).getTask().scriptId == taskModel.scriptId }
        configuration?.let {
            val settings =
                runManager.allSettings.firstOrNull { it.configuration == configuration }
            runManager.removeConfiguration(settings)
        }
        updateScriptsList()
    }

    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
        updateScriptsList()
    }

    override fun onTabForeground() {
       updateScriptsList()
    }

    override fun onTabBackground() {

    }

    private companion object {
        private const val HEADER_HEIGHT = 50
        private val title = "Tasks"
        private val addButton = "Create Task"
    }

}