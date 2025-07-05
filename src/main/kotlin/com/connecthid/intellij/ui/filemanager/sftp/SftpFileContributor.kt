package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.vfs.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class SftpFileContributor : SearchEverywhereContributor<VirtualFile> {
    private lateinit var project: Project
    private lateinit var sshService: ServerConnectionService
    fun setProject(project: Project) {
        this.project = project
        this.sshService = project.getSSHService()
    }

    override fun getSearchProviderId(): String {
        return javaClass.name
    }

    override fun getGroupName(): @Nls String {
        return  "ConnectHID SFTP Files"
    }

    override fun getSortWeight(): Int {
        return VIRTUAL_FILE_SORT_WEIGHT
    }

    override fun showInFindResults(): Boolean {
        return  true
    }

    override fun fetchElements(
        pattern: String,
        indicator: ProgressIndicator,
        consumer: Processor<in VirtualFile>
    ) {
        println(pattern)
       val fileSystem = if(sshService.getConnectedServers().size > 0) sshService.getConnection(sshService.getConnectedServers().get(0))?.fileSystem else null
        fileSystem?.let {
          it.searchFiles(pattern).forEach{
              consumer.process(it)
          }
        }
    }



    override fun processSelectedItem(
        p0: VirtualFile,
        p1: Int,
        p2: String
    ): Boolean {
        return (p0.fileSystem as SftpFileSystem).openFileInIDE(p0)
    }

    override fun getElementsRenderer(): ListCellRenderer<in VirtualFile> {
        return object : ListCellRenderer<VirtualFile> {
            override fun getListCellRendererComponent(
                list: JList<out VirtualFile>,
                value: VirtualFile?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = JLabel()
                if (value != null) {
                    label.text = "${value.fileSystem.protocol.lowercase()}:/${value.presentableUrl}"
                    label.icon = FileTypeManager.getInstance().getFileTypeByFileName(value.name).icon
                }
                if (isSelected) {
                    label.background = list.selectionBackground
                    label.foreground = list.selectionForeground
                    label.isOpaque = true
                }
                return label
            }
        }
    }

    companion object {
        // Standard sort weight for virtual file contributors in JetBrains IDEs is 400
        const val VIRTUAL_FILE_SORT_WEIGHT = 400
    }
}