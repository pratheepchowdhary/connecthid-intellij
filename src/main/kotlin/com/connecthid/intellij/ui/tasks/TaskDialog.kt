package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTask
import com.connecthid.intellij.utils.Utils.mapStringToElement
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import java.awt.Dimension
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

class TaskDialog(
    private val project: Project, private val taskType: RunConfigurationTask, taskModel: TaskModel?=null
) : DialogWrapper(project) {
    val propertyGraph = PropertyGraph()
    private var dialogPanel: DialogPanel? = null
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

    private var runAfter: RunAfterTasksPanel?=null
    private var downloadFiles: FilesPickerPanel?=null
    private var uploadFiles: FilesPickerPanel?=null

    private val task = taskModel ?: TaskModel(scriptId = UUID.randomUUID().toString())
    var taskName = propertyGraph.property("")

    init {
        init()
        title = "Script Dialog"
    }

    private fun setupPickers() {
        myScriptSelector?.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle(PluginBundle.message("sh.label.choose.shell.script"))
        )
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

    private fun getSelectedServer(): String? = hostBox?.selectedItem as? String

    private fun chooseFile(server: String, title: String, onChosen: (String) -> Unit) {
        if (server == localhost) {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(title)
            FileChooser.chooseFile(descriptor, project, null) { it?.path?.let(onChosen) }
        } else {
            val remoteFile = getRemoteRoot(server) ?: return
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(title).apply {
                    isForcedToUseIdeaFileChooser = true
                    setRoots(remoteFile)
                }
            FileChooser.chooseFile(descriptor, project, null) { it?.path?.let(onChosen) }
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

    private fun createUIComponents() {
        // Initialize all UI components before building the panel
        myScript = RawCommandLineEditor()
        myScriptSelector = TextFieldWithBrowseButton()
        myScriptOptions = RawCommandLineEditor()
        myScriptFileWorkingDirectory = TextFieldWithBrowseButton()
        myInterpreterSelector = TextFieldWithBrowseButton()
        myInterpreterOptions = RawCommandLineEditor()
        myExecuteFileInTerminal = JBCheckBox("Execute in Terminal")
        myEnvComponent = EnvironmentVariablesComponent().apply{
            text=""
        }
        remoteUploadPath = TextFieldWithBrowseButton()
        localDownloadPath = TextFieldWithBrowseButton()
        runAfter = RunAfterTasksPanel(task)
        downloadFiles = FilesPickerPanel(project,true)
        uploadFiles = FilesPickerPanel(project)
        hostBox = ComboBox(servers)
        servers.selectedItem = if(taskType == RunConfigurationTask.Script) localhost else (if(servers.size == 0) "" else servers.getElementAt(0))
        hostBox!!.addActionListener {
           downloadFiles!!.host=servers.selectedItem as String
        }
        setupPickers()

    }

    fun resetEditorFrom(configuration: TaskModel = task) {
        myScriptFileRadioButton?.isSelected = configuration.isScriptFile
        myScriptTextRadioButton?.isSelected = !configuration.isScriptFile
        selectMode()
        myScript!!.text = configuration.scriptText
        myScriptSelector!!.text = configuration.scriptFile
        myScriptOptions!!.text = configuration.scriptOptions
        myScriptFileWorkingDirectory!!.text = configuration.workingDir
        myInterpreterSelector!!.text = configuration.interpreterPath
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
        uploadFiles!!.setFiles(configuration.uploadFiles)
    }

    override fun getInitialSize(): Dimension {
        return Dimension(650, 500)
    }


    private fun selectMode() {
        val scriptExecutionSelected = myScriptFileRadioButton?.isSelected ?:false
        myScriptPathPanel?.isVisible = taskType == RunConfigurationTask.Script && scriptExecutionSelected
        myScriptTextPanel?.isVisible = taskType == RunConfigurationTask.Script && !scriptExecutionSelected
        myScriptTypeJPanel?.isVisible = taskType == RunConfigurationTask.Script
        myUploadsJPanel?.isVisible = taskType == RunConfigurationTask.SftpFileTransfer
        myDownloadsJPanel?.isVisible = taskType == RunConfigurationTask.SftpFileTransfer
        hostJPanel?.isVisible = taskType == RunConfigurationTask.Script
    }

    private fun getServersList(): List<String> {
        return getSSHService().getSavedConnections().map {
            "${it.username}@${it.host}"
        }.toMutableList().also {
           if(taskType == RunConfigurationTask.Script) it.add(0, localhost)
        }
    }

    private fun save(){
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
        service.addScript(task)
    }

    override fun createCenterPanel(): JComponent {
        if (dialogPanel == null) {
            dialogPanel = createDialogPanel()
        }
        return dialogPanel!!
    }


    fun createUI(): DialogPanel {
        val panel = panel {
            row("Task Name:") {
                textField().bindText(taskName).resizableColumn().align(Align.FILL)
            }
            row("Server:") {
                cell(hostBox!!).resizableColumn().align(Align.FILL)
            }
            if(taskType == RunConfigurationTask.Script){
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
            if(taskType == RunConfigurationTask.SftpFileTransfer){
                with(collapsibleGroup("Download Files") {
                    row {
                        cell(downloadFiles!!).resizableColumn().align(Align.FILL)
                    }
                }) {
                    expanded =true
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
                row("Local Download Path:") {
                    cell(localDownloadPath!!).resizableColumn().align(Align.FILL)
                }
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



    fun createDialogPanel(): DialogPanel {
        createUIComponents()
        // Now build the panel
        return createUI()
    }

    override fun doOKAction() {
        save()
        super.doOKAction()

    }

    override fun doCancelAction() {
        super.doCancelAction()
    }
}