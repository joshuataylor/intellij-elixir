package org.elixir_lang.junit.logs.samples

import com.intellij.openapi.diagnostic.Logger
import junit.framework.TestCase

/** A test class on a base without the check; run only by `UnexpectedLogsTest`. */
class UncheckedSample : TestCase() {
    fun testWarns() {
        Logger.getInstance(UncheckedSample::class.java).warn(SAMPLE_WARNING)
    }
}
