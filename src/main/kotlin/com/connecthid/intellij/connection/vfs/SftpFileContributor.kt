package com.connecthid.intellij.connection.vfs

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import org.jetbrains.annotations.Nls
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class SftpFileContributor : SearchEverywhereContributor<VirtualFile> {
    private lateinit var project: Project
    fun setProject(project: Project) {
        this.project = project
    }

    override fun getSearchProviderId(): String {
        return "SFTPFiles"
    }

    override fun getGroupName(): @Nls String {
        return  "testconnection"
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
            ): java.awt.Component {
                val label = JLabel()
                if (value != null) {
                    label.text = value.presentableUrl
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