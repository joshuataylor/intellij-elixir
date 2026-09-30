package org.elixir_lang.psi.stub.type.call;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.elixir_lang.module.PutAttribute;
import org.elixir_lang.module.RegisterAttribute;
import org.elixir_lang.psi.*;
import org.elixir_lang.psi.Module;
import org.elixir_lang.psi.call.Call;
import org.elixir_lang.psi.call.SyntacticCall;
import org.elixir_lang.psi.stub.call.Deserialized;
import org.elixir_lang.structure_view.element.CallDefinitionHead;
import org.elixir_lang.structure_view.element.CallDefinitionSpecification;
import org.elixir_lang.structure_view.element.Callback;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static org.elixir_lang.psi.CallDefinitionClause.enclosingModularMacroCall;

public abstract class Stub<Stub extends org.elixir_lang.psi.stub.call.Stub<Psi>,
        Psi extends org.elixir_lang.psi.call.StubBased> extends org.elixir_lang.psi.stub.type.Named<Stub, Psi> {
    /*
     * Static Methods
     */

    public Stub(@NotNull String debugName) {
        super(debugName);
    }

    @RequiresReadLock
    public static boolean isModular(Call call) {
        return isModular(SyntacticCall.of(call));
    }

    @RequiresReadLock
    public static boolean isModular(SyntacticCall call) {
        return Implementation.is(call) || Module.is(call) || Protocol.is(call);
    }

    private static boolean hasNameOrCanonicalNames(SyntacticCall call) {
        return call.name() != null || !call.canonicalNameSet().isEmpty();
    }

    private static boolean isDelegationCallDefinitionHead(SyntacticCall call) {
        return CallDefinitionHead.Companion.is(call) && CallDefinitionHead.Companion.enclosingDelegationCall(call) != null;
    }

    private static boolean isEnclosableByModular(SyntacticCall call) {
        return CallDefinitionClause.is(call) ||
                /* skip CallDefinitionHead because there can be false positives the the ancestor calls need to be
                   checked */
                CallDefinitionSpecification.Companion.is(call) ||
                // skip CallDefinitionHead because it is covered by CallDefinitionClause
                Callback.Companion.is(call);
    }

    private static boolean isNameable(SyntacticCall call) {
        return isEnclosableByModular(call) || isDelegationCallDefinitionHead(call) || isModular(call) || isQuoted(call);
    }

    private static boolean isQuoted(SyntacticCall call) {
        boolean isQuoted;

        if (ModuleAttribute.isDeclaration(call) || RegisterAttribute.is(call) || PutAttribute.is(call) ||
                Variable.isDeclaration(call)) {
            SyntacticCall enclosingModularMacroCall = enclosingModularMacroCall(call);

            if (enclosingModularMacroCall != null) {
                isQuoted = QuoteMacro.is(enclosingModularMacroCall);
            } else {
                isQuoted = false;
            }
        } else {
            isQuoted = false;
        }

        return isQuoted;
    }

    @Override
    public void serialize(@NotNull Stub stub, @NotNull StubOutputStream stubOutputStream) throws IOException {
        Deserialized.serialize(stubOutputStream, stub);
    }

    @Override
    public final boolean shouldCreateStub(ASTNode node) {
        SyntacticCall call = SyntacticCall.of((Call) node.getPsi());

        return isNameable(call) && hasNameOrCanonicalNames(call);
    }

    @Override
    public final @NotNull Stub createStub(@NotNull Psi psi, StubElement<? extends PsiElement> parentStub) {
        return createStub(SyntacticCall.of(psi), parentStub);
    }

    protected abstract Stub createStub(@NotNull SyntacticCall call, StubElement parentStub);
}
