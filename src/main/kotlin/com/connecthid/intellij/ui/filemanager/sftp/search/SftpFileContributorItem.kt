package com.connecthid.intellij.ui.filemanager.sftp.search

import com.connecthid.intellij.connection.sftp.SftpFile
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import java.awt.Font
import javax.swing.Icon


class SftpFileContributorItem() : PsiElementListCellRenderer<SftpPsiElement>() {

    init {
        val scheme = EditorColorsManager.getInstance().getGlobalScheme()
        val editorFont = Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize())
        setFont(editorFont)
    }

    override fun getElementText(element: SftpPsiElement): String {
        return element.name ?: "<unknown>"
    }

    override fun getContainerText(element: SftpPsiElement, name: String): String? {
        val file = element.containingFile.virtualFile as? SftpFile ?: return null
        return file.parent?.path
    }


    override fun getIconFlags(): Int {
        return Iconable.ICON_FLAG_VISIBILITY
    }

    override fun getIcon(element: PsiElement): Icon {
        return FileTypeManager.getInstance().getFileTypeByFileName(element.containingFile.name).icon
    }
}
