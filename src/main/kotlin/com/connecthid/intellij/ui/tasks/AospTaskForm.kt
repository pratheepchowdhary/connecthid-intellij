package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTaskType
import com.connecthid.intellij.ui.commons.ssh.EnvironmentVariablesComponent
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueMatches
import javax.swing.JButton
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingUtilities
import com.connecthid.intellij.utils.Utils.mapStringToElement
import java.util.*

class AospTaskForm(
    project: Project,
    taskModel: TaskModel? = null,
    fromRunConfiguration: Boolean = false
) : BaseTaskForm(project, RunConfigurationTaskType.AOSP, taskModel, fromRunConfiguration) {

    private var aospRootPath: TextFieldWithBrowseButton? = null
    private var lunchTargetBox: ComboBox<String>? = null
    private var fetchLunchTargetsButton: JButton? = null
    private var buildTargetTypeModule: JBRadioButton? = null
    private var buildTargetTypePath: JBRadioButton? = null
    private var buildTargetField: TextFieldWithBrowseButton? = null
    private var buildTargetModuleBox: ComboBox<String>? = null
    private var binaryOutputPanel: FilesPickerPanel? = null
    private var buildActionBox: ComboBox<String>? = null
    private var adbDeviceIdField: JBTextField? = null
    private var myEnvComponent: EnvironmentVariablesComponent? = null
    private var localDownloadPath: TextFieldWithBrowseButton? = null

    override fun createUIComponents(): DialogPanel {
        aospRootPath = TextFieldWithBrowseButton()
        lunchTargetBox = ComboBox(arrayOf())
        lunchTargetBox?.isEditable = true
        fetchLunchTargetsButton = JButton("Fetch")
        fetchLunchTargetsButton?.addActionListener { fetchLunchTargets() }
        
        buildTargetField = TextFieldWithBrowseButton()
        buildTargetModuleBox = ComboBox(arrayOf())
        buildTargetModuleBox?.isEditable = true
        buildTargetTypeModule = JBRadioButton("Module")
        buildTargetTypePath = JBRadioButton("Path")
        binaryOutputPanel = FilesPickerPanel(project, isRemote = true)
        buildActionBox = ComboBox(arrayOf("Build Only", "Build & Download", "Build & Download & Push"))
        adbDeviceIdField = JBTextField()
        localDownloadPath = TextFieldWithBrowseButton()

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
        aospRootPath?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFolder(selectedServer, "Choose AOSP Root") {
                aospRootPath!!.text = it
                fetchLunchTargets()
            }
        }
        
        lunchTargetBox?.addActionListener { fetchModules() }

        buildTargetField?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            if (buildTargetTypePath?.isSelected == true) {
                chooseFolder(selectedServer, "Choose Build Path") {
                    buildTargetField!!.text = it
                }
            }
        }
        
        buildTargetModuleBox?.addActionListener { fetchModuleOutput() }
    }

    override fun createSpecificUI(panel: Panel) {
        with(panel) {
            group("AOSP Environment") {
                row("AOSP Root:") {
                    cell(aospRootPath!!).resizableColumn().align(Align.FILL)
                }
                row("Lunch Target:") {
                    cell(lunchTargetBox!!).resizableColumn().align(Align.FILL)
                    cell(fetchLunchTargetsButton!!)
                }
                row("Environment Variables:") {
                    cell(myEnvComponent!!).resizableColumn().align(Align.FILL)
                }
            }

            group("Build Selection") {
                row("Target Type:") {
                    panel {
                        buttonsGroup {
                            row {
                                radioButton("Module").applyToComponent { 
                                    buildTargetTypeModule = this
                                    isSelected = true 
                                    addActionListener { 
                                        buildTargetModuleBox?.isVisible = true
                                        buildTargetField?.isVisible = false
                                    }
                                }
                                radioButton("Path").applyToComponent { 
                                    buildTargetTypePath = this 
                                    addActionListener { 
                                        buildTargetModuleBox?.isVisible = false
                                        buildTargetField?.isVisible = true
                                    }
                                }
                            }
                        }
                    }
                }
                row("Target Value:") {
                    cell(buildTargetField!!).resizableColumn().align(Align.FILL)
                        .visibleIf(buildTargetTypePath!!.selected)
                    cell(buildTargetModuleBox!!).resizableColumn().align(Align.FILL)
                        .visibleIf(buildTargetTypeModule!!.selected)
                }
            }

            group("Post-Build Actions") {
                row("Binary Output Location:") {
                    cell(binaryOutputPanel!!).resizableColumn().align(Align.FILL)
                }
                row("Action:") {
                    cell(buildActionBox!!).resizableColumn().align(Align.FILL)
                }
                row("Local Download Path:") {
                    cell(localDownloadPath!!).resizableColumn().align(Align.FILL)
                }.visibleIf(buildActionBox!!.selectedValueMatches { it == "Build & Download" || it == "Build & Download & Push" })
                
                row("ADB Device ID:") {
                    cell(adbDeviceIdField!!).resizableColumn().align(Align.FILL)
                }.visibleIf(buildActionBox!!.selectedValueMatches { it == "Build & Download & Push" })
            }
        }
    }

    override fun setData() {
        task.server = hostBox?.selectedItem as? String ?: ""
        task.scriptName = taskName.get()
        task.scriptType = taskType.ordinal
        task.envData = myEnvComponent?.envData?.envs?.entries?.joinToString(";") { "${it.key}=${it.value}" } ?: ""
        task.runAfter = runAfter?.getTaskIds() ?: ""
        task.passParentEnv = myEnvComponent?.envData?.isPassParentEnvs ?: true
        
        task.aospRootPath = aospRootPath?.text ?: ""
        task.lunchTarget = lunchTargetBox?.selectedItem as? String ?: ""
        task.buildTarget = if (buildTargetTypeModule?.isSelected == true) 
            (buildTargetModuleBox?.selectedItem as? String ?: "") 
        else 
            (buildTargetField?.text ?: "")
        task.buildTargetType = if (buildTargetTypeModule?.isSelected == true) 0 else 1
        task.buildAction = buildActionBox?.selectedIndex ?: 0
        task.binaryOutputLocation = binaryOutputPanel?.getFiles() ?: ""
        task.adbDeviceId = adbDeviceIdField?.text ?: ""
        task.localFolder = localDownloadPath?.text ?: ""

        task.updatedTimeStamp = System.currentTimeMillis()
        if (task.createTimeStamp == 0L) task.createTimeStamp = task.updatedTimeStamp
        task.isLocal = task.server == localhost
    }

    override fun resetEditorFrom(configuration: TaskModel) {
        this.task = configuration
        taskName.set(configuration.scriptName)
        runAfter?.setRunAfter(configuration.runAfter)
        myEnvComponent?.envData = EnvironmentVariablesData.readExternal(mapStringToElement(configuration.envData, passParentEnv = configuration.passParentEnv))
        if (configuration.server.isNotEmpty()) servers.selectedItem = configuration.server

        aospRootPath?.text = configuration.aospRootPath
        lunchTargetBox?.selectedItem = configuration.lunchTarget
        buildTargetField?.text = configuration.buildTarget
        buildTargetModuleBox?.selectedItem = configuration.buildTarget
        if (configuration.buildTargetType == 0) buildTargetTypeModule?.isSelected = true else buildTargetTypePath?.isSelected = true
        buildActionBox?.selectedIndex = configuration.buildAction
        binaryOutputPanel?.setFiles(configuration.binaryOutputLocation)
        adbDeviceIdField?.text = configuration.adbDeviceId
        localDownloadPath?.text = configuration.localFolder
        
        binaryOutputPanel?.host = configuration.server
    }

    private fun fetchLunchTargets() {
        val selectedServer = getSelectedServer()
        val rootPath = aospRootPath?.text
        if (selectedServer == null || rootPath.isNullOrEmpty()) return
        
        val serverConfig = service.getServer(selectedServer) ?: return
        fetchLunchTargetsButton?.isEnabled = false
        
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                service.getConnection(serverConfig)?.let { conn ->
                    val cmd = "bash -c 'cd ${rootPath} && source build/envsetup.sh && print_lunch_menu'"
                    val output = conn.execute(cmd, 15)
                    val targets = mutableListOf<String>()
                    val regex = Regex("^\\s*\\d+\\.\\s+(\\S+)", RegexOption.MULTILINE)
                    regex.findAll(output).forEach { match -> targets.add(match.groupValues[1]) }
                    
                    SwingUtilities.invokeLater {
                        if (targets.isNotEmpty()) {
                            lunchTargetBox?.model = DefaultComboBoxModel(targets.toTypedArray())
                            lunchTargetBox?.selectedItem = targets[0]
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                SwingUtilities.invokeLater { fetchLunchTargetsButton?.isEnabled = true }
            }
        }
    }

    private fun fetchModules() {
        val selectedServer = getSelectedServer()
        val rootPath = aospRootPath?.text
        val selectedLunch = lunchTargetBox?.selectedItem as? String
        if (selectedServer == null || rootPath.isNullOrEmpty() || selectedLunch.isNullOrEmpty()) return
        
        val serverConfig = service.getServer(selectedServer) ?: return
        
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
             try {
                service.getConnection(serverConfig)?.let { conn ->
                    val cmd = "bash -c 'cd ${rootPath} && source build/envsetup.sh && lunch ${selectedLunch} && allmod'"
                    val output = conn.execute(cmd, 60)
                    val modules = mutableListOf<String>()
                    output.lines().forEach { line ->
                         val t = line.trim()
                         if (t.isNotEmpty() && !t.contains(" ") && !t.contains("/") && !t.contains("\\") && !t.startsWith("[") && !t.startsWith("make") && !t.startsWith("INFO:") && !t.startsWith("WARNING:") && !t.contains("=")) {
                             modules.add(t)
                         }
                    }
                    SwingUtilities.invokeLater {
                        if (modules.isNotEmpty()) {
                            buildTargetModuleBox?.model = DefaultComboBoxModel(modules.toTypedArray())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchModuleOutput() {
        if (buildTargetTypeModule?.isSelected == false) return

        val selectedServer = getSelectedServer()
        val rootPath = aospRootPath?.text
        val selectedLunch = lunchTargetBox?.selectedItem as? String
        val selectedModule = buildTargetModuleBox?.selectedItem as? String
        if (selectedServer == null || rootPath.isNullOrEmpty() || selectedLunch.isNullOrEmpty() || selectedModule.isNullOrEmpty()) return
        
        val serverConfig = service.getServer(selectedServer) ?: return
        binaryOutputPanel?.host = selectedServer
        
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
             try {
                service.getConnection(serverConfig)?.let { conn ->
                    val cmd = "bash -c 'cd ${rootPath} && source build/envsetup.sh && lunch ${selectedLunch} && outmod ${selectedModule}'"
                    val output = conn.execute(cmd, 30)
                    val files = mutableListOf<String>()
                     output.lines().forEach { line ->
                         val t = line.trim()
                         if (t.isNotEmpty() && !t.startsWith("INFO:") && !t.startsWith("WARNING:") && !t.startsWith("ERROR:") && !t.startsWith("[") && !t.startsWith("=") && !t.startsWith("PRODUCT_") && !t.startsWith("TARGET_") && !t.contains("============") && !t.contains("including") && !t.contains("envsetup") && !t.contains("lunch") && (t.startsWith("/") || t.startsWith("out/"))) {
                             if (t.startsWith("/")) files.add(t) else files.add("${rootPath}/${t}")
                         }
                    }
                    SwingUtilities.invokeLater {
                        if (files.isNotEmpty()) {
                            val sftpUrls = files.map { path -> "sftp://${serverConfig.username}@${serverConfig.host}:${serverConfig.port}${path}" }
                            binaryOutputPanel?.setFiles(sftpUrls.joinToString(";"))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
