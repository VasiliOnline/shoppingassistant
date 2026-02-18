package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21GoldenSuiteRunnerTest {
    private val runner = Stage21GoldenSuiteRunner()

    @Test
    fun all_stage21_packages_meet_golden_thresholds() {
        val report = runner.run()
        assertTrue(report.summary(maxMissesPerPackage = 5), report.isPass)
    }
}
