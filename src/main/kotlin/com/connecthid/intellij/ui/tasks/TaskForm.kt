package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.commons.ssh.EnvironmentVariablesComponent
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTaskType
import com.connecthid.intellij.utils.Utils.mapStringToElement
import com.connecthid.intellij.utils.getDefaultShell
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueMatches
import java.awt.Component
import java.awt.Point
import java.util.*
import javax.swing.*
import com.intellij.ui.components.JBTextField
import javax.swing.DefaultComboBoxModel

class TaskForm(private val project: Project, private val taskType: RunConfigurationTaskType, taskModel: TaskModel?=null, val fromRunConfiguration: Boolean=false) {
    val propertyGraph = PropertyGraph()
    private var myScriptPathPanel: JPanel? = null
    private var myScriptTextPanel: JPanel? = null
    private var myUploadsJPanel: JPanel? = null
    private var myDownloadsJPanel: JPanel? = null
    private var myScriptTypeJPanel: JPanel? = null
    private var myScript: RawCommandLineEditor? = null
    private var myScriptSelector: TextFieldWithBrowseButton? = null
    private var myScriptOptions: RawCommandLineEditor? = null
    private var myScriptFileWorkingDirectory: TextFieldWithBrowseButton? = null
    private var myInterpreterSelector: TextFieldWithBrowseButton? = null
    private var remoteUploadPath: TextFieldWithBrowseButton? = null
    private var localDownloadPath: TextFieldWithBrowseButton? = null
    private var myInterpreterOptions: RawCommandLineEditor? = null
    private var myExecuteFileInTerminal: JBCheckBox? = null
    private var myEnvComponent: EnvironmentVariablesComponent? = null
    private var myScriptFileRadioButton: JBRadioButton? = null
    private var myScriptTextRadioButton: JBRadioButton? = null
    private var hostJPanel: JPanel? = null
    private var hostBox: ComboBox<String>? = null
    private val localhost = "localhost"
    private val servers by lazy { DefaultComboBoxModel(getServersList().toTypedArray()) }
    private val service = getSSHService()
    private val virtualFileSystem =
        VirtualFileManager.getInstance().getFileSystem(SftpFileSystem.PROTOCOL) as SftpFileSystem

    // AOSP Components
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

    private var runAfter: RunAfterTasksPanel?=null
    private var downloadFiles: FilesPickerPanel?=null
    private var uploadFiles: FilesPickerPanel?=null

    private var task = taskModel ?: TaskModel(scriptId = UUID.randomUUID().toString())
    var taskName = propertyGraph.property("")
    var notLocalHost:ComponentPredicate ?=null


