package org.elixir_lang.psi.scope

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IndexingTestUtil
import org.elixir_lang.beam.BeamLibraryFixture
import java.io.File

/** Fixtures shared by the tests of the call walk's cost. */
object WalkTestSupport {
    /** `Kernel` and `Kernel.SpecialForms` as decompiled source, as an SDK supplies them. */
    val DOCS_KERNEL = File("testData/org/elixir_lang/beam/decompiler/Docs")

    /** [block] with [directory] as a library of [module], removed afterwards. */
    fun <T> withLibrary(
        project: Project,
        module: Module,
        disposable: Disposable,
        name: String,
        directory: File,
        block: (VirtualFile) -> T
    ): T {
        val absolute = directory.absoluteFile
        VfsRootAccess.allowRootAccess(disposable, absolute.path)
        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(absolute)!!
        BeamLibraryFixture.addLibrary(project, module, name, listOf(root))
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        return try {
            block(root)
        } finally {
            WriteAction.runAndWait<Throwable> {
                val table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
                table.getLibraryByName(name)?.let { library ->
                    ModuleRootModificationUtil.updateModel(module) { model ->
                        model.findLibraryOrderEntry(library)?.let(model::removeOrderEntry)
                    }
                    table.removeLibrary(library)
                }
            }
        }
    }

    /** `file:line:column` of [element]. */
    fun location(element: PsiElement?): String {
        val file = element?.containingFile ?: return element.toString()
        val document = PsiDocumentManager.getInstance(element.project).getDocument(file)
            ?: return "${file.name}@${element.textOffset}"
        val offset = element.textRange.startOffset
        val line = document.getLineNumber(offset)

        return "${file.name}:${line + 1}:${offset - document.getLineStartOffset(line) + 1}"
    }
}
