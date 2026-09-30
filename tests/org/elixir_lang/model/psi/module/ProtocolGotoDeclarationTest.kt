package org.elixir_lang.model.psi.module

import com.intellij.ide.impl.HeadlessDataManager
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.code_insight.assertGotoDeclarationChosenAtCaret
import org.elixir_lang.code_insight.assertGotoDeclarationLandsIn
import org.elixir_lang.psi.Protocol

class ProtocolGotoDeclarationTest : PlatformTestCase() {
    override fun getTestDataPath(): String = "testData/org/elixir_lang/model/psi/module"

    override fun setUp() {
        super.setUp()
        HeadlessDataManager.fallbackToProductionDataManager(myFixture.testRootDisposable)
    }

    fun testCtrlClickOnDefimplProtocolAliasChoosesGotoDeclaration() {
        myFixture.configureByFiles("goto_declaration_protocol_defimpl.ex")
        myFixture.assertGotoDeclarationChosenAtCaret()
    }

    fun testCtrlClickOnCallQualifierProtocolAliasChoosesGotoDeclaration() {
        myFixture.configureByFiles("goto_declaration_protocol_call.ex")
        myFixture.assertGotoDeclarationChosenAtCaret()
    }

    fun testGoToDeclarationFromDefimplProtocolAliasNavigatesToDefprotocol() {
        myFixture.configureByFiles("goto_declaration_protocol_defimpl.ex")
        myFixture.assertGotoDeclarationLandsIn("NavigatedProtocol", "a defprotocol declaration") { Protocol.`is`(it) }
    }

    fun testGoToDeclarationFromCallQualifierProtocolAliasNavigatesToDefprotocol() {
        myFixture.configureByFiles("goto_declaration_protocol_call.ex")
        myFixture.assertGotoDeclarationLandsIn("NavigatedProtocol", "a defprotocol declaration") { Protocol.`is`(it) }
    }

    fun testGoToDeclarationFromQualifiedDefimplProtocolAliasLastSegmentNavigatesToFullProtocol() {
        myFixture.configureByFiles("goto_declaration_qualified_protocol_last_segment.ex")
        val declaration = myFixture.assertGotoDeclarationLandsIn("MyApp", "a defprotocol declaration") { Protocol.`is`(it) }
        assertEquals("MyApp.Protocol", ModuleSymbol.moduleNameText(declaration))
    }

    fun testGoToDeclarationFromProtocolAttributeInDefimplNavigatesToDefprotocol() {
        myFixture.configureByFiles("goto_declaration_protocol_attribute.ex")
        myFixture.assertGotoDeclarationLandsIn("NavigatedProtocol", "a defprotocol declaration") { Protocol.`is`(it) }
    }

    fun testGoToDeclarationFromProtocolAttributeInQualifiedDefimplNavigatesToFullProtocol() {
        myFixture.configureByFiles("goto_declaration_protocol_attribute_qualified.ex")
        val declaration = myFixture.assertGotoDeclarationLandsIn("MyApp", "a defprotocol declaration") { Protocol.`is`(it) }
        assertEquals("MyApp.Protocol", ModuleSymbol.moduleNameText(declaration))
    }

    fun testGoToDeclarationFromForAttributeNamingAProtocolNavigatesToItsDefprotocol() {
        myFixture.configureByFiles("goto_declaration_for_attribute_protocol.ex")
        myFixture.assertGotoDeclarationLandsIn("ForProtocol", "a defprotocol declaration") { Protocol.`is`(it) }
    }
}
