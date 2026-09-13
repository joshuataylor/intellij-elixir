package org.elixir_lang.inspection;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.elixir_lang.annotator.Injection;
import org.elixir_lang.psi.*;
import org.elixir_lang.psi.operation.Infix;
import org.elixir_lang.psi.operation.Prefix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by kadie.enheduanna.inanna on 12/5/14.
 */
public final class NoParenthesesManyStrict extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "Ambiguous nested calls";
    }

    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "Elixir";
    }

    @NotNull
    @Override
    public String getShortName() {
        return "NoParenthesesManyStrict";
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        ProblemsHolder problemsHolder = new ProblemsHolder(manager, file, isOnTheFly);
        checkFile(file, problemsHolder);
        return problemsHolder.getResultsArray();
    }

    private static void checkFile(final PsiFile file, final ProblemsHolder problemsHolder) {
        if (Injection.Companion.of(file) == Injection.UNCOMPILED) {
            return;
        }

        file.accept(
                new PsiRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitElement(@NotNull PsiElement element) {
                        String message = message(element);

                        if (message != null) {
                            PsiElement comma = ambiguousComma(element);

                            if (comma != null) {
                                problemsHolder.registerProblem(
                                        element,
                                        message,
                                        ProblemHighlightType.ERROR,
                                        comma.getTextRange().shiftLeft(element.getTextRange().getStartOffset())
                                );
                            }
                        }

                        super.visitElement(element);
                    }
                }
        );
    }

    @Nullable
    private static String message(@NotNull PsiElement element) {
        if (!isAmbiguous(element)) {
            return null;
        }

        String message;

        if (isContainerElement(element)) {
            message = "unexpected comma. Parentheses are required to solve ambiguity inside containers.";
        } else if (isLaterArgument(element)) {
            message = "unexpected comma. Parentheses are required to solve ambiguity in nested calls.";
        } else {
            return null;
        }

        // Elixir's parser stops at the innermost ambiguity, so one holding another is not reported.
        return containsAmbiguity(element) ? null : message;
    }

    private static boolean isContainerElement(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();

        return parent instanceof ElixirList ||
                parent instanceof ElixirTuple ||
                parent instanceof ElixirBitString ||
                parent instanceof ElixirMultipleAliases ||
                parent instanceof ElixirBracketArguments ||
                parent instanceof ElixirKeywordPair;
    }

    /**
     * A call without parentheses taking more than one argument, or an expression ending in one, where Elixir's parser
     * folds every later comma into that call. A keyword value of another call without parentheses may be one.
     */
    private static boolean isAmbiguous(@Nullable PsiElement element) {
        // A call with its own `do` block is a block expression to Elixir's parser.
        if (PsiTreeUtil.getChildOfType(element, ElixirDoBlock.class) != null) {
            return false;
        }

        if (element instanceof ElixirNoParenthesesManyStrictNoParenthesesExpression ||
                element instanceof ElixirUnqualifiedNoParenthesesManyArgumentsCall) {
            return true;
        }

        if (element instanceof Infix) {
            return isAmbiguous(((Infix) element).rightOperand());
        }

        if (element instanceof Prefix) {
            return isAmbiguous(((Prefix) element).operand());
        }

        if (element != null) {
            ElixirNoParenthesesOneArgument argument = PsiTreeUtil.getChildOfType(element, ElixirNoParenthesesOneArgument.class);

            if (argument != null) {
                PsiElement[] arguments = argument.getChildren();

                return arguments.length > 1 || (arguments.length == 1 && isAmbiguous(arguments[0]));
            }
        }

        return false;
    }

    private static boolean isLaterArgument(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();

        if (!(parent instanceof ElixirNoParenthesesOneArgument ||
                parent instanceof ElixirParenthesesArguments ||
                parent instanceof ElixirUnqualifiedNoParenthesesManyArgumentsCall)) {
            return false;
        }

        int index = 0;

        for (PsiElement argument : parent.getChildren()) {
            if (argument instanceof ElixirIdentifier || argument instanceof ElixirDoBlock) {
                continue;
            }

            if (argument == element) {
                return index > 0;
            }

            index++;
        }

        return false;
    }

    private static boolean containsAmbiguity(@NotNull PsiElement element) {
        return !PsiTreeUtil.processElements(
                element,
                descendant -> descendant == element ||
                        !(isAmbiguous(descendant) && (isContainerElement(descendant) || isLaterArgument(descendant)))
        );
    }

    @Nullable
    private static PsiElement ambiguousComma(@Nullable PsiElement element) {
        if (element instanceof ElixirNoParenthesesManyStrictNoParenthesesExpression) {
            return ambiguousComma(PsiTreeUtil.getChildOfType(element, ElixirUnqualifiedNoParenthesesManyArgumentsCall.class));
        }

        if (element instanceof ElixirUnqualifiedNoParenthesesManyArgumentsCall) {
            return firstComma(element);
        }

        if (element instanceof Infix) {
            return ambiguousComma(((Infix) element).rightOperand());
        }

        if (element instanceof Prefix) {
            return ambiguousComma(((Prefix) element).operand());
        }

        if (element != null) {
            ElixirNoParenthesesOneArgument argument = PsiTreeUtil.getChildOfType(element, ElixirNoParenthesesOneArgument.class);

            if (argument != null) {
                PsiElement[] arguments = argument.getChildren();

                if (arguments.length > 1) {
                    return firstComma(argument);
                } else if (arguments.length == 1) {
                    return ambiguousComma(arguments[0]);
                }
            }
        }

        return null;
    }

    @Nullable
    private static PsiElement firstComma(@NotNull PsiElement parent) {
        for (ASTNode child = parent.getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == ElixirTypes.COMMA) {
                return child.getPsi();
            }
        }

        return null;
    }
}
