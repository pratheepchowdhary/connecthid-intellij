package com.connecthid.intellij.ui.filemanager.sftp.search.actions



import com.intellij.util.messages.Topic

interface SEHeaderActionListener {
    fun performed(event: SearchEverywhereActionEvent)

    class SearchEverywhereActionEvent(val actionID: String)

    companion object {
        @Topic.AppLevel
        val SE_HEADER_ACTION_TOPIC = Topic(SEHeaderActionListener::class.java)
    }
}