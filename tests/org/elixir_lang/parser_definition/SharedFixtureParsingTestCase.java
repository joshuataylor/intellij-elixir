package org.elixir_lang.parser_definition;

import com.intellij.util.ThrowableRunnable;
import org.elixir_lang.junit.SharedFixture;
import org.elixir_lang.junit.SharedFixtureHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A generated suite whose cases share one {@link SharedFixture}.
 *
 * <p>From IntelliJ 2026.2 the platform's {@code ParsingTestCase.setUp} goes through {@code MockApplication.setUp},
 * which sleeps 50 ms, while parsing one snippet takes about 1 ms.
 */
abstract class SharedFixtureParsingTestCase<T extends SharedFixtureParsingTestCase<T>> extends ParsingTestCase
        implements SharedFixtureHost<T> {
    private final @Nullable SharedFixture<T> fixture;

    /** The host, which is set up once and checks every case. */
    protected SharedFixtureParsingTestCase() {
        fixture = null;
        setName("shared fixture");
    }

    /** A case named {@code name}, checked on {@code fixture}'s host. */
    protected SharedFixtureParsingTestCase(@NotNull SharedFixture<T> fixture, @NotNull String name) {
        this.fixture = fixture;
        setName(name);
        fixture.add(self());
    }

    @Override
    public final void runShared(@NotNull ThrowableRunnable<Throwable> serve) throws Throwable {
        runBare(serve);
    }

    @Override
    protected final void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
        if (fixture == null) {
            super.runBare(testRunnable);
        } else {
            fixture.check(self());
        }
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }
}
