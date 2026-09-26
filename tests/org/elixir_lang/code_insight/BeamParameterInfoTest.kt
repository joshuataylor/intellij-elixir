package org.elixir_lang.code_insight

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import org.elixir_lang.beam.BeamLibraryFixture
import org.elixir_lang.beam.BeamLibraryTestCase
import org.elixir_lang.beam.psi.BeamFileImpl
import java.io.File

/**
 * Parameter hints for calls whose definition is in a `.beam` rather than in source. The expected names are
 * the ones the decompiler shows for the first clause.
 */
class BeamParameterInfoTest : BeamLibraryTestCase() {
    override fun getTestDataPath(): String = "testData/org/elixir_lang/code_insight/parameter_info/beam"

    fun testErlangFunction() {
        assertEquals(listOf("l"), signaturesAtCaret("erlang_function.ex"))
    }

    /** `in_r/2` also resolves from `:queue.in`, and is not the function being called. */
    fun testErlangPatternParameters() {
        assertEquals(listOf("x, {[_] = erlangVariableIn, []}"), signaturesAtCaret("erlang_pattern_parameters.ex"))
    }

    fun testErlangZeroArity() {
        assertEquals(listOf("<no parameters>"), signaturesAtCaret("erlang_zero_arity.ex"))
    }

    fun testElixirFunction() {
        assertEquals(listOf("module"), signaturesAtCaret("elixir_function.ex"))
    }

    /** Each arity a default argument generates is its own definition in the `.beam`. */
    fun testElixirDefaultArguments() {
        assertEquals(
            listOf("string", "string, binding", "string, binding, %Macro.Env{} = env"),
            signaturesAtCaret("elixir_default_arguments.ex").sortedBy { it.length }
        )
    }

    /**
     * Resolution prefers a source module over a `.beam` of the same name, so the hint describes only the source
     * definition rather than both.
     */
    fun testSourcePreferredOverBeam() {
        assertEquals(
            listOf("module_or_path"),
            signaturesAtCaret("elixir_function.ex", "source_over_beam_declaration.ex")
        )
    }

    fun testSourcePreferredOverBeamWhenImported() {
        assertEquals(
            listOf("module_or_path"),
            signaturesAtCaret("source_over_beam_import.ex", "source_over_beam_declaration.ex")
        )
    }

    /** The hint is built on the EDT, where decompiling a large module takes hundreds of milliseconds. */
    fun testHintDoesNotDecompileTheModule() {
        val math = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(ebinDirectory, "math.beam"))!!
        val beamFile = ReadAction.computeBlocking<BeamFileImpl, Throwable> {
            myFixture.psiManager.findFile(math) as BeamFileImpl
        }
        assertNull("math.beam was decompiled before the hint was asked for", beamFile.cachedMirror)

        assertNotEmpty(signaturesAtCaret("undecompiled.ex"))

        assertNull("Building the hint decompiled math.beam", beamFile.cachedMirror)
    }

    fun testModuleIndexedTwiceIsDescribedOnce() {
        val copy = File(testDataPath, "ebin_copy").absoluteFile
        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, copy.path)
        val copyRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(copy)!!
        BeamLibraryFixture.addLibrary(project, myFixture.module, "beam-copy", listOf(copyRoot))

        assertEquals(1, signaturesAtCaret("undecompiled.ex").size)
    }

    fun testOpeningParenthesisPopsUpTheHint() {
        myFixture.configureByFile("auto_popup_opening_parenthesis.ex")

        val popup = myFixture.parameterInfoPopupAfterTyping('(')

        assertNotNull("Typing an opening parenthesis should pop up the parameter hint", popup)
        assertEquals(listOf("l"), popup!!.signatures)
        assertEquals(0, popup.currentParameterIndex)
    }

    fun testCommaPopsUpTheHint() {
        myFixture.configureByFile("auto_popup_comma.ex")

        val popup = myFixture.parameterInfoPopupAfterTyping(',')

        assertNotNull("Typing a comma should pop up the parameter hint", popup)
        assertEquals(
            listOf("string", "string, binding", "string, binding, %Macro.Env{} = env"),
            popup!!.signatures.sortedBy { it.length }
        )
        assertEquals(1, popup.currentParameterIndex)
    }

    private fun signaturesAtCaret(vararg paths: String): List<String> {
        val path = paths.first()
        myFixture.configureByFiles(*paths)

        val handler = ParameterInfo()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val arguments = handler.findElementForParameterInfo(context)
        assertNotNull("No Arguments at the caret", arguments)

        handler.showParameterInfo(arguments!!, context)
        val items = context.itemsToShow
        assertFalse("No parameter hint for the call in $path", items.isNullOrEmpty())

        return items!!.map { item ->
            val uiContext = MockParameterInfoUIContext(arguments)
            uiContext.currentParameterIndex = 0
            handler.updateUI(item as Signature, uiContext)
            uiContext.text
        }
    }
}
