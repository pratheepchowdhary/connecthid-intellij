package com.connecthid.intellij.ui.filemanager.sftp.search

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement

class SftpPsiElement(private val psiFile: PsiFile) : FakePsiElement() {
    val DO_NOT_ADJUST_NAME_RANGE: Key<Boolean> = Key.create("UsageViewPanel.DO_NOT_ADJUST_NAME_RANGE")
    override fun getParent(): PsiElement {
        return psiFile
    }

    override fun getContainingFile(): PsiFile {
        return psiFile
    }

    override fun getName(): String? {
        return psiFile.getName()
    }

    override fun getTextOffset(): Int {
        return 5 // or another offset if you want
    }
    override fun getTextRange(): TextRange {
        val parent: PsiElement? = getParent()
        return TextRange(5,10)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getUserData(key: Key<T?>): T? {
        if (key === DO_NOT_ADJUST_NAME_RANGE) {
            return true as T
        }
        return super.getUserData(key)
    }

}