    private fun setupPickers() {
        myScriptSelector?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            if(notLocalHost!!.invoke()){
                showPopup(myScriptSelector!!)
            } else{
                chooseFile(selectedServer, PluginBundle.message("sh.label.choose.shell.script"),isLocalFile = true) {
                    myScriptSelector!!.text = it
                }
            }
        }
        remoteUploadPath?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFolder(selectedServer, "Choose upload folder") {
                remoteUploadPath!!.text = it
            }
        }

        localDownloadPath?.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Choose local download folder")
        )
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
    
    private fun setupAospPickers() {
        aospRootPath?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFolder(selectedServer, "Choose AOSP Root") {
                aospRootPath!!.text = it
                fetchLunchTargets() // Auto-fetch on root selection
            }
        }
        
        lunchTargetBox?.addActionListener {
             fetchModules() // Auto-fetch modules when lunch target changes
        }

        buildTargetField?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            // If path mode is selected, show folder picker
            if (buildTargetTypePath?.isSelected == true) {
                chooseFolder(selectedServer, "Choose Build Path") {
                    buildTargetField!!.text = it
                }
            }
        }
        
        buildTargetModuleBox?.addActionListener {
             fetchModuleOutput()
        }
    }

    fun showPopup(button: JComponent) {
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
                    comp.border = javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 0) // 10px left padding
                }
                return comp
            }
        }
        val popupMenu = JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setItemChosenCallback { selected ->
                val selectedServer = getSelectedServer() ?: return@setItemChosenCallback
                if(selected.equals("Local File")){
                    chooseFile(selectedServer, PluginBundle.message("sh.label.choose.shell.script"),isLocalFile = true) {
                        myScriptSelector!!.text = it
                    }
                } else{
                    chooseFile(selectedServer, PluginBundle.message("sh.label.choose.shell.script"), isLocalFile = false) {
                        myScriptSelector!!.text = it
                    }
                }
            }
            .createPopup()

        val popupWidth = popupMenu.content.preferredSize.width
        val popupLocation = RelativePoint(button, Point(button.width - popupWidth, button.height))
        popupMenu.show(popupLocation)

    }

    private fun getSelectedServer(): String? = hostBox?.selectedItem as? String

    private fun chooseFile(server: String, title: String, isLocalFile: Boolean = server == localhost, onChosen: (String) -> Unit) {
        if (isLocalFile) {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(title)
            FileChooser.chooseFile(descriptor, project, null) { it?.path?.let(onChosen) }
        } else {
            val remoteFile = getRemoteRoot(server) ?: return
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(title).apply {
                isForcedToUseIdeaFileChooser = true
                setRoots(remoteFile)
            }
            FileChooser.chooseFile(descriptor, project, null) { it?.url?.let(onChosen) }
        }
    }

    private fun chooseFolder(server: String, title: String, onChosen: (String) -> Unit) {
        if (server == localhost) {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(title)
            FileChooser.chooseFile(descriptor, project, null) { it?.path?.let(onChosen) }
        } else {
            val remoteRoot = getRemoteRoot(server) ?: return
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(title).apply {
                isForcedToUseIdeaFileChooser = true
                setRoots(remoteRoot)
            }
            FileChooser.chooseFile(descriptor, project, null) { it?.path?.let(onChosen) }
        }
    }

    private fun getRemoteRoot(serverName: String) = service.getServer(serverName)?.let { server ->
        virtualFileSystem.findFileByPath(server.sftpRootPath)
    }

    fun createUIComponents(): DialogPanel {
        // Initialize all UI components before building the panel
        myScript = RawCommandLineEditor()
        myScriptSelector = TextFieldWithBrowseButton()
        myScriptOptions = RawCommandLineEditor()
        myScriptFileWorkingDirectory = TextFieldWithBrowseButton()
        myInterpreterSelector = TextFieldWithBrowseButton()
        myInterpreterOptions = RawCommandLineEditor()
        myExecuteFileInTerminal = JBCheckBox("Execute in Terminal")
        myEnvComponent = EnvironmentVariablesComponent(project){
            if(notLocalHost!!.invoke()){
                service.getServer( hostBox!!.selectedItem!! as String)?.let {
                   service.getConnection(it)?.let {
                       return@EnvironmentVariablesComponent it.getEnvironmentVariables() .toMutableMap()
                   }
                }
                return@EnvironmentVariablesComponent mutableMapOf()
            } else{
                return@EnvironmentVariablesComponent GeneralCommandLine().parentEnvironment.toMutableMap()
            }

        }.apply{
            text=""
        }
        remoteUploadPath = TextFieldWithBrowseButton()
        localDownloadPath = TextFieldWithBrowseButton()
        runAfter = RunAfterTasksPanel(task)
        downloadFiles = FilesPickerPanel(project,true)
        uploadFiles = FilesPickerPanel(project)
        hostBox = ComboBox(servers)
        servers.selectedItem = if(taskType == RunConfigurationTaskType.Script) localhost else (if(servers.size == 0) "" else servers.getElementAt(0))
        notLocalHost = hostBox!!.selectedValueMatches{
            it != localhost
        }
        hostBox!!.addActionListener {
            downloadFiles!!.host=servers.selectedItem as String
            if(localhost.equals(downloadFiles!!.host)){
                myInterpreterSelector!!.text = project.getDefaultShell()
            } else{
                service.getServer( downloadFiles!!.host)?.let {
                    myInterpreterSelector!!.text =it.systemInfo.defaultShell
                }
            }
        }
    
        
        // AOSP init
        aospRootPath = TextFieldWithBrowseButton()
        lunchTargetBox = ComboBox(arrayOf())
        lunchTargetBox!!.isEditable = true
        fetchLunchTargetsButton = JButton("Fetch")
        fetchLunchTargetsButton!!.addActionListener {
            fetchLunchTargets()
        }
        buildTargetField = TextFieldWithBrowseButton()
        buildTargetModuleBox = ComboBox(arrayOf()) // Mock
        buildTargetModuleBox!!.isEditable = true
        buildTargetTypeModule = JBRadioButton("Module")
        buildTargetTypePath = JBRadioButton("Path")
        binaryOutputPanel = FilesPickerPanel(project, isRemote = true)
        buildActionBox = ComboBox(arrayOf("Build Only", "Build & Download", "Build & Download & Push"))
        adbDeviceIdField = JBTextField()

        setupPickers()
        setupAospPickers()
        return createUI()
    }

    fun resetEditorFrom(configuration: TaskModel = task) {
        this.task = configuration
        myScriptFileRadioButton?.isSelected = configuration.isScriptFile
        myScriptTextRadioButton?.isSelected = !configuration.isScriptFile
        selectMode()
        myScript!!.text = configuration.scriptText
        myScriptSelector!!.text = configuration.scriptFile
        myScriptOptions!!.text = configuration.scriptOptions
        myScriptFileWorkingDirectory!!.text = configuration.workingDir
    downloadFiles!!.host=servers.selectedItem as String

        if (configuration.interpreterPath.isEmpty()) {
            if (localhost.equals(downloadFiles!!.host)) {
                myInterpreterSelector!!.text = project.getDefaultShell()
            } else {
                service.getServer(downloadFiles!!.host)?.let {
                    myInterpreterSelector!!.text = it.systemInfo.defaultShell
                }
            }
        } else {
            myInterpreterSelector!!.text = configuration.interpreterPath
        }

        myInterpreterOptions!!.text = configuration.interpreterOptions
        myExecuteFileInTerminal!!.isSelected = configuration.executeInTerminal
        servers.selectedItem = if(!configuration.server.isEmpty()) configuration.server else servers.selectedItem
        taskName.set(configuration.scriptName)
        runAfter!!.setRunAfter(configuration.runAfter)
        myEnvComponent!!.envData = EnvironmentVariablesData.readExternal(mapStringToElement(configuration.envData, passParentEnv = configuration.passParentEnv))
        downloadFiles!!.host = (configuration.server)
        remoteUploadPath!!.text = configuration.remoteFolder
        localDownloadPath!!.text = configuration.localFolder
        downloadFiles!!.setFiles(configuration.downloadFiles)
        downloadFiles!!.host = servers.selectedItem as String
        downloadFiles!!.host = servers.selectedItem as String
        uploadFiles!!.setFiles(configuration.uploadFiles)
        
        // AOSP Fields
        aospRootPath?.text = configuration.aospRootPath
        lunchTargetBox?.selectedItem = configuration.lunchTarget
        lunchTargetBox?.selectedItem = configuration.lunchTarget
        buildTargetField?.text = configuration.buildTarget
        buildTargetModuleBox?.selectedItem = configuration.buildTarget
        if (configuration.buildTargetType == 0) buildTargetTypeModule?.isSelected = true else buildTargetTypePath?.isSelected = true
        buildActionBox?.selectedIndex = configuration.buildAction
        binaryOutputPanel?.setFiles(configuration.binaryOutputLocation)
        adbDeviceIdField?.text = configuration.adbDeviceId

        selectMode()
    }

    private fun selectMode() {
        val scriptExecutionSelected = myScriptFileRadioButton?.isSelected ?:false
        myScriptPathPanel?.isVisible = taskType == RunConfigurationTaskType.Script && scriptExecutionSelected
        myScriptTextPanel?.isVisible = taskType == RunConfigurationTaskType.Script && !scriptExecutionSelected
        myScriptTypeJPanel?.isVisible = taskType == RunConfigurationTaskType.Script
        myUploadsJPanel?.isVisible = taskType == RunConfigurationTaskType.ScpFileTransfer
        myDownloadsJPanel?.isVisible = taskType == RunConfigurationTaskType.ScpFileTransfer
        hostJPanel?.isVisible = taskType == RunConfigurationTaskType.Script || taskType == RunConfigurationTaskType.AOSP
        // Hide script panels if AOSP
        if (taskType == RunConfigurationTaskType.AOSP) {
            myScriptPathPanel?.isVisible = false
            myScriptTextPanel?.isVisible = false
            myScriptTypeJPanel?.isVisible = false
            myUploadsJPanel?.isVisible = false
            myDownloadsJPanel?.isVisible = false
        }
    }

    private fun getServersList(): List<String> {
        return getSSHService().getSavedConnections().map {
            "${it.username}@${it.host}"
        }.toMutableList().also {
            if(taskType == RunConfigurationTaskType.Script) it.add(0, localhost)
        }
    }
    fun setData(){
        task.server = hostBox!!.selectedItem as String
        task.scriptName = taskName.get()
        task.scriptText = myScript!!.text
        task.scriptFile = myScriptSelector!!.text
        task.scriptOptions = myScriptOptions!!.text
        task.workingDir = myScriptFileWorkingDirectory!!.text
        task.executeInTerminal = myExecuteFileInTerminal!!.isSelected
        task.interpreterPath = myInterpreterSelector!!.text
        task.interpreterOptions = myInterpreterOptions!!.text
        task.scriptType = taskType.ordinal
        task.isScriptFile = myScriptFileRadioButton?.isSelected?:false
        task.envData = myEnvComponent!!.envData.envs.entries.joinToString(";") { "${it.key}=${it.value}" }
        task.runAfter = runAfter!!.getTaskIds()
        task.passParentEnv = myEnvComponent!!.envData.isPassParentEnvs
        task.downloadFiles = downloadFiles!!.getFiles()
        task.uploadFiles = uploadFiles!!.getFiles()
        task.localFolder = localDownloadPath!!.text
        task.remoteFolder = remoteUploadPath!!.text
        
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

        task.updatedTimeStamp = System.currentTimeMillis()
        if(task.createTimeStamp == 0L){
            task.createTimeStamp = task.updatedTimeStamp
        }
        task.isLocal = localhost.equals(downloadFiles!!.host)
    }
    fun save(){
        setData()
        service.addScript(task)
    }

    private fun createUI(): DialogPanel {
        val panel = panel {
            if (!fromRunConfiguration) {
                row("Task Name:") {
                    textField().bindText(taskName).resizableColumn().align(Align.FILL)
                }
            }
             // General Server Selection (visible for Script & AOSP)
            if (taskType != RunConfigurationTaskType.ScpFileTransfer) {
                 row("Server:") {
                    cell(hostBox!!).resizableColumn().align(Align.FILL)
                }
            }
            if(taskType == RunConfigurationTaskType.Script){
                row {
                    panel {
                        buttonsGroup {
                            row {
                                radioButton("Execute Script File").applyToComponent {
                                    myScriptFileRadioButton = this
                                    isSelected = true
                                    addActionListener { selectMode() }
                                }
                                radioButton("Execute Script Text").applyToComponent {
                                    myScriptTextRadioButton = this
                                    isSelected = false
                                    addActionListener { selectMode() }
                                }
                            }
                        }
                    }.align(Align.CENTER)

                }
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
                row {
                    cell(TitledSeparator("Interpreter"))
                }.visibleIf(myScriptFileRadioButton!!.selected)
                row("Interpreter Path:") {
                    cell(myInterpreterSelector!!).resizableColumn().align(Align.FILL)
                }.visibleIf(myScriptFileRadioButton!!.selected)
                row("Interpreter Options:") {
                    cell(myInterpreterOptions!!).resizableColumn().align(Align.FILL)
                }.visibleIf(myScriptFileRadioButton!!.selected)
                row {
                    cell(myExecuteFileInTerminal!!).resizableColumn().align(Align.FILL).align(Align.CENTER)
                }
            }
            if(taskType == RunConfigurationTaskType.ScpFileTransfer){
                with(collapsibleGroup("Download Files") {
                    row {
                        cell(downloadFiles!!).resizableColumn().align(Align.FILL)
                    }
                }) {
                    expanded =true
                }
                row("Local Download Path:") {
                    cell(localDownloadPath!!).resizableColumn().align(Align.FILL)
                }
                with(collapsibleGroup("Upload Files") {
                    row {
                        cell(uploadFiles!!).resizableColumn().align(Align.FILL)
                    }
                }) {
                    expanded =true
                }
                row("Remote Upload Path:") {
                    cell(remoteUploadPath!!).resizableColumn().align(Align.FILL)
                }
            }
            if (taskType == RunConfigurationTaskType.AOSP) {
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
                row("Build Target:") {
                    panel {
                        buttonsGroup {
                            row {
                                radioButton("Module").applyToComponent { 
                                    buildTargetTypeModule = this; 
                                    isSelected=true 
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
                
                row {
                    cell(binaryOutputPanel!!).resizableColumn().align(Align.FILL)
                } 
                

                row("Action:") {
                    cell(buildActionBox!!).resizableColumn().align(Align.FILL)
                }

                row("ADB Device ID:") {
                    cell(adbDeviceIdField!!).resizableColumn().align(Align.FILL)
                }.visibleIf(buildActionBox!!.selectedValueMatches { it == "Build & Push" })
            }
            with(collapsibleGroup("Run After Tasks") {
                row {
                    cell(runAfter!!).resizableColumn().align(Align.FILL)
                }
            }) {
                expanded = task.runAfter.isNotEmpty()
            }
            

        }
        resetEditorFrom()
        return panel
    }




    

    private fun fetchLunchTargets() {
        val selectedServer = getSelectedServer()
        val rootPath = aospRootPath?.text
        
        if (selectedServer == null || rootPath.isNullOrEmpty()) {
            return
        }
        
        val serverConfig = service.getServer(selectedServer) ?: return
        fetchLunchTargetsButton!!.isEnabled = false
        
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                service.getConnection(serverConfig)?.let { conn ->
                    // Command to source envsetup and print menu. 
                    // dependent on shell, typically bash is used for AOSP
                    val cmd = "bash -c 'cd ${rootPath} && source build/envsetup.sh && print_lunch_menu'"
                    val output = conn.execute(cmd, 15) // Short timeout for menu
                    
                    val targets = mutableListOf<String>()
                    // Regex to match "1. target-name"
                    val regex = Regex("^\\s*\\d+\\.\\s+(\\S+)", RegexOption.MULTILINE)
                    regex.findAll(output).forEach { match ->
                         targets.add(match.groupValues[1])
                    }
                    
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
                SwingUtilities.invokeLater {
                    fetchLunchTargetsButton!!.isEnabled = true
                }
            }
        }
    }

    private fun fetchModules() {
        val selectedServer = getSelectedServer()
        val rootPath = aospRootPath?.text
        val selectedLunch = lunchTargetBox?.selectedItem as? String
        
        if (selectedServer == null || rootPath.isNullOrEmpty() || selectedLunch.isNullOrEmpty()) {
            return
        }
        
        val serverConfig = service.getServer(selectedServer) ?: return
        
        // Indicate loading...
        
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
             try {
                service.getConnection(serverConfig)?.let { conn ->
                    // Command: source envsetup -> lunch -> allmod
                    // 'allmod' lists modules. We assume it prints them to stdout.
                    val cmd = "bash -c 'cd ${rootPath} && source build/envsetup.sh && lunch ${selectedLunch} && allmod'"
                    val output = conn.execute(cmd, 60) // Longer timeout for module listing
                    
                    val modules = mutableListOf<String>()
                    // Parse output lines, strict filtering for module names
                    // Module names are typically alphanumeric, no spaces, no slashes
                    output.lines().forEach { line ->
                         val t = line.trim()
                         if(t.isNotEmpty() && 
                            !t.contains(" ") && 
                            !t.contains("/") && 
                            !t.contains("\\") &&
                            !t.startsWith("[") &&
                            !t.startsWith("make") &&
                            !t.startsWith("INFO:") &&
                            !t.startsWith("WARNING:") &&
                            !t.contains("=")) {
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
        if(buildTargetTypeModule?.isSelected == false) return

        val selectedServer = getSelectedServer()
        val rootPath = aospRootPath?.text
        val selectedLunch = lunchTargetBox?.selectedItem as? String
        val selectedModule = buildTargetModuleBox?.selectedItem as? String
        
        if (selectedServer == null || rootPath.isNullOrEmpty() || selectedLunch.isNullOrEmpty() || selectedModule.isNullOrEmpty()) {
            return
        }
        
        val serverConfig = service.getServer(selectedServer) ?: return
         // Update host for pickers
         binaryOutputPanel!!.host = selectedServer
         downloadFiles!!.host = selectedServer
         uploadFiles!!.host  = selectedServer

        
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
             try {
                service.getConnection(serverConfig)?.let { conn ->
                    // Command: source envsetup -> lunch -> outmod
                    // 'outmod' lists output files.
                    val cmd = "bash -c 'cd ${rootPath} && source build/envsetup.sh && lunch ${selectedLunch} && outmod ${selectedModule}'"
                    val output = conn.execute(cmd, 30)
                    
                    val files = mutableListOf<String>()
                    // Parse output lines - outmod typically outputs file paths
                    // Filter strictly for actual file paths
                     output.lines().forEach { line ->
                         val t = line.trim()
                         // Skip empty lines, log messages, build output, and non-path lines
                         if(t.isNotEmpty() && 
                            !t.startsWith("INFO:") && 
                            !t.startsWith("WARNING:") &&
                            !t.startsWith("ERROR:") &&
                            !t.startsWith("[") &&
                            !t.startsWith("=") &&
                            !t.startsWith("PRODUCT_") &&
                            !t.startsWith("TARGET_") &&
                            !t.contains("============") &&
                            !t.contains("including") &&
                            !t.contains("envsetup") &&
                            !t.contains("lunch") &&
                            (t.startsWith("/") || t.startsWith("out/"))) {
                             // Valid path - either absolute or relative starting with out/
                             if(t.startsWith("/")) {
                                 files.add(t)
                             } else {
                                  files.add("${rootPath}/${t}")
                             }
                         }
                    }
                    
                    SwingUtilities.invokeLater {
                        if (files.isNotEmpty()) {
                            // Convert paths to SFTP URLs
                            val sftpUrls = files.map { path ->
                                "sftp://${serverConfig.username}@${serverConfig.host}:${serverConfig.port}${path}"
                            }
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