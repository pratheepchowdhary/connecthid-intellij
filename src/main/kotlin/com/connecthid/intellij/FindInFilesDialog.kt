package com.connecthid.intellij

import com.intellij.ide.actions.BigPopupUI
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import org.jetbrains.annotations.Nls
import javax.swing.ListCellRenderer


class FindInFilesDialog(project: Project) : BigPopupUI(project){


    override fun createList(): JBList<in Any> {
        TODO("Not yet implemented")
    }

    override fun createCellRenderer(): ListCellRenderer<in Any> {
        TODO("Not yet implemented")
    }

    override fun getAccessibleName(): @Nls String {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }
}
