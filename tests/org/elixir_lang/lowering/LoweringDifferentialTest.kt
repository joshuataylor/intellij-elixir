package org.elixir_lang.lowering

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import org.elixir_lang.language_level.ElixirLanguageLevelResolver
import org.elixir_lang.lowering.ElixirAst.Placeholder
import org.elixir_lang.parser_definition.ElixirLangElixirParsingTestCase
import org.elixir_lang.parser_definition.ParsingTestCase
import org.elixir_lang.psi.ElixirFile
import org.elixir_lang.psi.impl.ElixirPsiImplUtil
import java.nio.file.Path

/**
 * Lowers every file of the parser corpus and, where nothing was left unlowered, holds the lowering's terms equal to
 * today's quoting. Prints how many files that covered.
 */
class LoweringDifferentialTest : ParsingTestCase() {
    fun testLoweringQuotesLikeQuotableOnEveryFileItCovers() {
        val corpus = System.getenv(CORPUS)
        assertFalse("$CORPUS is not set; the Gradle test task sets it", corpus.isNullOrEmpty())
        val root = Path.of(corpus!!)
        val paths = ElixirLangElixirParsingTestCase.sourcePaths(root)
        assertFalse("no .ex or .exs files under $root", paths.isEmpty())

        val outcomes = paths.associateWith { path ->
            compare(path, FileUtil.loadFile(root.resolve(path).toFile(), Charsets.UTF_8.name(), true).trim())
        }
        val covered = outcomes.values.count { it != Outcome.UNCOVERED }
        println("covered $covered of ${paths.size} files")

        val differing = outcomes.filterValues { it == Outcome.DIFFERS }.keys
        assertTrue(
            "lowering and quoting differ in ${differing.size} of $covered covered files:\n  ${differing.joinToString("\n  ")}",
            differing.isEmpty()
        )
    }

    private enum class Outcome { UNCOVERED, AGREES, DIFFERS }

    private fun compare(path: String, text: String): Outcome {
        val file = createPsiFile(FileUtilRt.getNameWithoutExtension(path.substringAfterLast('/')), text) as ElixirFile
        val lowered = ReadAction.computeBlocking<ElixirAst, Throwable> {
            Lowering.lower(file, ElixirLanguageLevelResolver.languageLevelFor(file))
        }

        return when {
            lowered.hasUnlowered() -> Outcome.UNCOVERED
            lowered.toOtp() == ElixirPsiImplUtil.quote(file) -> Outcome.AGREES
            else -> Outcome.DIFFERS
        }
    }

    private fun ElixirAst.hasUnlowered(): Boolean =
        when (this) {
            is Placeholder -> when (reason) {
                is Placeholder.Reason.Unlowered -> true
            }
            is ElixirAst.Call -> callee.hasUnlowered() || arguments.orEmpty().any { it.hasUnlowered() }
            is ElixirAst.Alias -> segments.any { it.hasUnlowered() }
            is ElixirAst.Literal -> false
            is ElixirAst.ListNode -> elements.any { it.hasUnlowered() }
            is ElixirAst.Tuple -> elements.any { it.hasUnlowered() }
            is ElixirAst.Block -> expressions.any { it.hasUnlowered() }
        }

    private companion object {
        const val CORPUS = ElixirLangElixirParsingTestCase.CORPUS_ENVIRONMENT_VARIABLE
    }
}
