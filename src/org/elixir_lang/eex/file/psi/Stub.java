package org.elixir_lang.eex.file.psi;

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import org.elixir_lang.eex.file.ElementType;
import org.elixir_lang.eex.File;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.Nullable;

public class Stub extends PsiFileStubImpl<File> {
    public Stub(File file) {
        super(file);
    }

    @NotNull
    @Override
    public IStubFileElementType<?> getType() {
        return ElementType.INSTANCE;
    }

    // Narrows the platform's raw return type, which javac reports as an unchecked override.
    @Override
    public @Nullable IStubElementType<?, ?> getStubType() {
        return null;
    }
}
