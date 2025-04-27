package com.connecthid.intellij.vfs

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection

class FileChangeWatcher(
    private val project: Project
) {

    private val watchedFileUrls = hashMapOf<String, VirtualFile>()
    private var connection: MessageBusConnection? = null
    fun openFileInEditor(file: VirtualFile) {
        // Programmatically open the file using FileEditorManager
        println("file.url: ${file.url}")
        watchedFileUrls.put(file.url,file)
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun startWatching() {
        connection = project.messageBus.connect(project)
        connection?.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                for (event in events) {
                    val changedFile = event.file ?: continue
                    if(watchedFileUrls.contains(changedFile.url)){
                        // save changes
                    }
                }
            }
        })
        connection!!.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {

               override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    watchedFileUrls.remove(file.url)
                    println("File closed: ${file.name}")
                }
        })
    }

    fun stopWatching() {
        connection?.disconnect()
    }
}
