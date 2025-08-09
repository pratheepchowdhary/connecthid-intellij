package com.connecthid.intellij.ui.filemanager.sftp.search

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement

class SftpPsiElement(private val psiFile: PsiFile) : FakePsiElement() {
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
        return 0 // or another offset if you want
    }
    override fun getTextRange(): TextRange {
        val parent: PsiElement? = getParent()
        return if (parent != null) parent.getTextRange() else TextRange.EMPTY_RANGE
    }


}