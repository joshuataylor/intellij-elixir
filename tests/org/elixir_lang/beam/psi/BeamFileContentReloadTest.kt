package org.elixir_lang.beam.psi

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveState
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.beam.psi.impl.CallDefinitionImpl
import org.elixir_lang.beam.psi.impl.ModuleImpl
import java.io.File
import java.io.IOException

/**
 * A `.beam` rewritten on disk keeps its `BeamFileImpl`, which then has to stop serving the stub and mirror decompiled
 * from the old bytes.
 */
class BeamFileContentReloadTest : PlatformTestCase() {
    fun testChangedBeamIsDecompiledAfresh() {
        val virtualFile = writableCopy(FIXTURE)
        val beamFile = PsiManager.getInstance(project).findFile(virtualFile) as BeamFileImpl

        assertTrue(
            "precondition: $FIXTURE decompiles to `defmodule :queue`",
            beamFile.decompiledPsiFile.text.contains("defmodule :queue do")
        )

        replaceContent(virtualFile, REPLACEMENT)

        val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
        assertTrue(
            "precondition: the decompiled document follows the replaced content to `defmodule :gb_sets`",
            document.text.contains("defmodule :gb_sets do")
        )
        assertSame(
            "precondition: $FIXTURE must keep its BeamFileImpl across the change, or a new one decompiles the new " +
                "content whether or not the old mirror was dropped",
            beamFile,
            PsiManager.getInstance(project).findFile(virtualFile)
        )

        assertEquals(
            "After $FIXTURE's content became $REPLACEMENT's, its PSI must decompile the new content, not keep " +
                "serving the mirror built from the old bytes",
            document.text,
            beamFile.decompiledPsiFile.text
        )
    }

    fun testCompiledElementHeldAcrossAContentChangeIsInvalid() {
        val virtualFile = writableCopy(FIXTURE)
        val beamFile = PsiManager.getInstance(project).findFile(virtualFile) as BeamFileImpl
        beamFile.decompiledPsiFile
        val callDefinition = (beamFile.children.single() as ModuleImpl<*>).callDefinitions().first { it.isExported }
        val name = nameArity(callDefinition)

        replaceContent(virtualFile, REPLACEMENT)

        assertFalse(
            "$name, held from before $FIXTURE's content became $REPLACEMENT's, must report itself invalid",
            callDefinition.isValid
        )
    }

    private fun writableCopy(name: String): VirtualFile {
        val directory = FileUtil.createTempDirectory("beam-content-reload", null, false)
        Disposer.register(testRootDisposable) { FileUtil.delete(directory) }
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.path)

        val copy = File(directory, name)
        File(DECOMPILER_TEST_DATA, name).copyTo(copy)

        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(copy)!!
    }

    private fun replaceContent(virtualFile: VirtualFile, name: String) {
        val replacement = File(DECOMPILER_TEST_DATA, name).readBytes()
        WriteAction.runAndWait<IOException> { virtualFile.setBinaryContent(replacement) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun nameArity(callDefinition: CallDefinitionImpl<*>): String =
        "${callDefinition.exportedName()}/${callDefinition.exportedArity(ResolveState.initial())}"

    companion object {
        private const val DECOMPILER_TEST_DATA = "testData/org/elixir_lang/beam/decompiler"
        private const val FIXTURE = "queue.beam"
        private const val REPLACEMENT = "gb_sets.beam"
    }
}
