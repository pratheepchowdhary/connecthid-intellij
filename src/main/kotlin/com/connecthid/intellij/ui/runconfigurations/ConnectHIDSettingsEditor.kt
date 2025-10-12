package com.connecthid.intellij.ui.runconfigurations

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.FileTask
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import javax.swing.*

class ConnectHIDSettingsEditor(
    private val project: Project,
    private val task: RunConfigurationTask
) : SettingsEditor<ConnectHIDRunConfiguration>() {

    private var myPanel: JPanel? = null
    private var myScriptPathPanel: JPanel? = null
    private var myScriptTextPanel: JPanel? = null
    private var myUploadsJPanel: JPanel? = null
    private var myDownloadsJPanel: JPanel? = null
    private var myScriptTypeJPanel: JPanel? = null
    private var myScript: RawCommandLineEditor? = null
    private var myScriptSelector: TextFieldWithBrowseButton? = null
    private var myScriptOptions: RawCommandLineEditor? = null
    private var myScriptFileWorkingDirectory: TextFieldWithBrowseButton? = null
    private var myScriptWorkingDirectory: TextFieldWithBrowseButton? = null
    private var myInterpreterSelector: TextFieldWithBrowseButton? = null
    private var localTargetPath: TextFieldWithBrowseButton? = null
    private var remoteUploadPath: TextFieldWithBrowseButton? = null
    private var localDownloadPath: TextFieldWithBrowseButton? = null
    private var remoteTargetPath: TextFieldWithBrowseButton? = null

    private var myInterpreterOptions: RawCommandLineEditor? = null
    private var myExecuteFileInTerminal: JBCheckBox? = null
    private var myExecuteScriptInTerminal: JBCheckBox? = null
    private var myEnvComponent: EnvironmentVariablesComponent? = null
    private var myScriptEnvComponent: EnvironmentVariablesComponent? = null
    private var myScriptGroup: ButtonGroup
    private var myScriptFileRadioButton: JBRadioButton? = null
    private var myScriptTextRadioButton: JBRadioButton? = null
    private var titleTitledSeparator: TitledSeparator? = null
    private var collapsibleTitledSeparator: CollapsibleTitledSeparator? = null
    private val service = getSSHService()
    private val virtualFileSystem =
        VirtualFileManager.getInstance().getFileSystem(SftpFileSystem.PROTOCOL) as SftpFileSystem

    private var hostJPanel: JPanel? = null
    private var hostBox: JComboBox<String>? = null
    private val localhost = "localhost"
    private val servers by lazy { DefaultComboBoxModel(getServersList().toTypedArray()) }

    init {
        myScriptGroup = ButtonGroup().apply {
            add(myScriptTextRadioButton)
            add(myScriptFileRadioButton)
        }

        myScriptFileRadioButton?.addActionListener { selectMode() }
        myScriptTextRadioButton?.addActionListener { selectMode() }

        hostBox?.model = servers
        hostBox?.selectedItem = localhost

        setupPickers()
    }

    /** Common chooser setup logic for all UI fields */
    private fun setupPickers() {
        // Script file selector
        myScriptSelector?.addBrowseFolderListener(
            project, FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle(PluginBundle.message("sh.label.choose.shell.script"))
        )

        // Remote Upload Folder
        remoteUploadPath?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFolder(selectedServer, "Choose upload folder") {
                remoteUploadPath!!.text = it
            }
        }

        // Remote Target File
        remoteTargetPath?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFile(selectedServer, "Choose remote target file") {
                remoteTargetPath!!.text = it
            }
        }

        // Local Target File
        localTargetPath?.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle("Choose local target file")
        )

        // Local Download Folder
        localDownloadPath?.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Choose local download folder")
        )

        // Script File Working Directory
        myScriptFileWorkingDirectory?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFolder(selectedServer, "Choose script working directory") {
                myScriptFileWorkingDirectory!!.text = it
            }
        }

        // Local Script Working Directory (text mode)
        myScriptWorkingDirectory?.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Choose local script working directory")
        )

        // Interpreter Picker
        myInterpreterSelector?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFile(selectedServer, PluginBundle.message("sh.label.choose.interpreter")) {
                myInterpreterSelector!!.text = it
            }
        }
    }

    /** Helper: Get selected server name */
    private fun getSelectedServer(): String? = hostBox?.selectedItem as? String

    /** Chooser helpers for local & remote contexts **/
    private fun chooseFile(server: String, title: String, onChosen: (String) -> Unit) {
        if (server == localhost) {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(title)
            FileChooser.chooseFile(descriptor, project, null) { it?.path?.let(onChosen) }
        } else {
            val remoteFile = getRemoteRoot(server) ?: return
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle(title)
                .apply {
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
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(title)
                .apply {
                    isForcedToUseIdeaFileChooser = true
                    setRoots(remoteRoot)
                }
            FileChooser.chooseFile(descriptor, project, null) { it?.path?.let(onChosen) }
        }
    }

    /** Helper to resolve the remote root VirtualFile */
    private fun getRemoteRoot(serverName: String) =
        service.getServer(serverName)?.let { server ->
            virtualFileSystem.findFileByPath(server.sftpRootPath)
        }

    private fun createUIComponents() {
        collapsibleTitledSeparator = CollapsibleTitledSeparator("Hello")

    }

    override fun resetEditorFrom(configuration: ConnectHIDRunConfiguration) {
        if (configuration.isExecuteScriptFile()) {
            myScriptFileRadioButton!!.isSelected = true
        } else {
            myScriptTextRadioButton!!.isSelected = true
        }
        selectMode()

        myScript!!.text = configuration.getScriptText()
        myScriptWorkingDirectory!!.text = configuration.getScriptWorkingDirectory()
        myExecuteScriptInTerminal!!.isSelected = configuration.isExecuteInTerminal()
        myScriptEnvComponent!!.envData = configuration.getEnvData()
        titleTitledSeparator!!.text = configuration.task.name

        myScriptSelector!!.text = configuration.getScriptPath()
        myScriptOptions!!.text = configuration.getScriptOptions()
        myScriptFileWorkingDirectory!!.text = configuration.getScriptWorkingDirectory()
        myInterpreterSelector!!.text = configuration.getInterpreterPath()
        myInterpreterOptions!!.text = configuration.getInterpreterOptions()
        myExecuteFileInTerminal!!.isSelected = configuration.isExecuteInTerminal()
        servers.selectedItem = configuration.getServer()
        myEnvComponent!!.envData = configuration.getEnvData()
        remoteTargetPath!!.text = configuration.getRemoteTarget()
        remoteUploadPath!!.text = configuration.getRemoteFolder()
        localTargetPath!!.text = configuration.getLocalTarget()
        localDownloadPath!!.text = configuration.getLocalFolder()
    }

    override fun applyEditorTo(configuration: ConnectHIDRunConfiguration) {
        configuration.setScriptText(myScript!!.text)

        if (myScriptFileRadioButton!!.isSelected) {
            configuration.setScriptWorkingDirectory(myScriptFileWorkingDirectory!!.text)
            configuration.setExecuteInTerminal(myExecuteFileInTerminal!!.isSelected)
            configuration.setEnvData(myEnvComponent!!.envData)
            configuration.setExecuteScriptFile(true)
        } else {
            configuration.setScriptWorkingDirectory(myScriptWorkingDirectory!!.text)
            configuration.setExecuteInTerminal(myExecuteScriptInTerminal!!.isSelected)
            configuration.setEnvData(myScriptEnvComponent!!.envData)
            configuration.setExecuteScriptFile(false)
        }

        configuration.setScriptPath(myScriptSelector!!.text)
        configuration.setScriptOptions(myScriptOptions!!.text)
        configuration.setInterpreterPath(myInterpreterSelector!!.text)
        configuration.setInterpreterOptions(myInterpreterOptions!!.text)
        configuration.setRemoteTarget(remoteTargetPath!!.text)
        configuration.setRemoteFolder(remoteUploadPath!!.text)
        configuration.setLocalTarget(localTargetPath!!.text)
        configuration.setLocalFolder(localDownloadPath!!.text)
        configuration.setServer(hostBox!!.selectedItem as String)
    }

    private fun selectMode() {
        if (task == RunConfigurationTask.Script) {
            val scriptExecutionSelected = myScriptFileRadioButton!!.isSelected
            myScriptPathPanel!!.isVisible = scriptExecutionSelected
            myScriptTextPanel!!.isVisible = !scriptExecutionSelected
            myScriptTypeJPanel!!.isVisible = true
        } else {
            myScriptTextPanel!!.isVisible = false
            myScriptPathPanel!!.isVisible = false
            myScriptTypeJPanel!!.isVisible = false
        }

        hostJPanel!!.isVisible = task == RunConfigurationTask.Script
        myUploadsJPanel!!.isVisible = task == RunConfigurationTask.SftpFileTransfer
        myDownloadsJPanel!!.isVisible = task == RunConfigurationTask.SftpFileTransfer
    }

    override fun createEditor(): JComponent = myPanel!!

    private fun getServersList(): List<String> {
        return getSSHService().getSavedConnections().map {
            "${it.username}@${it.host}"
        }.toMutableList().also {
            it.add(0, localhost)
        }
    }
}
