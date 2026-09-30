package org.elixir_lang.reference.resolver.atom.resolvable

import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import com.intellij.psi.stubs.StubIndex
import org.elixir_lang.psi.ElixirAtom
import org.elixir_lang.psi.NamedElement
import org.elixir_lang.Module
import org.elixir_lang.psi.stub.index.ModularName
import org.elixir_lang.reference.resolver.atom.Resolvable
import org.elixir_lang.reference.resolver.narrowedScope
import java.util.function.Predicate
import java.util.regex.Pattern

class Pattern(private val predicate: Predicate<String>) : Resolvable() {
    constructor(regex: String) : this(Pattern.compile(regex)) {}
    constructor(pattern: Pattern) : this(pattern.asMatchPredicate()) {}

    override fun resolve(element: ElixirAtom): Array<ResolveResult> {
        val project = element.project
        val stubIndex = StubIndex.getInstance()
        val scope = narrowedScope(element, project)
        val names = mutableListOf<String>()
        stubIndex.processAllKeys(ModularName.KEY, project) { name ->
            if (Module.atom(name)?.let(predicate::test) == true) names.add(name)

            true
        }

        val resolveResults = mutableListOf<ResolveResult>()

        for (name in names) {
            stubIndex.processElements(ModularName.KEY, name, project, scope, NamedElement::class.java) {
                resolveResults.add(PsiElementResolveResult(it, false))

                true
            }
        }

        return resolveResults.toTypedArray()
    }
}
