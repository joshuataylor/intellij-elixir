package org.elixir_lang.parser_definition;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.elixir_lang.junit.SharedFixture;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * One test per {@code .ex} and {@code .exs} file under {@code ELIXIR_PARSING_CORPUS}, each parsed and quoted
 * against the reference quoter of the Elixir the leg runs.
 */
@SuppressWarnings("JUnitMalformedDeclaration") // Built only by suite().
public class ElixirLangElixirParsingTestCase extends SharedFixtureParsingTestCase<ElixirLangElixirParsingTestCase> {
    static final String CORPUS_ENVIRONMENT_VARIABLE = "ELIXIR_PARSING_CORPUS";
    private static final Path KNOWN_FAILURES =
            Path.of("testData", "org", "elixir_lang", "parser_definition", "corpus_known_failures.tsv");

    private final Path corpusRoot;
    private final KnownFailures knownFailures;

    private ElixirLangElixirParsingTestCase() {
        corpusRoot = null;
        knownFailures = null;
    }

    private ElixirLangElixirParsingTestCase(
            @NotNull SharedFixture<ElixirLangElixirParsingTestCase> fixture,
            @NotNull Path corpusRoot,
            @NotNull String relativePath,
            @NotNull KnownFailures knownFailures
    ) {
        super(fixture, relativePath);
        this.corpusRoot = corpusRoot;
        this.knownFailures = knownFailures;
    }

    public static Test suite() {
        SharedFixture<ElixirLangElixirParsingTestCase> fixture = new SharedFixture<>(ElixirLangElixirParsingTestCase::new);
        TestSuite suite = fixture.suite(ElixirLangElixirParsingTestCase.class.getName());
        String corpus = System.getenv(CORPUS_ENVIRONMENT_VARIABLE);

        if (corpus == null || corpus.isEmpty()) {
            suite.addTest(TestSuite.warning(
                    CORPUS_ENVIRONMENT_VARIABLE + " is not set. The Gradle test task sets it when " +
                            ".github/ci-versions.json declares a corpus for Elixir " + System.getenv("ELIXIR_VERSION")
            ));
            return suite;
        }

        Path corpusRoot = Path.of(corpus);
        List<String> relativePaths = sourcePaths(corpusRoot);

        if (relativePaths.isEmpty()) {
            suite.addTest(TestSuite.warning("No .ex or .exs files under " + corpusRoot));
        }

        KnownFailures knownFailures = KnownFailures.forElixirUnderTest(KNOWN_FAILURES);

        for (String relativePath : relativePaths) {
            suite.addTest(new ElixirLangElixirParsingTestCase(fixture, corpusRoot, relativePath, knownFailures));
        }

        knownFailures.checkStale(suite, relativePaths);

        return suite;
    }

    static List<String> sourcePaths(@NotNull Path corpusRoot) {
        try (Stream<Path> paths = Files.walk(corpusRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".ex") || name.endsWith(".exs");
                    })
                    .map(path -> FileUtil.toSystemIndependentName(corpusRoot.relativize(path).toString()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void check(@NotNull ElixirLangElixirParsingTestCase testCase) throws IOException {
        String name = testCase.getName();
        File file = testCase.corpusRoot.resolve(name).toFile();

        if (testCase.knownFailures.contains(name)) {
            testCase.knownFailures.expectFailure(name, () -> assertParsed(file));
        } else {
            assertParsed(file);
        }
    }

    private void assertParsed(@NotNull File file) throws IOException {
        String text = FileUtil.loadFile(file, StandardCharsets.UTF_8.name(), true).trim();

        myFile = createPsiFile(FileUtilRt.getNameWithoutExtension(file.getName()), text);
        ensureParsed(myFile);
        toParseTreeText(myFile, skipSpaces(), includeRanges());

        assertWithoutLocalError();
        assertQuotedCorrectly();
    }
}
