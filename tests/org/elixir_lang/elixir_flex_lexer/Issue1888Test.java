package org.elixir_lang.elixir_flex_lexer;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import com.intellij.util.ThrowableRunnable;
import org.elixir_lang.ElixirLexer;
import org.elixir_lang.junit.logs.UnexpectedLogs;
import org.jetbrains.annotations.NotNull;

/**
 * {@code checkCorrectRestart} is called explicitly because {@code doTest} only performs it
 * implicitly on platform 262 - see the KDoc on
 * {@link org.elixir_lang.heex.lexer.RestartabilityTest}. Without it this class's effective
 * coverage would differ across the CI legs in .github/ci-versions.json.
 */
public class Issue1888Test extends LexerTestCase {
    public void testAtom() {
        String text = """
                defmodule MyModule do
                  def my_function([:list_atom], :argument_atom)
                end
                """;

        doTest(text,
                """
                identifier ('defmodule')
                WHITE_SPACE (' ')
                Alias ('MyModule')
                WHITE_SPACE (' ')
                do ('do')
                WHITE_SPACE ('\\n  ')
                identifier ('def')
                WHITE_SPACE (' ')
                identifier ('my_function')
                <zero-width-call> ('')
                ( ('(')
                [ ('[')
                : (':')
                A-Z, a-z, _, @, 0-9. ?, ! ('list_atom')
                ] (']')
                , (',')
                WHITE_SPACE (' ')
                : (':')
                A-Z, a-z, _, @, 0-9. ?, ! ('argument_atom')
                ) (')')
                WHITE_SPACE ('\\n')
                end ('end')
                \\\\n, \\\\r\\\\n ('\\n')""");
        checkCorrectRestart(text);
    }

    public void testColumn() {
        String text = """
                defmodule MyModule do
                  def my_function([:list_atom], :)
                end
                """;

        doTest(text,
                """
                identifier ('defmodule')
                WHITE_SPACE (' ')
                Alias ('MyModule')
                WHITE_SPACE (' ')
                do ('do')
                WHITE_SPACE ('\\n  ')
                identifier ('def')
                WHITE_SPACE (' ')
                identifier ('my_function')
                <zero-width-call> ('')
                ( ('(')
                [ ('[')
                : (':')
                A-Z, a-z, _, @, 0-9. ?, ! ('list_atom')
                ] (']')
                , (',')
                WHITE_SPACE (' ')
                : (':')
                ) (')')
                WHITE_SPACE ('\\n')
                end ('end')
                \\\\n, \\\\r\\\\n ('\\n')""");
        checkCorrectRestart(text);
    }

    @Override
    protected @NotNull Lexer createLexer() {
        return new ElixirLexer();
    }

    @Override
    protected @NotNull String getDirPath() {
        return "testData/org/elixir_lang/elixir_flex_lexer/issue_1888";
    }

    @Override
    protected void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
        UnexpectedLogs.failOnUnexpectedLogs(() -> super.runBare(testRunnable));
    }
}
