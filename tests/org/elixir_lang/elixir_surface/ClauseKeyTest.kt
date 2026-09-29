package org.elixir_lang.elixir_surface

import com.ericsson.otp.erlang.OtpErlangAtom
import com.ericsson.otp.erlang.OtpErlangDouble
import com.ericsson.otp.erlang.OtpErlangList
import com.ericsson.otp.erlang.OtpErlangLong
import com.ericsson.otp.erlang.OtpErlangObject
import com.ericsson.otp.erlang.OtpErlangString
import com.ericsson.otp.erlang.OtpErlangTuple
import org.elixir_lang.junit.UnitTestCase

class ClauseKeyTest : UnitTestCase() {
    fun testRenamedVariablesShareAKey() {
        val forArgs = key(tuple(atom("for"), v("Meta"), v("Args")), guard(call("is_list", v("Args"))))
        val forA = key(tuple(atom("for"), v("M"), v("A")), guard(call("is_list", v("A"))))

        assertEquals("{for,_,V1} when is_list(V1)", forArgs)
        assertEquals(forArgs, forA)
    }

    fun testLineNumbersDoNotCount() {
        val atLine = key(tuple(atom("for"), v("Meta", line = 10), v("Args", line = 10), line = 10))
        val atLineAndColumn = key(
            tuple(atom("for", line = 99), v("Meta", line = 99), v("Args", line = 99), line = anno(99, 7))
        )

        assertEquals(atLine, atLineAndColumn)
    }

    fun testSingleUseVariablesBecomeUnderscore() {
        assertEquals("{'::',_,[_,_]}", key(tuple(atom("::"), v("Meta"), list(v("Left"), v("Right")))))
    }

    fun testUnderscoreVariablesAreSingleUse() {
        assertEquals("{'::',_,[_,_]}", key(tuple(atom("::"), v("_"), list(v("_Left"), v("_")))))
    }

    fun testRepeatedVariablesNumberedByFirstAppearance() {
        assertEquals("{V1,V2,V2,V1}", key(tuple(v("B"), v("A"), v("A"), v("B"))))
    }

    fun testRepeatedVariablesDifferFromSingleUse() {
        assertFalse(key(tuple(v("A"), v("A"))) == key(tuple(v("A"), v("B"))))
    }

    fun testAliasOnTheRightIsDropped() {
        assertEquals(
            "{'__cursor__',_,_}",
            key(match(tuple(atom("__cursor__"), v("Meta"), v("Args")), v("Expr")))
        )
    }

    fun testAliasOnTheLeftIsDropped() {
        assertEquals(
            "{'__cursor__',_,_}",
            key(match(v("Expr"), tuple(atom("__cursor__"), v("Meta"), v("Args"))))
        )
    }

    fun testGuardCountsTowardUse() {
        assertEquals(
            "V1 when is_float(V1), V1 == 0.0",
            key(v("Float"), guard(call("is_float", v("Float")), op("==", v("Float"), float(0.0))))
        )
    }

    fun testGuardVariableFromAnotherArgumentIsSingleUse() {
        assertEquals("{'=',_,_} when is_list(_)", key(tuple(atom("="), v("Meta"), v("Args")), guard(call("is_list", v("E")))))
    }

    fun testGuardSequenceAlternatives() {
        assertEquals(
            "V1 when is_atom(V1); is_list(V1)",
            key(v("X"), OtpErlangList(arrayOf(guardTests(call("is_atom", v("X"))), guardTests(call("is_list", v("X"))))))
        )
    }

    fun testDifferentGuardsDiffer() {
        assertFalse(key(v("X"), guard(call("is_list", v("X")))) == key(v("X"), guard(call("is_atom", v("X")))))
    }

    fun testDifferentAtomsDiffer() {
        assertFalse(key(tuple(atom("::"), v("M"), v("A"))) == key(tuple(atom("|"), v("M"), v("A"))))
    }

    fun testAtomsQuotedAsErlangWritesThem() {
        assertEquals(
            "[{do,_},{'after',_},{'catch',_},{'else',_},{'Elixir.Kernel',_},{'__block__',_},{ok@x,_}]",
            key(
                list(
                    tuple(atom("do"), v("A")),
                    tuple(atom("after"), v("B")),
                    tuple(atom("catch"), v("C")),
                    tuple(atom("else"), v("D")),
                    tuple(atom("Elixir.Kernel"), v("E")),
                    tuple(atom("__block__"), v("F")),
                    tuple(atom("ok@x"), v("G")),
                )
            )
        )
    }

    fun testQuoteAndBackslashInAtomAreEscaped() {
        assertEquals("'a\\'b\\\\c'", key(atom("a'b\\c")))
    }

    fun testImproperListTail() {
        assertEquals(
            "[{'when',_,[_,_,_,_|_]}]",
            key(list(tuple(atom("when"), v("M"), cons(v("A"), cons(v("B"), cons(v("C"), cons(v("D"), v("T"))))))))
        )
    }

    fun testEmptyList() {
        assertEquals("{'__block__',_,[]}", key(tuple(atom("__block__"), v("M"), nil())))
    }

    fun testLiterals() {
        assertEquals(
            "{1,-2,0.5,\"a\\\"b\",<<\"bin\">>}",
            key(
                tuple(
                    form("integer", OtpErlangLong(1)),
                    op("-", form("integer", OtpErlangLong(2))),
                    float(0.5),
                    form("string", OtpErlangString("a\"b")),
                    form("bin", OtpErlangList(arrayOf(form("bin_element", form("string", OtpErlangString("bin")), OtpErlangAtom("default"), OtpErlangAtom("default"))))),
                )
            )
        )
    }

