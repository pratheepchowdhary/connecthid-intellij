package com.connecthid.intellij.ui.filemanager.sftp.search

import com.connecthid.intellij.connection.vfs.SftpMatchInfo
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement


/**
 * Represents a PSI element for SFTP search results with text highlighting support
 */
class SftpPsiElement(
    private val psiFile: PsiFile,
    private val matchInfo: SftpMatchInfo? = null
) : FakePsiElement() {

    val DO_NOT_ADJUST_NAME_RANGE: Key<Boolean> = Key.create("UsageViewPanel.DO_NOT_ADJUST_NAME_RANGE")

    override fun getParent(): PsiElement {
        return psiFile
    }

    override fun getContainingFile(): PsiFile {
        return psiFile
    }

    override fun getName(): String? {
        matchInfo?.let {
            return psiFile.name+":${it.lineNumber}"
        }
        return psiFile.getName()
    }

    override fun getTextOffset(): Int {
        matchInfo?.let {
            return it.startOffset
        }
        return 0
    }

    override fun getTextRange(): TextRange {
        // If we have match information, use it to create a text range
        matchInfo?.let {
            return TextRange(it.startOffset,it.endOffset)
        }
        // Fallback to default range
        return psiFile.textRange ?: TextRange(0, 0)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getUserData(key: Key<T?>): T?{
        if(key.toString().equals(DO_NOT_ADJUST_NAME_RANGE.toString())){
            return true as T
        }
        else{
            return super.getUserData(key)
        }
    }
}