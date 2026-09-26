package org.elixir_lang.psi.stub;

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import org.elixir_lang.psi.ElixirFile;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.Nullable;

public class File extends PsiFileStubImpl<ElixirFile> {
    public File(ElixirFile file) {
        super(file);
    }

    @Override
    public @NotNull IStubFileElementType<?> getType() {
        return org.elixir_lang.psi.stub.type.File.INSTANCE;
    }

    // Narrows the platform's raw return type, which javac reports as an unchecked override.
    @Override
    public @Nullable IStubElementType<?, ?> getStubType() {
        return null;
    }
}
