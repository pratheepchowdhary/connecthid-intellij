package com.connecthid.intellij.ui.filemanager.sftp

import com.connecthid.intellij.connection.sftp.SftpFile
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import org.jetbrains.annotations.Nls

class SftpNavBarExtension : NavBarModelExtension {

    override fun getPresentableText(`object`: Any?): @Nls String? {
        return null
    }

    override fun getParent(psiElement: PsiElement?): PsiElement? {
        return null
    }

    override fun adjustElement(psiElement: PsiElement): PsiElement {
        return psiElement
    }

    override fun additionalRoots(project: Project?): Collection<VirtualFile> {
        return emptyList()
    }

    override fun processChildren(
        file: Any,
        rootElement: Any?,           // ← make nullable
        processor: Processor<in Any>
    ): Boolean {
        // If null or our SFTP file type, skip processing
        if (file is SftpFile || rootElement == null) {
            return false
        }
        return super.processChildren(file, rootElement, processor)
    }
}
