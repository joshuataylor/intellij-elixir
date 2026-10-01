package org.elixir_lang.expander

/** The expander's empty env is the env Elixir gives the start of an empty module body, on the leg's Elixir. */
class EmptyEnvProbeTest : ProbeTestCase() {
    fun testTheEmptyEnvIsElixirsAtTheStartOfAnEmptyModuleBody() {
        val batch = harness.compile(listOf(""))
        val observation = batch.observations.single()
        val empty = Env.empty(legLevel(), legKernel)

        assertEquals(
            ProbedEnvNormaliser.render(ProbedEnvNormaliser.projected(empty)),
            ProbedEnvNormaliser.render(ProbedEnvNormaliser.observed(observation.env, batch.probeModule))
        )
    }
}