    fun testMapPattern() {
        assertEquals(
            "#{'__struct__' := V1,a := V1}",
            key(
                form(
                    "map",
                    OtpErlangList(
                        arrayOf(
                            form("map_field_exact", atom("__struct__"), v("S")),
                            form("map_field_exact", atom("a"), v("S")),
                        )
                    )
                )
            )
        )
    }

    fun testRecordPatternAndGuardFieldAccess() {
        assertEquals(
            "#elixir_ex{context=match} when _#elixir_ex.prematch == raise",
            key(
                form(
                    "record",
                    OtpErlangAtom("elixir_ex"),
                    OtpErlangList(arrayOf(form("record_field", atom("context"), atom("match")))),
                ),
                guard(op("==", form("record_field", v("E"), OtpErlangAtom("elixir_ex"), atom("prematch")), atom("raise"))),
            )
        )
    }

    fun testAliasUsedInGuardIsKept() {
        val aliased = key(match(tuple(atom("a"), v("M")), v("X")), guard(call("is_tuple", v("X"))))

        assertEquals("{a,_}=V1 when is_tuple(V1)", aliased)
        assertFalse(aliased == key(tuple(atom("a"), v("M")), guard(call("is_tuple", v("Y")))))
    }

    fun testAliasReusedInPatternIsKept() {
        val equal = key(tuple(atom("="), v("M"), list(match(tuple(v("A"), v("B"), v("C")), v("Node")), v("Node"))))
        val unequal = key(tuple(atom("="), v("M"), list(tuple(v("A"), v("B"), v("C")), v("Other"))))

        assertEquals("{'=',_,[{_,_,_}=V1,V1]}", equal)
        assertFalse(equal == unequal)
    }

    fun testUnknownFormKeepsDataTuples() {
        val foo = key(v("X"), guard(op("==", v("X"), form("fun", OtpErlangTuple(arrayOf(OtpErlangAtom("function"), OtpErlangAtom("foo"), OtpErlangLong(1)))))))
        val bar = key(v("X"), guard(op("==", v("X"), form("fun", OtpErlangTuple(arrayOf(OtpErlangAtom("function"), OtpErlangAtom("bar"), OtpErlangLong(1)))))))

        assertFalse(foo == bar)
    }

    fun testMapUpdateInGuard() {
        assertEquals(
            "V1 when V1 == V1#{a => 1}",
            key(v("M"), guard(op("==", v("M"), form("map", v("M"), OtpErlangList(arrayOf(form("map_field_assoc", atom("a"), form("integer", OtpErlangLong(1))))))))),
        )
    }

    fun testUnknownFormIgnoresNestedAnnotations() {
        assertEquals(
            key(form("foo", OtpErlangList(arrayOf(v("A", line = 1))))),
            key(form("foo", OtpErlangList(arrayOf(v("A", line = 2)))))
        )
    }

    fun testUnaryWordOperatorIsSpaced() {
        assertEquals("V1 when not is_list(V1)", key(v("X"), guard(op("not", call("is_list", v("X"))))))
    }

    fun testControlCharactersAreEscaped() {
        assertEquals("{'a\\nb',\"c\\td\"}", key(tuple(atom("a\nb"), form("string", OtpErlangString("c\td")))))
    }

    fun testRemoteCallInGuard() {
        assertEquals(
            "V1 when erlang:is_list(V1)",
            key(v("X"), guard(form("call", form("remote", atom("erlang"), atom("is_list")), OtpErlangList(arrayOf(v("X"))))))
        )
    }

    private fun key(pattern: OtpErlangObject, guards: OtpErlangList = OtpErlangList()): String =
        ClauseKey.of(pattern, guards)

    private fun anno(line: Int, column: Int): OtpErlangObject = OtpErlangTuple(arrayOf(OtpErlangLong(line.toLong()), OtpErlangLong(column.toLong())))

    private fun form(tag: String, vararg elements: OtpErlangObject, line: Any = 1): OtpErlangTuple =
        OtpErlangTuple(arrayOf(OtpErlangAtom(tag), annotation(line), *elements))

    private fun annotation(line: Any): OtpErlangObject =
        when (line) {
            is OtpErlangObject -> line
            else -> OtpErlangLong((line as Int).toLong())
        }

    private fun v(name: String, line: Any = 1) = form("var", OtpErlangAtom(name), line = line)
    private fun atom(name: String, line: Any = 1) = form("atom", OtpErlangAtom(name), line = line)
    private fun float(value: Double) = form("float", OtpErlangDouble(value))
    private fun tuple(vararg elements: OtpErlangObject, line: Any = 1) = form("tuple", OtpErlangList(elements), line = line)
    private fun nil() = form("nil")
    private fun cons(head: OtpErlangObject, tail: OtpErlangObject) = form("cons", head, tail)
    private fun list(vararg elements: OtpErlangObject): OtpErlangObject =
        elements.foldRight(nil() as OtpErlangObject) { element, tail -> cons(element, tail) }
    private fun match(left: OtpErlangObject, right: OtpErlangObject) = form("match", left, right)
    private fun op(operator: String, vararg operands: OtpErlangObject) = form("op", OtpErlangAtom(operator), *operands)
    private fun call(name: String, vararg arguments: OtpErlangObject) = form("call", atom(name), OtpErlangList(arguments))
    private fun guardTests(vararg tests: OtpErlangObject) = OtpErlangList(tests)
    private fun guard(vararg tests: OtpErlangObject) = OtpErlangList(arrayOf<OtpErlangObject>(guardTests(*tests)))
}
