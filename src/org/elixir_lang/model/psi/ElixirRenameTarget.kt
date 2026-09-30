package org.elixir_lang.model.psi

import com.intellij.model.Pointer
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameValidationResult
import com.intellij.refactoring.rename.api.RenameValidator

/**
 * An [ElixirSymbolWithUsages] the user can rename, unless it is declared in compiled code.
 *
 * The platform offers any [RenameTarget] a symbol resolves to, and a compiled declaration has no document to edit,
 * so only the validator can refuse.
 */
@Suppress("UnstableApiUsage")
interface ElixirRenameTarget : ElixirSymbolWithUsages, RenameTarget {
    override fun createPointer(): Pointer<out ElixirRenameTarget>

    override fun validator(): RenameValidator =
        if (compiledFile != null) {
            val message = "$targetName cannot be renamed: it is declared in compiled code"

            object : RenameValidator {
                override fun validate(newName: String): RenameValidationResult = RenameValidationResult.invalid(message)
            }
        } else {
            RenameValidator.empty()
        }
}
