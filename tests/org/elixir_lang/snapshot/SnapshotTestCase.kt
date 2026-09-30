package org.elixir_lang.snapshot

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.golden.CommittedGolden

/** Copies an input directory into the project and compares what is measured over it with a committed golden. */
abstract class SnapshotTestCase : PlatformTestCase() {
    protected fun copyInputs(inputDirectory: String): Pair<VirtualFile, List<PsiFile>> {
        val root = myFixture.copyDirectoryToProject(inputDirectory, "")
        val psiManager = PsiManager.getInstance(project)
        val files = mutableListOf<PsiFile>()
        VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
            if (!virtualFile.isDirectory) files += psiManager.findFile(virtualFile)!!
            true
        }

        return root to files
    }

    protected fun assertGolden(golden: String, header: List<String>, lines: List<String>, regenerate: String) {
        val hashed = lines.filter { IDENTITY_HASH.containsMatchIn(it) }
        assertTrue(
            "Lines carry identity hashes, which differ between runs:\n${hashed.joinToString("\n")}",
            hashed.isEmpty()
        )

        val text = (header + lines).joinToString("\n", postfix = "\n")
        CommittedGolden.assertMatches("$testDataPath/$golden", text, regenerate)
    }

    override fun getTestDataPath(): String = "testData/org/elixir_lang"

    companion object {
        private val IDENTITY_HASH = Regex("""[\w$]@[0-9a-f]+(?![\w.])""")
    }
}
