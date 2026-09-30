package org.elixir_lang.psi.impl.call

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.elixir_lang.PlatformTestCase
import org.elixir_lang.psi.Module
import org.elixir_lang.psi.NamedElement
import org.elixir_lang.psi.call.Call
import org.elixir_lang.psi.call.StubBased
import org.elixir_lang.psi.stub.index.ModularName

class StubBackedCallTest : PlatformTestCase() {
    /** Loading the tree drops the stub, so the module name must be the first thing asked. */
    fun testQualifiedCallReadsItsModuleNameFromTheStub() {
        val call = stubBackedFoo()

        assertEquals("Kernel", call.resolvedModuleName())
    }

    fun testStubBackedQualifiedDefmoduleIsAModule() {
        val call = stubBackedFoo()

        assertTrue(Module.`is`(call))
    }

    private fun stubBackedFoo(): Call {
        myFixture.addFileToProject("foo.ex", "Kernel.defmodule Foo do\nend\n")
        val call = StubIndex.getElements(
            ModularName.KEY, "Foo", project, GlobalSearchScope.allScope(project), NamedElement::class.java
        ).single()

        assertNotNull("the tree is loaded, so this test checks nothing", (call as StubBased<*>).stub)

        return call as Call
    }
}
