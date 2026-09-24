package org.elixir_lang.sdk;

import com.intellij.execution.ExecutionException;
import org.elixir_lang.junit.UnitTestCase;

public class ProcessOutputTest extends UnitTestCase {
    /*
     * Tests
     */

    public void testIssue521() throws ExecutionException {
        assertEquals(
                -1,
                ProcessOutput
                        .getProcessOutput(
                                1,
                                null,
                                "/exe-path"
                        )
                        .getExitCode()
        );
    }
}
