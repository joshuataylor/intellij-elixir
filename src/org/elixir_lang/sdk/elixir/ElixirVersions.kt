package org.elixir_lang.sdk.elixir

/**
 * The OTP major an Elixir build was compiled against. Recording a failed read as [None] would settle the OTP mismatch
 * check for good, so the warning would never fire again for that installation.
 */
sealed interface OtpMajor {
    data class Known(val major: String) : OtpMajor

    /** The build records no major, as every Elixir below 1.6 does. */
    data object None : OtpMajor

    /** Not read yet, or the read failed. */
    data object Unread : OtpMajor
}

val OtpMajor.knownOrNull: String?
    get() = (this as? OtpMajor.Known)?.major

/** @param elixirVersion the `vsn` in `lib/elixir/ebin/elixir.app`, verbatim, e.g. `"1.20.0-rc.0"`. */
data class ElixirVersions(val elixirVersion: String, val elixirOtpMajor: OtpMajor)
