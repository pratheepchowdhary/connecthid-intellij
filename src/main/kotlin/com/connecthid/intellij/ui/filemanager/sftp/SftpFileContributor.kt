package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.vfs.SftpFile
import com.connecthid.intellij.connection.vfs.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.services.ServerConnectionService
import com.intellij.ide.actions.searcheverywhere.EssentialContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewProvider
import com.intellij.openapi.actionSystem.AnActionEvent
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


class SftpFileContributor(p01: AnActionEvent) : SearchEverywhereContributor<SftpFile> , EssentialContributor, SearchEverywherePreviewProvider{
    private val project: Project = p01.project!!
    private  val sshService: ServerConnectionService  = project.getSSHService()


    override fun getSearchProviderId(): String {
        return javaClass.name
    }

    override fun getGroupName(): @Nls String {
        return  "ConnectHID SFTP"
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
        consumer: Processor<in SftpFile>
    ) {
        println(pattern)
       val fileSystem = if(sshService.getConnectedServers().size > 0) sshService.getConnection(sshService.getConnectedServers().get(0))?.fileSystem else null
        fileSystem?.let {
          it.searchFiles(pattern).forEach{
              consumer.process(it)
          }
        }
    }

    override fun isShownInSeparateTab(): Boolean {
        return true
    }



    override fun processSelectedItem(
        p0: SftpFile,
        p1: Int,
        p2: String
    ): Boolean {
        val fileSystem = (p0.fileSystem as SftpFileSystem)
        val fileStat = fileSystem.getFileStat(p0.path)
        p0.fileEntry = fileStat
        return if(fileStat != null) fileSystem.openFileInIDE(p0) else false
    }

    override fun getItemDescription(element: SftpFile): String? {
        return super.getItemDescription(element)
    }

    override fun getDataForItem(
        element: SftpFile,
        dataId: String
    ): Any? {
        return super.getDataForItem(element, dataId)
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
                    label.text = value.url
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

