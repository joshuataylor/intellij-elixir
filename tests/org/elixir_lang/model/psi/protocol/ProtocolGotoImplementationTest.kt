package org.elixir_lang.model.psi.protocol

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.ImplementationSearcher
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.Implementation
import org.elixir_lang.psi.call.Call

/** Go To Implementation (`Ctrl+Alt+B`) on a protocol's name lists its `defimpl`s. */
class ProtocolGotoImplementationTest : PlatformTestCase() {
    override fun getTestDataPath(): String = "testData/org/elixir_lang/model/psi/protocol"

    override fun setUp() {
        super.setUp()
        HeadlessDataManager.fallbackToProductionDataManager(myFixture.testRootDisposable)
    }

    fun testGotoImplementationOnDefprotocolNameFindsDefimpl() =
        assertFindsOneDefimpl("goto_implementation_protocol_name.ex")

    fun testGotoImplementationOnQualifiedDefprotocolNameFindsDefimpl() =
        assertFindsOneDefimpl("goto_implementation_qualified_protocol_name.ex")

    fun testGotoImplementationOnDefimplProtocolAliasFindsDefimpl() =
        assertFindsOneDefimpl("goto_implementation_defimpl_protocol_alias.ex")

    fun testGotoImplementationOnCallQualifierFindsDefimpl() =
        assertFindsOneDefimpl("goto_implementation_call_qualifier.ex")

    private fun assertFindsOneDefimpl(file: String) {
        myFixture.configureByFiles(file)

        val source = TargetElementUtil.getInstance()
            .findTargetElement(myFixture.editor, ImplementationSearcher.getFlags(), myFixture.caretOffset)
        assertNotNull("Go To Implementation must resolve a source element", source)

        val implementations = DefinitionsScopedSearch.search(source!!).findAll()

        assertEquals(1, implementations.count { it is Call && Implementation.`is`(it) })
    }
}
