package org.elixir_lang.snapshot

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.PsiReferenceService.Hints.NO_HINTS
import com.intellij.psi.ResolveState
import org.elixir_lang.declaration.ArityKnowledge
import org.elixir_lang.declaration.Declaration
import org.elixir_lang.declaration.Declared
import org.elixir_lang.model.psi.ElixirSymbolWithUsages
import org.elixir_lang.model.psi.generic_server.GenServerHandlerTarget
import org.elixir_lang.psi.CallDefinitionClause
import org.elixir_lang.psi.Modular
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.CanonicallyNamed
import org.elixir_lang.psi.stub.type.call.Stub

/**
 * What the references in a set of files resolve to, one line per reference, from both of the plugin's reference
 * systems: `psi` for [PsiReference]s and `symbol` for [com.intellij.model.psi.PsiSymbolReference]s. Each module
 * gets a `module` line and a `definition` line per clause that the resolver sees in it.
 */
@Suppress("UnstableApiUsage")
class ResolutionSnapshot(private val root: VirtualFile) {
    private data class Row(val path: String, val offset: Int, val line: String)

    fun lines(files: Iterable<PsiFile>): List<String> =
        files
            .flatMap { file -> file.viewProvider.allFiles.flatMap(::rows) }
            .sortedWith(compareBy(Row::path, Row::offset, Row::line))
            .map(Row::line)

    private fun rows(file: PsiFile): List<Row> {
        val rows = mutableListOf<Row>()

        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                for (reference in PsiReferenceService.getService().getReferences(element, NO_HINTS)) {
                    rows += row(file, "psi", reference, reference.absoluteRange, targets(reference))
                }

                for (reference in PsiSymbolReferenceService.getService().getReferences(element)) {
                    val targets = reference.resolveReference().map(::describe).sorted()
                    rows += row(file, "symbol", reference, reference.absoluteRange, targets)
                }

                if (element is Call && Stub.isModular(element)) {
                    rows += inventory(file, element)
                }
            }
        })

        return rows
    }

    private fun row(file: PsiFile, system: String, reference: Any, range: TextRange, targets: List<String>): Row {
        val text = oneLine(range.subSequence(file.viewProvider.contents).toString())
        val resolved = targets.joinToString(" | ").ifEmpty { "nothing" }

        return row(file, range, "$system  ${reference.javaClass.simpleName}  `$text`  ->  $resolved")
    }

    private fun inventory(file: PsiFile, modular: Call): List<Row> {
        val module = (modular as? CanonicallyNamed)?.canonicalNameSet().orEmpty().sorted().joinToString(", ")
            .ifEmpty { "?" }
        val definitions = Modular.callDefinitionClauseCallSequence(modular).map { clause ->
            val description = CallDefinitionClause.declaration(clause, ResolveState.initial())?.let(::describe)
                ?: "(no declaration)  `${firstLine(clause)}`"

            row(file, clause.textRange, "definition  $module  $description")
        }

        return listOf(row(file, modular.textRange, "module  $module")) + definitions
    }

    private fun describe(declaration: Declaration): String {
        val arity = when (val arity = declaration.arity) {
            is ArityKnowledge.Exact -> "${arity.arity}"
            is ArityKnowledge.Range -> "${arity.minimum}..${arity.maximum}"
            is ArityKnowledge.Open -> "${arity.minimum}.."
            ArityKnowledge.Unknown -> "?"
        }
        val form = when (val declared = declaration.declared) {
            is Declared.Source -> declared.form.name.lowercase()
            is Declared.Compiled -> "compiled"
        }
        val capabilities = declaration.capabilities
        val time = if (capabilities.compileTime) "compile-time" else "runtime"

        return "${declaration.name}/$arity  $form  " +
            "${capabilities.visibility.name.lowercase()} ${capabilities.presentation.name.lowercase()} $time"
    }

    private fun row(file: PsiFile, range: TextRange, description: String): Row =
        Row(path(file), range.startOffset, "${location(file, range)}  $description")

    private fun targets(reference: PsiReference): List<String> =
        if (reference is PsiPolyVariantReference) {
            val (valid, invalid) = reference.multiResolve(false).partition { it.isValidResult }

            valid.map { describe(it.element) }.sorted() + invalid.map { "${describe(it.element)} (invalid)" }.sorted()
        } else {
            listOfNotNull(reference.resolve()).map(::describe)
        }

    private fun describe(element: PsiElement?): String {
        val file = element?.containingFile ?: return element.toString()
        val path = path(file)

        return "$path:${position(file, element.textRange.startOffset)} `${firstLine(element)}`"
    }

    private fun describe(symbol: Symbol): String =
        when (symbol) {
            is ElixirSymbolWithUsages -> "${location(symbol.file, symbol.range)} $symbol"
            is GenServerHandlerTarget -> "${location(symbol.file, symbol.range)} $symbol"
            else -> symbol.toString()
        }

    private fun location(file: PsiFile, range: TextRange): String = "${path(file)}:${position(file, range.startOffset)}"

    private fun path(file: PsiFile): String {
        val virtualFile = file.viewProvider.virtualFile

        return VfsUtilCore.getRelativePath(virtualFile, root)
            ?: throw AssertionError("${virtualFile.path} is outside the inputs, so its path differs between machines")
    }

    private fun position(file: PsiFile, offset: Int): String {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)!!
        val line = document.getLineNumber(offset)

        return "${line + 1}:${offset - document.getLineStartOffset(line) + 1}"
    }

    private fun firstLine(element: PsiElement): String = oneLine(element.text.lineSequence().first().trim())

    private fun oneLine(text: String): String {
        val escaped = text.replace("\n", "\\n")

        return if (escaped.length > MAX_TEXT) escaped.take(MAX_TEXT) + "..." else escaped
    }

    companion object {
        private const val MAX_TEXT = 80
    }
}
