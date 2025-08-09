package com.connecthid.intellij.ui.filemanager.sftp.search

import com.connecthid.intellij.connection.vfs.SftpFile
import com.connecthid.intellij.connection.vfs.SftpFileSystem
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.SelectPathAction
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.SelectServerAction
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.TextSearchAction
import com.connecthid.intellij.ui.filemanager.sftp.search.actions.TextSearchRightActionAction.*
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewProvider
import com.intellij.ide.actions.searcheverywhere.SearchFieldActionsContributor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.Processor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.lang.Runnable
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer


@OptIn(FlowPreview::class)
class SftpFileContributor(p01: AnActionEvent) : SearchEverywhereContributor<SftpPsiElement> , SearchEverywherePreviewProvider,SearchFieldActionsContributor,SearchEverywhereExtendedInfoProvider{
    private val project: Project = p01.project!!
    // Use lazy initialization to defer service access until actually needed
    private val sshService by lazy { project.getSSHService() }
    val word = AtomicBooleanProperty(false).apply { afterChange { //todo
                                                                           } }

    val case = AtomicBooleanProperty(false).apply { afterChange {  } }
    val regexp = AtomicBooleanProperty(false).apply { afterChange {  }}
    val findInFiles = AtomicBooleanProperty(false).apply { afterChange {  }}
    val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    val queryFlow = MutableStateFlow("")
    var firstTimeQuery = false






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

        actions.add(TextSearchAction(findInFiles, onChanged))

        actions.add(com.connecthid.intellij.ui.filemanager.sftp.search.actions.PreviewAction())


        return actions
    }



    override fun fetchElements(
        pattern: String,
        indicator: ProgressIndicator,
        consumer: Processor<in SftpPsiElement>
    ) {
        sshService.searchServers.forEach { server ->
            val connection = sshService.getConnection(server.stmpName)
            val fileSystem = connection?.fileSystem

            if (fileSystem != null) {
                if (findInFiles.get()) {
                    // Search within file contents
                    fileSystem.searchTextInFiles(pattern, server.lastSearchPath).forEach {
                        val psiFile = runReadAction {
                            SftpPsiElement(PsiManager.getInstance(project).findFile(it)!!)
                        }
                        consumer.process(psiFile)
                    }
                } else {
                    // Search by file name (existing functionality)
                    fileSystem.searchFiles(pattern, server.lastSearchPath).forEach {
                        val psiFile = runReadAction {
                            val fileSystem = (it.fileSystem as SftpFileSystem)
                            it.fileEntry = fileSystem.getFileStat(it.pathLocation)
                            SftpPsiElement(PsiManager.getInstance(project).findFile(it)!!)
                        }
                        consumer.process(psiFile)
                    }
                }
            }
        }
    }

    override fun isShownInSeparateTab(): Boolean {
        return true
    }

    override fun processSelectedItem(
        p0: SftpPsiElement,
        modifier: Int,
        searchText: String
    ): Boolean {
        val file = (p0.containingFile.virtualFile as?  SftpFile) ?: return false
        val fileSystem = (file.fileSystem as SftpFileSystem)
        return fileSystem.openFileInIDE(file)
    }

    override fun getItemDescription(element: SftpPsiElement): String? {
        return "test code"
    }



    override fun getElementsRenderer(): ListCellRenderer<in SftpPsiElement> {
        return object : ListCellRenderer<SftpPsiElement> {
            override fun getListCellRendererComponent(
                list: JList<out SftpPsiElement>,
                value: SftpPsiElement?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = JLabel()

                if (value != null) {
                    val file = (value.containingFile.virtualFile as  SftpFile)
                    label.text = file.url
                    label.icon = FileTypeManager.getInstance().getFileTypeByFileName(value.containingFile.name).icon
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

    override fun getDataForItem(element: SftpPsiElement, dataId: String): Any? {
        return super.getDataForItem(element, dataId)
    }

    override fun dispose() {
        super.dispose()
        coroutineScope.cancel()
    }



    companion object {
        // Standard sort weight for virtual file contributors in JetBrains IDEs is 400
        const val VIRTUAL_FILE_SORT_WEIGHT = 200
    }



}
