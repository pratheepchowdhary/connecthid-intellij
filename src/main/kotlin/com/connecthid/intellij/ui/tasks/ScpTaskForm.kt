package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTaskType
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel

class ScpTaskForm(
    project: Project,
    taskModel: TaskModel? = null,
    fromRunConfiguration: Boolean = false
) : BaseTaskForm(project, RunConfigurationTaskType.ScpFileTransfer, taskModel, fromRunConfiguration) {

    private var remoteUploadPath: TextFieldWithBrowseButton? = null
    private var localDownloadPath: TextFieldWithBrowseButton? = null
    private var downloadFiles: FilesPickerPanel? = null
    private var uploadFiles: FilesPickerPanel? = null

    override fun createUIComponents(): DialogPanel {
        remoteUploadPath = TextFieldWithBrowseButton()
        localDownloadPath = TextFieldWithBrowseButton()
        downloadFiles = FilesPickerPanel(project, true)
        uploadFiles = FilesPickerPanel(project)
        
        localDownloadPath?.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Choose local download folder")
        )
        
        remoteUploadPath?.addActionListener {
            val selectedServer = getSelectedServer() ?: return@addActionListener
            chooseFolder(selectedServer, "Choose upload folder") {
                remoteUploadPath!!.text = it
            }
        }

        return super.createUIComponents()
    }

    override fun createSpecificUI(panel: Panel) {
        with(panel) {
            group("File Transfer Configuration") {
                with(collapsibleGroup("Download Files") {
                    row {
                        cell(downloadFiles!!).resizableColumn().align(Align.FILL)
                    }
                }) {
                    expanded = true
                }
                row("Local Download Path:") {
                    cell(localDownloadPath!!).resizableColumn().align(Align.FILL)
                }
                
                with(collapsibleGroup("Upload Files") {
                    row {
                        cell(uploadFiles!!).resizableColumn().align(Align.FILL)
                    }
                }) {
                    expanded = true
                }
                row("Remote Upload Path:") {
                    cell(remoteUploadPath!!).resizableColumn().align(Align.FILL)
                }
            }
        }
    }

    override fun setData() {
        task.scriptName = taskName.get()
        task.scriptType = taskType.ordinal
        task.runAfter = runAfter?.getTaskIds() ?: ""
        task.downloadFiles = downloadFiles?.getFiles() ?: ""
        task.uploadFiles = uploadFiles?.getFiles() ?: ""
        task.localFolder = localDownloadPath?.text ?: ""
        task.remoteFolder = remoteUploadPath?.text ?: ""
        task.updatedTimeStamp = System.currentTimeMillis()
        if (task.createTimeStamp == 0L) task.createTimeStamp = task.updatedTimeStamp
    }

    override fun resetEditorFrom(configuration: TaskModel) {
        this.task = configuration
        taskName.set(configuration.scriptName)
        runAfter?.setRunAfter(configuration.runAfter)
        remoteUploadPath?.text = configuration.remoteFolder
        localDownloadPath?.text = configuration.localFolder
        downloadFiles?.setFiles(configuration.downloadFiles)
        uploadFiles?.setFiles(configuration.uploadFiles)
        
        val server = configuration.server.ifEmpty { servers.selectedItem as? String ?: "" }
        downloadFiles?.host = server
        uploadFiles?.host = server
    }
}
