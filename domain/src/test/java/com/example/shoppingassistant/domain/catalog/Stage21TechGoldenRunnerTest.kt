package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21TechGoldenRunnerTest {
    private val runner = Stage21TechGoldenRunner()

    @Test
    fun golden_runner_builds_report_and_optionally_enforces_thresholds() = runBlocking {
        val report = runner.run()
        val summary = report.summary()
        println(summary)
        val enforce = parseBoolean(
            System.getProperty(ENFORCE_PROPERTY)
                ?: System.getenv(ENFORCE_ENV),
        )

        assertTrue(summary, report.total > 0)
        assertTrue(summary, report.hitsAt1 in 0..report.total)
        assertTrue(summary, report.hitsAt3 in 0..report.total)
        assertTrue(summary, report.recallAt1 in 0.0..1.0)
        assertTrue(summary, report.recallAt3 in 0.0..1.0)

        if (enforce) {
            assertTrue(summary, report.meetsGoldenMinQueries)
            assertTrue(summary, report.meetsRecallTop1)
            assertTrue(summary, report.meetsRecallTop3)
        }
    }

    private companion object {
        const val ENFORCE_PROPERTY = "stage21.tech.golden.enforce"
        const val ENFORCE_ENV = "STAGE21_TECH_GOLDEN_ENFORCE"

        fun parseBoolean(value: String?): Boolean = when (value?.trim()?.lowercase()) {
            "true", "1", "yes" -> true
            else -> false
        }
    }
}
