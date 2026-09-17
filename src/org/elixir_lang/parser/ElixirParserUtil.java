package org.elixir_lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.elixir_lang.psi.ElixirTypes;
import org.elixir_lang.psi.quoting.QuotingDialect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers the grammar calls as external rules, {@code <<name>>}.
 * <p>
 * Must extend {@link GeneratedParserUtilBase}: GrammarKit static-imports this class into the
 * generated parser *instead of* that one, so its helpers have to stay in scope.
 */
// GrammarKit calls every external rule with the recursion level.
@SuppressWarnings("unused")
public class ElixirParserUtil extends GeneratedParserUtilBase {
    /** Set by {@code File.doParseContents}; absent for builders created by any other route. */
    public static final Key<QuotingDialect> DIALECT = Key.create("ELIXIR_PARSE_DIALECT");

    private static final TokenSet GROUP_OPENERS = TokenSet.create(
            ElixirTypes.DO,
            ElixirTypes.FN,
            ElixirTypes.INTERPOLATION_START,
            ElixirTypes.OPENING_BIT,
            ElixirTypes.OPENING_BRACKET,
            ElixirTypes.OPENING_CURLY,
            ElixirTypes.OPENING_PARENTHESIS
    );

    private static final TokenSet GROUP_CLOSERS = TokenSet.create(
            ElixirTypes.CLOSING_BIT,
            ElixirTypes.CLOSING_BRACKET,
            ElixirTypes.CLOSING_CURLY,
            ElixirTypes.CLOSING_PARENTHESIS,
            ElixirTypes.END,
            ElixirTypes.INTERPOLATION_END
    );

    /** Group delimiters the lexer also emits for a name after {@code .}, as in {@code range.end}. */
    private static final TokenSet NAMEABLE_DELIMITERS = TokenSet.create(ElixirTypes.DO, ElixirTypes.END);

    /** What {@link WordAfterNumber} rewrites, which for {@code do} and {@code end} are group delimiters. */
    private static final TokenSet REMAPPABLE = TokenSet.create(
            ElixirTypes.INVALID_BINARY_DIGITS,
            ElixirTypes.INVALID_DECIMAL_DIGITS,
            ElixirTypes.INVALID_HEXADECIMAL_DIGITS,
            ElixirTypes.INVALID_OCTAL_DIGITS
    );

    /**
     * The token at {@code steps} as the builder will see it. The remapper rewrites a token only once the builder reaches
     * it, so ahead of it {@link PsiBuilder#rawLookup} still shows invalid digits where it will show a keyword.
     * {@code File.doParseContents} installs the remapper with the {@link #DIALECT}; without one nothing is remapped.
     */
    private static @Nullable IElementType remapped(@NotNull PsiBuilder builder, int steps) {
        IElementType tokenType = builder.rawLookup(steps);

        if (!REMAPPABLE.contains(tokenType)) {
            return tokenType;
        }

        QuotingDialect dialect = builder.getUserData(DIALECT);

        return dialect == null
                ? tokenType
                : new WordAfterNumber(dialect).filter(
                        tokenType,
                        builder.rawTokenTypeStart(steps),
                        builder.rawTokenTypeStart(steps + 1),
                        builder.getOriginalText()
                );
    }

    /**
     * Whether the {@code stab} starting here can hold a stab operation: whether a {@code ->} appears at the group
     * nesting the scan starts from before a closer leaves it. Without the check, a {@code stab} holding no {@code ->}
     * parses its contents twice - once as a signature that then fails for want of {@code ->}, once as a body - and
     * since every parenthesised expression is a {@code stab}, the doubling compounds once per level of nesting.
     * <p>
     * A stab operation's signature is group-balanced, so the {@code ->} it would consume is always reached before the
     * scan leaves that nesting.
     */
    public static boolean stabOperationAhead(@NotNull PsiBuilder builder, int level) {
        int depth = 0;

        for (int steps = 0; ; steps++) {
            ProgressManager.checkCanceled();

            IElementType tokenType = remapped(builder, steps);

            if (tokenType == null) {
                return false;
            } else if (NAMEABLE_DELIMITERS.contains(tokenType) && afterDot(builder, steps)) {
                continue;
            } else if (GROUP_OPENERS.contains(tokenType)) {
                depth++;
            } else if (GROUP_CLOSERS.contains(tokenType)) {
                if (depth == 0) {
                    return false;
                }

                depth--;
            } else if (depth == 0 && tokenType == ElixirTypes.STAB_OPERATOR) {
                return true;
            }
        }
    }

    /**
     * Whether the token at {@code steps} follows a {@code .}. The lexer reaches the state that names a field or
     * function only through {@code .}, and only whitespace and comments can come between.
     */
    private static boolean afterDot(@NotNull PsiBuilder builder, int steps) {
        int back = steps - 1;
        IElementType previous = builder.rawLookup(back);

        while (previous == TokenType.WHITE_SPACE || previous == ElixirTypes.COMMENT) {
            previous = builder.rawLookup(--back);
        }

        return previous == ElixirTypes.DOT_OPERATOR;
    }

