package com.connecthid.intellij.ui.tasks

import com.connecthid.intellij.PluginBundle
import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.utils.Utils.parseSftpUrl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBList.StripedListCellRenderer
import com.intellij.ui.scale.JBUIScale.scale
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.io.File
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class FilesPickerPanel(val project: Project, val isRemote: Boolean = false): JPanel() {
    private val service = getSSHService()
    private val virtualFileSystem =
        VirtualFileManager.getInstance().getFileSystem(SftpFileSystem.PROTOCOL) as SftpFileSystem
    private val myList: JBList<Any>
    private val myModel: CollectionListModel<Any>
    private val myPanel: JPanel
    var host: String =""
    init {
        myModel = CollectionListModel<Any>()
        myList = JBList<Any>(myModel)
        myList.getEmptyText().setText(PluginBundle.message("after.launch.panel.empty"))
        myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        myList.setCellRenderer(MyListCellRenderer())
        myList.setVisibleRowCount(4)

        myModel.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent?) {
                //updateText()
            }

            override fun intervalRemoved(e: ListDataEvent?) {
                //updateText()
            }

            override fun contentsChanged(e: ListDataEvent?) {
            }
        })

        val myDecorator = ToolbarDecorator.createDecorator<Any>(myList)

        myDecorator.setEditAction(object : AnActionButtonRunnable {
            override fun run(button: AnActionButton) {
                //updateText()
            }
        })
        myDecorator.setEditActionUpdater(object : AnActionButtonUpdater {
            override fun isEnabled(e: AnActionEvent): Boolean {
                return  true
            }
        })
        myDecorator.setAddAction(object : AnActionButtonRunnable {
            override fun run(button: AnActionButton) {
                doAddAction(button)
            }
        })
        myDecorator.setAddActionUpdater(object : AnActionButtonUpdater {
            override fun isEnabled(e: AnActionEvent): Boolean {
                //return checkBeforeRunTasksAbility(true)
                return true
            }
        })

        myDecorator.setRemoveAction(object : AnActionButtonRunnable {
            override fun run(button: AnActionButton?) {
                ListUtil.removeSelectedItems<Any>(myList)
            }
        })



        myPanel = myDecorator.createPanel()
        myDecorator.getActionsPanel().setCustomShortcuts(
            CommonActionsPanel.Buttons.EDIT,
            CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.EDIT),
            CommonShortcuts.DOUBLE_CLICK_1
        )


        setLayout(BorderLayout())
        add(myPanel, BorderLayout.CENTER)
        val checkboxPanel = JPanel(FlowLayout(FlowLayout.LEADING, scale(10), scale(5)))
        add(checkboxPanel, BorderLayout.SOUTH)
    }

    fun setFiles(files: String){
        val fileTasks = files.split(";")
        fileTasks.forEach { file ->
            if(!file.isEmpty()){
                val task = if (isRemote) {
                    val url = parseSftpUrl(file)
                    val server = service.getServer("${url.username}@${url.host}")
                    server?.let { SftpFile(url.path, it) }
                } else {
                    File(file)
                }
                task?.let {
                    myModel.add(it)
                }
            }
        }
    }
    fun getFiles(): String{
        return if(isRemote)myModel.items.joinToString(";") { (it as SftpFile).url } else myModel.items.joinToString(";") { (it as File).path }
    }

    private fun doAddAction(button: AnActionButton) {
        chooseFiles("Choose files"){
            it.forEach{
                val task = if (isRemote) {
                    val url = parseSftpUrl(it)
                    val server = service.getServer("${url.username}@${url.host}")
                    server?.let { SftpFile(url.path, it) }
                } else {
                    File(it)
                }
                task?.let {
                    if(!myModel.contains(it)){
                        myModel.add(it)
                    }
                }
            }
        }
    }

    private fun getRemoteRoot(serverName: String) = service.getServer(serverName)?.let { server ->
        virtualFileSystem.findFileByPath(server.sftpRootPath)
    }


    private fun chooseFiles(title: String, onChosen: (List<String>) -> Unit) {
        if (!isRemote) {
            val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
                .withTitle(title)

            FileChooser.chooseFiles(descriptor, project, null) { files ->
                val paths = files.mapNotNull { it.path }
                onChosen(paths)
            }
        } else {
            val remoteFile = getRemoteRoot(host) ?: return
            val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
                .withTitle(title)
                .apply {
                    isForcedToUseIdeaFileChooser = true
                    setRoots(remoteFile)
                }

            FileChooser.chooseFiles(descriptor, project, null) { files ->
                val paths = files.mapNotNull { it.url }
                onChosen(paths)
            }
        }
    }


    private inner class MyListCellRenderer : StripedListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<Any>,
            value: Any,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if(isRemote){
                val item = value as SftpFile
                setIcon(FileTypeManager.getInstance().getFileTypeByFileName(item.name).icon)
                setText(item.url)
            } else {
                val item = value as File
                setIcon(FileTypeManager.getInstance().getFileTypeByFileName(item.name).icon)
                setText(item.path)
            }

            return this
        }
    }
}