package org.elixir_lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.elixir_lang.declaration.SourceOrigin
import org.elixir_lang.psi.call.Call

/** The outermost call spanning exactly this origin's range, `null` once its file has changed. */
@RequiresReadLock
fun SourceOrigin.declaringCall(project: Project): Call? =
    file.takeIf { it.isValid }
        ?.let { PsiManager.getInstance(project).findFile(it) }
        ?.takeIf { it.viewProvider.modificationStamp == modificationStamp }
        ?.let { (it as? PsiCompiledFile)?.decompiledPsiFile ?: it }
        ?.let { psiFile ->
            generateSequence(psiFile.findElementAt(range.startOffset)) { it.parent }
                .takeWhile { it !is PsiFile }
                .filterIsInstance<Call>()
                .lastOrNull { it.textRange == range }
        }