    /**
     * Whether the {@code &} just consumed is joined to what follows, making the two one capture
     * argument such as {@code &1} - see
     * {@link QuotingDialect#getRequiresAdjacentCaptureArgument()}.
     * <p>
     * Used positively by {@code captureNumericOperation} and negated by {@code nonNumeric}, which is
     * what keeps those two rules exact complements: a spaced {@code & 1} the first rejects has to be
     * accepted by the second, or it matches neither and parses as an error.
     */
    public static boolean captureArgument(@NotNull PsiBuilder builder, int level) {
        if (!dialect(builder).getRequiresAdjacentCaptureArgument()) {
            return true;
        }

        /* Whitespace is skipped lazily, so the current lexeme may still be the space itself. Asking
           for the token type forces the skip, which is what makes rawLookup(-1) meaningful here. */
        builder.getTokenType();

        return builder.rawLookup(-1) == ElixirTypes.CAPTURE_OPERATOR;
    }

    /** Whether {@code //} is the step operator - see {@link QuotingDialect#getHasStepOperator()}. */
    public static boolean stepOperator(@NotNull PsiBuilder builder, int level) {
        return dialect(builder).getHasStepOperator();
    }

    /** The characters between a heredoc's opening and its end of line, which the lexer returns as bad characters. */
    public static boolean heredocOpeningContent(@NotNull PsiBuilder builder, int level) {
        if (builder.getTokenType() != TokenType.BAD_CHARACTER) {
            return false;
        }

        while (builder.getTokenType() == TokenType.BAD_CHARACTER) {
            builder.advanceLexer();
        }

        return true;
    }

    /** {@code ...}, which the lexer returns as an identifier. */
    public static boolean ellipsis(@NotNull PsiBuilder builder, int level) {
        return builder.getTokenType() == ElixirTypes.IDENTIFIER_TOKEN && "...".equals(builder.getTokenText());
    }

    /**
     * Whether the {@code +} or {@code -} here takes the other reading from the one the lexer gave it, because the
     * dialect does not count an escaped newline as space - see
     * {@link QuotingDialect#getCountsEscapedNewlineAsSpace()}. The lexer follows the newer reading, so this is true
     * for a binary sign in {@code f -\}+newline+{@code var} and a unary one in {@code f \}+newline+{@code -var}.
     */
    public static boolean escapedNewlineSwapsDualOperator(@NotNull PsiBuilder builder, int level) {
        IElementType tokenType = builder.getTokenType();

        if (tokenType == ElixirTypes.ADDITION_OPERATOR || tokenType == ElixirTypes.SUBTRACTION_OPERATOR) {
            return !dialect(builder).getCountsEscapedNewlineAsSpace() &&
                    rawTokenStartsWith(builder, 1, '\\') &&
                    rawTokenStartsWithHorizontalSpace(builder, -1) &&
                    builder.rawLookup(-2) == ElixirTypes.IDENTIFIER_TOKEN;
        }

        if (tokenType == ElixirTypes.NEGATE_OPERATOR || tokenType == ElixirTypes.NUMBER_OR_BADARITH_OPERATOR) {
            if (dialect(builder).getCountsEscapedNewlineAsSpace()) {
                return false;
            }

            int steps = -1;
            boolean escapedNewline = false;

            while (builder.rawLookup(steps) == TokenType.WHITE_SPACE) {
                escapedNewline |= rawTokenStartsWith(builder, steps, '\\');
                steps--;
            }

            return escapedNewline &&
                    steps < -1 &&
                    rawTokenStartsWithHorizontalSpace(builder, steps + 1) &&
                    builder.rawLookup(steps) == ElixirTypes.IDENTIFIER_TOKEN;
        }

        return false;
    }

    private static QuotingDialect dialect(@NotNull PsiBuilder builder) {
        QuotingDialect dialect = builder.getUserData(DIALECT);

        return dialect != null ? dialect : QuotingDialect.getFALLBACK();
    }

    private static boolean rawTokenStartsWithHorizontalSpace(@NotNull PsiBuilder builder, int steps) {
        return rawTokenStartsWith(builder, steps, ' ') || rawTokenStartsWith(builder, steps, '\t');
    }

    private static boolean rawTokenStartsWith(@NotNull PsiBuilder builder, int steps, char character) {
        if (builder.rawLookup(steps) != TokenType.WHITE_SPACE) {
            return false;
        }

        int start = builder.rawTokenTypeStart(steps);
        CharSequence text = builder.getOriginalText();

        return start < text.length() && text.charAt(start) == character;
    }
}
