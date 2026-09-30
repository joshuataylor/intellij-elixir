package org.elixir_lang.lowering

import com.intellij.openapi.application.ReadAction
import org.elixir_lang.language_level.ElixirLanguageLevel
import org.elixir_lang.parser_definition.ParsingTestCase
import org.elixir_lang.psi.ElixirFile

/** Lowers snippets at chosen language levels, so each leg's metadata is checked whichever Elixir runs the tests. */
abstract class LoweringTestCase : ParsingTestCase() {
    protected fun lower(code: String, elixirVersion: String = NEWEST): ElixirAst {
        val file = createPsiFile(getTestName(false), code) as ElixirFile

        return ReadAction.computeBlocking<ElixirAst, Throwable> {
            Lowering.lower(file, ElixirLanguageLevel.of(elixirVersion))
        }
    }

    /**
     * [code] lowers, at each version in [expected], to the paired term, printed by [inspect] with columns and token
     * metadata.
     */
    protected fun assertLowers(code: String, vararg expected: Pair<String, String>) =
        assertEquals(
            expected.joinToString("\n") { (version, term) -> "$version: $term" },
            expected.joinToString("\n") { (version, _) ->
                "$version: " + inspect(lower(code, version).toOtp(COLUMNS_AND_TOKEN_METADATA))
            }
        )

    /** [code] lowers to [expected] on the oldest and newest supported versions. */
    protected fun assertLowers(code: String, expected: String) = assertLowers(code, OLDEST to expected, NEWEST to expected)

    companion object {
        val COLUMNS_AND_TOKEN_METADATA = ParserOptions(columns = true, tokenMetadata = true)

        const val OLDEST = "1.11.4"
        val NEWEST: String = ElixirLanguageLevel.FALLBACK.elixirVersion
    }
}
