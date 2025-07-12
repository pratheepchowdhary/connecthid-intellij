package com.connecthid.intellij

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class FindInFilesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val dialog = FindInFilesDialog(project)
//        if (dialog. showAndGet()) {
//            val findModel = dialog.getFindModel()
//            // Handle the search with findModel
//        }
    }
}
