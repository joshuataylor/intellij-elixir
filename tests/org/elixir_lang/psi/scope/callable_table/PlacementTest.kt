package org.elixir_lang.psi.scope.callable_table

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.IdempotenceChecker
import org.elixir_lang.PlatformTestCase

/**
 * Where each kind of call places its declarations: a `use`d module's quoted definitions before and inside its
 * `schema`, an imported query macro's API, a definition in an ExUnit `describe` seen from its `test`, and a definition
 * injected by an unknown macro, seen only from inside it.
 */
class PlacementTest : PlatformTestCase() {
    override fun setUp() {
        super.setUp()
        IdempotenceChecker.disableRandomChecksUntil(testRootDisposable)
    }

    fun testSchemaFunctionBeforeTheSchema() =
        assertFound("post.ex:5:26", "ecto.ex:10:7 __schema__/Exact(arity=1) USE VALID")

    fun testSchemaFunctionInsideTheSchema() =
        assertFound("post.ex:9:28", "ecto.ex:11:7 __changeset__/Exact(arity=0) USE VALID")

    fun testQueryApiFragmentInsideFrom() =
        assertFound("post.ex:12:29", "ecto.ex:39:3 fragment/Open(minimum=0) IMPORT VALID")

    fun testQueryApiCountInsideFrom() =
        assertFound("post.ex:12:52", "ecto.ex:40:3 count/Exact(arity=1) IMPORT VALID")

    fun testDescribeDefinitionFromItsTest() =
        assertFound("case_test.ex:11:7", "case_test.ex:7:5 in_describe/Exact(arity=1) OWN VALID")

    fun testUnknownMacroDefinitionFromInsideIt() =
        assertFound("unknown_macro.ex:14:21", "unknown_macro.ex:4:7 injected_helper/Exact(arity=1) USE VALID")

    fun testUnknownMacroDefinitionFromOutsideIt() =
        assertFound("unknown_macro.ex:17:20")

    /** [use]'s candidates, in both modes, with the table and walking the module, are [expected]. */
    private fun assertFound(use: String, vararg expected: String) {
        val root = myFixture.copyDirectoryToProject("psi/scope/callable_table/placement", "")
        val files = mutableListOf<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(root, null) { if (!it.isDirectory) files += it; true }
        val dump = WalkDump(project, root)

        for (table in listOf(false, true)) {
            val found = dump.lines(myFixture, files, table)
                .filter { it.startsWith("$use ") && "  found  " in it }
                .map { line ->
                    line.substringAfter("  found  ").split(" | ").filter(String::isNotEmpty).map { it.substringBefore(" via=") }
                }

            assertEquals("$use with table=$table", listOf(expected.toList(), expected.toList()), found)
        }
    }

    override fun getTestDataPath(): String = "testData/org/elixir_lang"
}
