package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTaskType
import com.connecthid.intellij.utils.Utils.mapStringToElement
import com.connecthid.intellij.utils.getDefaultShell
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.connecthid.intellij.ui.commons.ssh.EnvironmentVariablesComponent
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.layout.selected
import com.connecthid.intellij.PluginBundle
import com.intellij.ui.components.JBList
import com.intellij.ui.awt.RelativePoint
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Point
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.SwingConstants
import java.awt.Component
import javax.swing.JComponent

class ScriptTaskForm(
    project: Project,
    taskModel: TaskModel? = null,
    fromRunConfiguration: Boolean = false
) : BaseTaskForm(project, RunConfigurationTaskType.Script, taskModel, fromRunConfiguration) {

    private var myScript: RawCommandLineEditor? = null
    private var myScriptSelector: TextFieldWithBrowseButton? = null
    private var myScriptOptions: RawCommandLineEditor? = null
    private var myScriptFileWorkingDirectory: TextFieldWithBrowseButton? = null
    private var myInterpreterSelector: TextFieldWithBrowseButton? = null
    private var myInterpreterOptions: RawCommandLineEditor? = null
    private var myExecuteFileInTerminal: JBCheckBox? = null
    private var myEnvComponent: EnvironmentVariablesComponent? = null
    private var myScriptFileRadioButton: JBRadioButton? = null
    private var myScriptTextRadioButton: JBRadioButton? = null

    override fun createUIComponents(): DialogPanel {
        myScript = RawCommandLineEditor()
        myScriptSelector = TextFieldWithBrowseButton()
        myScriptOptions = RawCommandLineEditor()
        myScriptFileWorkingDirectory = TextFieldWithBrowseButton()
        myInterpreterSelector = TextFieldWithBrowseButton()
        myInterpreterOptions = RawCommandLineEditor()
        myExecuteFileInTerminal = JBCheckBox("Execute in Terminal")
        
        myEnvComponent = EnvironmentVariablesComponent(project) {
            val selectedItem = hostBox?.selectedItem as? String
            if (selectedItem != null && selectedItem != localhost) {
                service.getServer(selectedItem)?.let {
                    service.getConnection(it)?.let { conn ->
                        return@EnvironmentVariablesComponent conn.getEnvironmentVariables().toMutableMap()
                    }
                }
                return@EnvironmentVariablesComponent mutableMapOf()
            } else {
                return@EnvironmentVariablesComponent GeneralCommandLine().parentEnvironment.toMutableMap()
            }
        }.apply { text = "" }

        setupPickers()
        return super.createUIComponents()
    }

    private fun setupPickers() {
        myScriptSelector?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            if (selectedServer != localhost) {
                showPopup(myScriptSelector!!)
            } else {
                chooseFile(selectedServer, PluginBundle.message("sh.label.choose.shell.script"), isLocalFile = true) {
                    myScriptSelector!!.text = it
                }
            }
        }

        myScriptFileWorkingDirectory?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFolder(selectedServer, "Choose script working directory") {
                myScriptFileWorkingDirectory!!.text = it
            }
        }

        myInterpreterSelector?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFile(selectedServer, PluginBundle.message("sh.label.choose.interpreter")) {
                myInterpreterSelector!!.text = it
            }
        }
    }

    private fun showPopup(button: JComponent) {
        val list = JBList(listOf("Local File", "Remote File"))
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (comp is JLabel) {
                    comp.horizontalAlignment = SwingConstants.LEFT
                    comp.border = javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 0)
                }
                return comp
            }
        }
        JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setItemChosenCallback { selected ->
                val selectedServer = getSelectedServer() ?: return@setItemChosenCallback
                chooseFile(selectedServer, PluginBundle.message("sh.label.choose.shell.script"), isLocalFile = selected == "Local File") {
                    myScriptSelector!!.text = it
                }
            }
            .createPopup()
            .show(RelativePoint(button, Point(button.width - 100, button.height)))
    }

    override fun createSpecificUI(panel: Panel) {
        with(panel) {
            group("Execution Mode") {
                buttonsGroup {
                    row {
                        radioButton("Execute Script File").applyToComponent {
                            myScriptFileRadioButton = this
                            isSelected = true
                        }
                        radioButton("Execute Script Text").applyToComponent {
                            myScriptTextRadioButton = this
                            isSelected = false
                        }
                    }
                }
            }

            group("Script Configuration") {
                row("Script Text:") {
                    cell(myScript!!).resizableColumn().align(Align.FILL)
                }.visibleIf(myScriptTextRadioButton!!.selected)
                
                row("Script Path:") {
                    cell(myScriptSelector!!).resizableColumn().align(Align.FILL)
                }.visibleIf(myScriptFileRadioButton!!.selected)
                
                row("Script Options:") {
                    cell(myScriptOptions!!).resizableColumn().align(Align.FILL)
                }.visibleIf(myScriptFileRadioButton!!.selected)
                
                row("Working Directory:") {
                    cell(myScriptFileWorkingDirectory!!).resizableColumn().align(Align.FILL)
                }
                
                row("Environment Variables:") {
                    cell(myEnvComponent!!).resizableColumn().align(Align.FILL)
                }
            }

            group("Interpreter Settings") {
                row("Interpreter Path:") {
                    cell(myInterpreterSelector!!).resizableColumn().align(Align.FILL)
                }
                row("Interpreter Options:") {
                    cell(myInterpreterOptions!!).resizableColumn().align(Align.FILL)
                }
                row {
                    cell(myExecuteFileInTerminal!!).resizableColumn().align(Align.FILL).align(Align.CENTER)
                }
            }.visibleIf(myScriptFileRadioButton!!.selected)
        }
    }

    override fun setData() {
        task.server = hostBox?.selectedItem as? String ?: ""
        task.scriptName = taskName.get()
        task.scriptText = myScript?.text ?: ""
        task.scriptFile = myScriptSelector?.text ?: ""
        task.scriptOptions = myScriptOptions?.text ?: ""
        task.workingDir = myScriptFileWorkingDirectory?.text ?: ""
        task.executeInTerminal = myExecuteFileInTerminal?.isSelected ?: false
        task.interpreterPath = myInterpreterSelector?.text ?: ""
        task.interpreterOptions = myInterpreterOptions?.text ?: ""
        task.scriptType = taskType.ordinal
        task.isScriptFile = myScriptFileRadioButton?.isSelected ?: false
        task.envData = myEnvComponent?.envData?.envs?.entries?.joinToString(";") { "${it.key}=${it.value}" } ?: ""
        task.runAfter = runAfter?.getTaskIds() ?: ""
        task.passParentEnv = myEnvComponent?.envData?.isPassParentEnvs ?: true
        task.updatedTimeStamp = System.currentTimeMillis()
        if (task.createTimeStamp == 0L) task.createTimeStamp = task.updatedTimeStamp
        task.isLocal = task.server == localhost
    }

    override fun resetEditorFrom(configuration: TaskModel) {
        this.task = configuration
        myScriptFileRadioButton?.isSelected = configuration.isScriptFile
        myScriptTextRadioButton?.isSelected = !configuration.isScriptFile
        myScript?.text = configuration.scriptText
        myScriptSelector?.text = configuration.scriptFile
        myScriptOptions?.text = configuration.scriptOptions
        myScriptFileWorkingDirectory?.text = configuration.workingDir
        
        if (configuration.interpreterPath.isEmpty()) {
            val server = hostBox?.selectedItem as? String ?: localhost
            if (server == localhost) {
                myInterpreterSelector?.text = project.getDefaultShell()
            } else {
                service.getServer(server)?.let {
                    myInterpreterSelector?.text = it.systemInfo.defaultShell
                }
            }
        } else {
            myInterpreterSelector?.text = configuration.interpreterPath
        }

        myInterpreterOptions?.text = configuration.interpreterOptions
        myExecuteFileInTerminal?.isSelected = configuration.executeInTerminal
        if (configuration.server.isNotEmpty()) servers.selectedItem = configuration.server
        taskName.set(configuration.scriptName)
        runAfter?.setRunAfter(configuration.runAfter)
        myEnvComponent?.envData = EnvironmentVariablesData.readExternal(mapStringToElement(configuration.envData, passParentEnv = configuration.passParentEnv))
    }
}
