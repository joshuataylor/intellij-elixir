package org.elixir_lang.snapshot

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.system.OS
import org.elixir_lang.elixir_surface.LegManifest
import org.elixir_lang.elixir_surface.LegManifest.environment
import org.elixir_lang.junit.UnitTestCase
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fails when what the leg's Elixir compiler says about the resolution snapshot's inputs differs from the facts
 * committed for its version, which `generate.exs` writes, or when any committed fact is malformed or sits at a
 * position that does not spell what it names.
 */
class CompilerFactsTest : UnitTestCase() {
    fun testFactsMatchTheCompiler() {
        val elixirVersion = environment("ELIXIR_VERSION")
        val otpVersion = environment("ERLANG_VERSION")
        val generated = FileUtil.createTempDirectory("compiler-facts", null)

        try {
            generate(generated)
            assertEquals(
                "the Elixir that ran generate.exs is not the leg's",
                listOf(elixirVersion),
                generated.list().orEmpty().toList()
            )

            for (input in INPUTS.keys) {
                val actual = File(generated, "$elixirVersion/$input.facts").readText()
                LegManifest.assertMatchesFile(
                    ROOT,
                    elixirVersion,
                    "$input.facts",
                    actual,
                    LegManifest.regenerateCommand(CompilerFactsTest::class, elixirVersion, otpVersion)
                )
            }
        } finally {
            FileUtil.delete(generated)
        }
    }

    fun testEveryCommittedFactIsWellFormed() {
        val malformed = committed().flatMap { (version, facts) ->
            val lines = facts.readLines()
            val header = if (HEADER.find(lines.first())?.groupValues?.get(1) == version.name) {
                emptyList()
            } else {
                listOf("$facts:1: ${lines.first()}")
            }

            header + lines.drop(1).withIndex()
                .filterNot { (_, line) -> FACT.any { it.matches(line) } }
                .map { (index, line) -> "$facts:${index + 2}: $line" }
        }

        assertTrue("Malformed facts:\n${malformed.joinToString("\n")}", malformed.isEmpty())
    }

    fun testEveryCommittedPositionSpellsItsName() {
        val sources = mutableMapOf<File, String>()
        val misplaced = committed().flatMap { (_, facts) ->
            val input = INPUTS.getValue(facts.nameWithoutExtension)

            facts.readLines().withIndex().mapNotNull { (index, line) ->
                val (path, lineNumber, column, spells) = position(line) ?: return@mapNotNull null
                val file = File(input, path)
                val source = sources.getOrPut(file) { file.readText() }
                val text = source.lines().getOrNull(lineNumber - 1)?.drop(column - 1)

                if (text != null && spells(text, source)) null else "$facts:${index + 1}: $line"
            }
        }

        assertTrue(
            "Facts at positions that do not spell their name:\n${misplaced.joinToString("\n")}",
            misplaced.isEmpty()
        )
    }

    private fun generate(root: File) {
        val executable = if (OS.CURRENT == OS.Windows) "bin/elixir.bat" else "bin/elixir"
        val elixir = File(environment("ELIXIR_LANG_ELIXIR_PATH"), executable)
        val commandLine =
            GeneralCommandLine(elixir.path, "generate.exs", root.path).withWorkingDirectory(File(ROOT).toPath())
        val output = CapturingProcessHandler(commandLine).runProcess(TimeUnit.MINUTES.toMillis(5).toInt())

        assertFalse("generate.exs timed out:\n${output.stdout}\n${output.stderr}", output.isTimeout)
        assertEquals("generate.exs failed:\n${output.stdout}\n${output.stderr}", 0, output.exitCode)
    }

    private fun committed(): List<Pair<File, File>> =
        File(ROOT).listFiles(File::isDirectory).orEmpty().sortedBy { it.name }.flatMap { version ->
            version.listFiles { file -> file.extension == "facts" }.orEmpty().sortedBy { it.name }.map { version to it }
        }

    /** [spells] is given the source from the fact's column to the end of its line, and the whole source. */
    private data class Position(
        val path: String,
        val line: Int,
        val column: Int,
        val spells: (text: String, source: String) -> Boolean,
    )

    private fun position(line: String): Position? {
        REF.matchEntire(line)?.let { match ->
            val (path, lineNumber, column) = match.destructured
            val name = match.groupValues[6]
            val boundary = if (name.first().isLetter() || name.first() == '_') """(?![\w?!])""" else ""
            val spelling = Regex("^${Regex.escape(name)}$boundary")

            return Position(path, lineNumber.toInt(), column.toInt()) { text, _ -> spelling.containsMatchIn(text) }
        }

        ALIAS.matchEntire(line)?.let { match ->
            val (path, lineNumber, column, module) = match.destructured

            return Position(path, lineNumber.toInt(), column.toInt()) { text, source ->
                spellsAlias(text, source, module)
            }
        }

        return null
    }

    /**
     * Whether [text] starts with a name whose segments are [module]'s last ones, or whose first segment an `as:` in
     * [source] names and whose other segments are.
     */
    private fun spellsAlias(text: String, source: String, module: String): Boolean {
        val written = Regex("^$MODULE").find(text)?.value?.split(".") ?: return false
        val segments = module.split(".")

        return segments.takeLast(written.size) == written ||
            segments.takeLast(written.size - 1) == written.drop(1) &&
            Regex("""\bas:\s*${Regex.escape(written.first())}\b""").containsMatchIn(source)
    }

    companion object {
        private const val ROOT = "testData/org/elixir_lang/snapshot/compiler"
        private val INPUTS = mapOf(
            "callable_declaration" to "testData/org/elixir_lang/psi/callable_declaration",
            "inputs" to "testData/org/elixir_lang/snapshot/inputs",
        )
        private val HEADER = Regex("""^# Elixir (\S+)\.""")
        private const val MODULE = """[A-Z]\w*(?:\.[A-Z]\w*)*"""
        private const val KIND =
            "remote_function|remote_macro|imported_function|imported_macro|imported_quoted|local_function|local_macro"
        private val REF = Regex("""ref (\S+):(\d+):(\d+) ($KIND) ($MODULE|:\w+)\.(\S+)/\d+""")
        private val ALIAS = Regex("""alias (\S+):(\d+):(\d+) ($MODULE)""")
        private val FACT = listOf(
            REF,
            ALIAS,
            Regex("""def $MODULE (def|defp|defmacro|defmacrop) \S+/\d+ \d+"""),
            Regex("""diag \S+:\d+ (undefined_function \S+/\d+|undefined_type \S+/\d+|module_not_available $MODULE)"""),
        )
    }
}
