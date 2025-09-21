package com.connecthid.intellij.ui.filemanager.sftp.search

import com.connecthid.intellij.connection.sftp.SftpFile
import com.connecthid.intellij.connection.sftp.SftpFileSystem
import com.connecthid.intellij.connection.sftp.openFileInIDE
import com.connecthid.intellij.getSSHService
import com.connecthid.intellij.models.Server
import com.connecthid.intellij.models.SftpFileOccurrence
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
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import com.intellij.util.Processor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import org.jetbrains.annotations.Nls
import java.lang.Runnable
import javax.swing.ListCellRenderer


@OptIn(FlowPreview::class)
class SftpFileContributor(p01: AnActionEvent) : SearchEverywhereContributor<SftpPsiElement> , SearchEverywherePreviewProvider,SearchFieldActionsContributor,SearchEverywhereExtendedInfoProvider{
    private val project: Project = p01.project!!
    // Use lazy initialization to defer service access until actually needed
    private val sshService by lazy { getSSHService() }
    val DO_NOT_ADJUST_NAME_RANGE: Key<Boolean> = Key.create("UsageViewPanel.DO_NOT_ADJUST_NAME_RANGE")
    val word = AtomicBooleanProperty(false).apply { afterChange {
         if(getSelectedServer()!=null && getSelectedServer()?.word==it) return@afterChange
         getSelectedServer()?.word=it
         sshService.saveState()
    } }
    val case = AtomicBooleanProperty(false).apply { afterChange {
        if(getSelectedServer()!=null && getSelectedServer()?.case==it) return@afterChange
        getSelectedServer()?.case=it
        sshService.saveState()
    } }
    val regexp = AtomicBooleanProperty(false).apply { afterChange {
        if(getSelectedServer()!=null && getSelectedServer()?.regexp==it) return@afterChange
        getSelectedServer()?.regexp=it
        sshService.saveState()
    }}
    val findInFiles = AtomicBooleanProperty(false).apply { afterChange {
        if(getSelectedServer()!=null && getSelectedServer()?.findInFiles==it) return@afterChange
        getSelectedServer()?.findInFiles=it
        sshService.saveState()
    }}
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
        getSelectedServer()?.let { server ->
            word.set(server.word)
            case.set(server.case)
            regexp.set(server.regexp)
            findInFiles.set(server.findInFiles)
        }
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
                    val suggestions = sshService.getConnection(server.stmpName)?.fileSystem?.listFolderPaths(server,currentPath)
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
            onChanged.run()
        }, project))

        actions.add(TextSearchAction(findInFiles, onChanged))

        actions.add(com.connecthid.intellij.ui.filemanager.sftp.search.actions.PreviewAction())


        return actions
    }

    fun getSelectedServer(): Server? {
        return sshService.searchServers.firstOrNull()
    }





    override fun fetchElements(
        pattern: String,
        indicator: ProgressIndicator,
        consumer: Processor<in SftpPsiElement>
    ) {


        sshService.searchServers.forEach { server ->
            val connection = sshService.getConnection(server.stmpName)
            val wordFlag = server.word
            val caseFlag = server.case
            val regexpFlag = server.regexp
            val fileSystem = connection?.fileSystem

            if (fileSystem != null) {
                if (findInFiles.get()) {
                    // Search within file contents and handle multiple occurrences
                    val fileOccurrences: List<SftpFileOccurrence> = fileSystem.searchTextInFiles(server,
                        pattern,
                        server.lastSearchPath,
                        wordFlag,
                        caseFlag,
                        regexpFlag
                    )

                    for (fileOccurrence in fileOccurrences) {
                        val file = fileOccurrence.file

                        // Create a PSI file once for this file
                        val psiFile = runReadAction {
                            PsiManager.getInstance(project).findFile(file)
                        } ?: continue

                        // Create a separate SftpPsiElement for each match in the file
                        for (match in fileOccurrence.matches) {
                            val psiElement = SftpPsiElement(psiFile, match)
                            psiElement.putUserData(DO_NOT_ADJUST_NAME_RANGE,true)
                            consumer.process(psiElement)
                        }
                    }
                } else {
                    // Search by file name (existing functionality)
                    fileSystem.searchFiles(server,
                        pattern,
                        server.lastSearchPath,
                        wordFlag,
                        caseFlag,
                        regexpFlag
                    ).forEach {
                        val psiFile = runReadAction {
                            PsiManager.getInstance(project).findFile(it)
                        }
                        if (psiFile != null) {
                            val file =  SftpPsiElement(psiFile)
                            file.putUserData(DO_NOT_ADJUST_NAME_RANGE, true)
                            consumer.process(file)
                        }
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
        val file = (p0.containingFile.virtualFile as? SftpFile) ?: return false
        val fileSystem = (file.fileSystem as SftpFileSystem)
        // For text search results, open the file at the specific match location
        return project.openFileInIDE(file)
    }

    override fun getItemDescription(element: SftpPsiElement): String? {
        // Display match context for text search results
        val file = element.containingFile.virtualFile as? SftpFile ?: return null
        val name = file.name

        // Get the match text if available
        if (element.getName() != name) {
            // This is a text search result
            return "Found in: ${file.path}"
        }

        return file.path
    }



    override fun getElementsRenderer(): ListCellRenderer<in SftpPsiElement> {
        return SftpFileContributorItem()
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
