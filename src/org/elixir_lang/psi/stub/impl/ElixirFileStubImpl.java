package org.elixir_lang.psi.stub.impl;

import com.intellij.psi.stubs.PsiFileStubImpl;
import org.elixir_lang.beam.psi.stubs.ElixirFileStub;
import org.elixir_lang.beam.psi.stubs.ModuleStubElementTypes;
import org.elixir_lang.psi.ElixirFile;
import org.elixir_lang.psi.call.CanonicallyNamed;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.Nullable;

public class ElixirFileStubImpl extends PsiFileStubImpl<ElixirFile> implements ElixirFileStub {
    public ElixirFileStubImpl() {
        super(null);
    }

    @NotNull
    @Override
    public CanonicallyNamed[] modulars() {
        return getChildrenByType(ModuleStubElementTypes.MODULE, CanonicallyNamed[]::new);
    }

    // Narrows the platform's raw return type, which javac reports as an unchecked override.
    @Override
    public @Nullable IStubElementType<?, ?> getStubType() {
        return null;
    }
}
