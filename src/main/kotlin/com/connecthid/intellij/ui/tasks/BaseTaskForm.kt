package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.TaskModel
import com.connecthid.intellij.ui.runconfigurations.RunConfigurationTaskType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import com.intellij.ui.components.JBList
import com.intellij.ui.awt.RelativePoint
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Point
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.SwingConstants
import java.awt.Component

abstract class BaseTaskForm(
    protected val project: Project,
    protected val taskType: RunConfigurationTaskType,
    taskModel: TaskModel? = null,
    protected val fromRunConfiguration: Boolean = false
) {
    val propertyGraph = PropertyGraph()
    protected val service = getSSHService()
    protected val localhost = "localhost"
    protected val servers by lazy { DefaultComboBoxModel(getServersList().toTypedArray()) }
    protected val virtualFileSystem =
        VirtualFileManager.getInstance().getFileSystem(SftpFileSystem.PROTOCOL) as SftpFileSystem

    protected var task = taskModel ?: TaskModel(scriptId = UUID.randomUUID().toString())
    var taskName = propertyGraph.property("")
    protected var hostBox: ComboBox<String>? = null
    protected var runAfter: RunAfterTasksPanel? = null

    abstract fun createSpecificUI(panel: Panel)
    abstract fun setData()
    abstract fun resetEditorFrom(configuration: TaskModel)

    open fun createUIComponents(): DialogPanel {
        hostBox = ComboBox(servers)
        servers.selectedItem = if (taskType == RunConfigurationTaskType.Script) localhost else (if (servers.size == 0) "" else servers.getElementAt(0))
        runAfter = RunAfterTasksPanel(task)
        
        return createUI()
    }

    protected fun createUI(): DialogPanel {
        return panel {
            if (!fromRunConfiguration) {
                row("Task Name:") {
                    textField().bindText(taskName).resizableColumn().align(Align.FILL)
                }
            }

            if (taskType != RunConfigurationTaskType.ScpFileTransfer) {
                row("Server:") {
                    cell(hostBox!!).resizableColumn().align(Align.FILL)
                }
            }

            createSpecificUI(this)

            with(collapsibleGroup("Run After Tasks") {
                row {
                    cell(runAfter!!).resizableColumn().align(Align.FILL)
                }
            }) {
                expanded = task.runAfter.isNotEmpty()
            }
        }.also {
            resetEditorFrom(task)
        }
    }

    protected fun getServersList(): List<String> {
        return getSSHService().getSavedConnections().map {
            "${it.username}@${it.host}"
        }.toMutableList().also {
            if (taskType == RunConfigurationTaskType.Script) it.add(0, localhost)
        }
    }

    protected fun getSelectedServer(): String? = hostBox?.selectedItem as? String

    protected fun chooseFile(server: String, title: String, isLocalFile: Boolean = server == localhost, onChosen: (String) -> Unit) {
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

    protected fun chooseFolder(server: String, title: String, onChosen: (String) -> Unit) {
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

    protected fun getRemoteRoot(serverName: String) = service.getServer(serverName)?.let { server ->
        virtualFileSystem.findFileByPath(server.sftpRootPath)
    }

    open fun save() {
        setData()
        service.addScript(task)
    }
}
