package org.elixir_lang.goto_decompiled

import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.application.ReadAction
import org.elixir_lang.psi.call.Call
import javax.swing.Icon

class Item(definer: Call) : GotoRelatedItem(definer, "Decompiled BEAM") {
    // The related-items popup's speed search asks for this on the EDT without a read lock.
    override fun getCustomContainerName(): String? =
        ReadAction.computeBlocking<String?, RuntimeException> { definerPresentation?.locationString }

    override fun getCustomIcon(): Icon? = definerPresentation?.getIcon(true)

    private val definerPresentation by lazy { definer.presentation }
}
