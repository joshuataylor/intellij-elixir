package org.elixir_lang.elixir_surface

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangBinary
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangMap
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangTuple
import com.ericsson.otp.erlang.OtpOutputStream
import com.intellij.openapi.util.io.FileUtil
import org.elixir_lang.beam.BeamBytes
import org.elixir_lang.junit.UnitTestCase
import java.io.File

class MacroSurfaceTest : UnitTestCase() {
    private lateinit var ebin: File

    override fun setUp() {
        super.setUp()
        ebin = FileUtil.createTempDirectory("ebin", null)
    }

    override fun tearDown() {
        try {
            ebin.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testOneSortedLinePerMacroOfBothModules() {
        writeBeam("Kernel", docsV1(macro("unless", 2), macro("if", 2), macro("def", 2)))
        writeBeam("Kernel.SpecialForms", docsV1(macro("case", 2), macro("__block__", 1)))

        assertEquals(
            "Kernel def/2\nKernel if/2\nKernel unless/2\nKernel.SpecialForms __block__/1\nKernel.SpecialForms case/2\n",
            MacroSurface.macros(ebin)
        )
    }

    fun testFunctionsAreLeftOut() {
        writeBeam("Kernel", docsV1(entry("function", "apply", 2, markdown()), macro("if", 2)))
        writeBeam("Kernel.SpecialForms", docsV1())

        assertEquals("Kernel if/2\n", MacroSurface.macros(ebin))
    }

    fun testHiddenMacroIsMarked() {
        writeBeam("Kernel", docsV1(entry("macro", "to_char_list", 1, OtpErlangAtom("hidden")), macro("if", 2)))
        writeBeam("Kernel.SpecialForms", docsV1())

        assertEquals("Kernel if/2\nKernel to_char_list/1 hidden\n", MacroSurface.macros(ebin))
    }

    fun testUndocumentedMacroIsNotHidden() {
        writeBeam("Kernel", docsV1(entry("macro", "sigil_x", 2, OtpErlangAtom("none"))))
        writeBeam("Kernel.SpecialForms", docsV1())

        assertEquals("Kernel sigil_x/2\n", MacroSurface.macros(ebin))
    }

    fun testEachArityIsItsOwnLine() {
        writeBeam("Kernel", docsV1(macro("def", 1), macro("def", 2)))
        writeBeam("Kernel.SpecialForms", docsV1())

        assertEquals("Kernel def/1\nKernel def/2\n", MacroSurface.macros(ebin))
    }

    fun testBeamWithoutDocsFails() {
        File(ebin, "Elixir.Kernel.beam").writeBytes(BeamBytes.beam("AtU8" to ByteArray(4)))
        writeBeam("Kernel.SpecialForms", docsV1())

        val error = macrosError()

        assertTrue(error.message, error.message!!.contains("Elixir.Kernel.beam"))
    }

    fun testDocsChunkThatIsNotDocsV1Fails() {
        writeBeam("Kernel", OtpErlangTuple(arrayOf(OtpErlangAtom("docs_v2"))))
        writeBeam("Kernel.SpecialForms", docsV1())

        val error = macrosError()

        assertTrue(error.message, error.message!!.contains("Elixir.Kernel.beam"))
    }

    private fun macrosError(): AssertionError =
        try {
            MacroSurface.macros(ebin)
            throw IllegalStateException("macros did not fail")
        } catch (e: AssertionError) {
            e
        }

    private fun writeBeam(module: String, docs: OtpErlangObject) {
        val term = byteArrayOf(131.toByte()) + OtpOutputStream(docs).toByteArray()
        File(ebin, "Elixir.$module.beam").writeBytes(BeamBytes.beam("Docs" to term))
    }

    private fun docsV1(vararg entries: OtpErlangObject): OtpErlangTuple =
        OtpErlangTuple(
            arrayOf(
                OtpErlangAtom("docs_v1"),
                OtpErlangLong(1),
                OtpErlangAtom("elixir"),
                OtpErlangBinary("text/markdown".toByteArray()),
                markdown(),
                OtpErlangMap(),
                OtpErlangList(entries),
            )
        )

    private fun macro(name: String, arity: Long) = entry("macro", name, arity, markdown())

    private fun entry(kind: String, name: String, arity: Long, doc: OtpErlangObject): OtpErlangTuple =
        OtpErlangTuple(
            arrayOf(
                OtpErlangTuple(arrayOf(OtpErlangAtom(kind), OtpErlangAtom(name), OtpErlangLong(arity))),
                OtpErlangLong(1),
                OtpErlangList(arrayOf<OtpErlangObject>(OtpErlangBinary("$name(...)".toByteArray()))),
                doc,
                OtpErlangMap(),
            )
        )

    private fun markdown(): OtpErlangMap =
        OtpErlangMap(
            arrayOf<OtpErlangObject>(OtpErlangBinary("en".toByteArray())),
            arrayOf<OtpErlangObject>(OtpErlangBinary("Docs.".toByteArray()))
        )
}
