package org.elixir_lang.quoter

import org.elixir_lang.IntellijElixir
import org.elixir_lang.PlatformTestCase

/**
 * Verifies that the Burrito quoter is working.
 */
class BurritoOTPTest : PlatformTestCase() {
    fun testOTPNode() {
        val otpNode = IntellijElixir.getLocalNode()
        assertNotNull("OTP Node is null", otpNode)
    }

}
