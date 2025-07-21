package com.connecthid.intellij.ui.filemanager.sftp.search

import com.connecthid.intellij.connection.vfs.SftpFile
import com.connecthid.intellij.connection.vfs.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.SelectPathAction
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.SelectServerAction
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.TextSearchRightActionAction.*
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewProvider
import com.intellij.ide.actions.searcheverywhere.SearchFieldActionsContributor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer


@OptIn(FlowPreview::class)
class SftpFileContributor(p01: AnActionEvent) : SearchEverywhereContributor<SftpFile> , SearchEverywherePreviewProvider,SearchFieldActionsContributor,SearchEverywhereExtendedInfoProvider{
    private val project: Project = p01.project!!
    private  val sshService  = project.getSSHService()
    val word = AtomicBooleanProperty(false).apply { afterChange { //todo
                                                                           } }
    val case = AtomicBooleanProperty(false).apply { afterChange {  } }
    val regexp = AtomicBooleanProperty(false).apply { afterChange {  }}
    val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    val queryFlow = MutableStateFlow("")
    var firstTimeQuery =false





    override fun getSearchProviderId(): String {
        return "com.connecthid.intellij.ui.filemanager.sftp.search.SftpFileContributor"
    }

    override fun getGroupName(): @Nls String {
        return  "ConnectHID SFTP"
    }

    override fun getSortWeight(): Int {
        return VIRTUAL_FILE_SORT_WEIGHT
    }

    override fun showInFindResults(): Boolean {
        return  false
    }

    override fun getActions(onChanged: Runnable): MutableList<AnAction> {
        val actions = mutableListOf<AnAction>()
        var pathAction: SelectPathAction? = null
        sshService.searchServers.firstOrNull { server ->
            pathAction=SelectPathAction(server.lastSearchPath,onFolderSuggestions={ currentPath->
                queryFlow.value = currentPath
            },onChanged = {
                server.lastSearchPath = it
                onChanged.run()
            })
            coroutineScope.launch {
                queryFlow.filter { it.isNotBlank() }.debounce(300).collect { currentPath ->
                    val suggestions = sshService.getConnection(server.stmpName)?.fileSystem?.listFolderPaths(currentPath)
                    println(currentPath)
                    withContext(Dispatchers.Main){
                        pathAction.hidePopup()
                        val model = pathAction.model
                        model.removeAllElements()
                        model.addElement(currentPath)
                        println(suggestions.toString())
                        model.addAll(suggestions)
                        pathAction.showPopup()
                    }
                }
            }
            firstTimeQuery = false
            actions.add(pathAction)
        }
        actions.add(SelectServerAction(onChanged = {
           // pathAction?. = sshService.searchServers.firstOrNull()?.lastSearchPath ?: ""
            onChanged.run()
        }, project))
        actions.add(com.connecthid.intellij.ui.filemanager.sftp.search.actions.PreviewAction())
        return actions
    }

    override fun fetchElements(
        pattern: String,
        indicator: ProgressIndicator,
        consumer: Processor<in SftpFile>
    ) {
        sshService.searchServers.forEach {
            sshService.getConnection(it.stmpName)?.fileSystem?.searchFiles(pattern,it.lastSearchPath)?.forEach {
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

    override fun createRightActions(
        registerShortcut: (AnAction) -> Unit,
        onChanged: Runnable
    ): List<AnAction> {
        return listOf(
            CaseSensitiveAction(case, registerShortcut, onChanged),
            WordAction(word, registerShortcut, onChanged),
            RegexpAction(regexp, registerShortcut, onChanged)
        )
    }


    companion object {
        // Standard sort weight for virtual file contributors in JetBrains IDEs is 400
        const val VIRTUAL_FILE_SORT_WEIGHT = 200
    }



}
