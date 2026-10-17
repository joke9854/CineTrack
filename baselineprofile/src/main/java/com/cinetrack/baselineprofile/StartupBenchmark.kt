package com.cinetrack.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Needs a benchmark (non-debuggable) build on a physical device; see baselineprofile/README.md. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldNoCompilation() = startup(CompilationMode.None(), StartupMode.COLD)

    // UseIfAvailable: tolerates a build without a generated profile instead of failing.
    @Test fun coldPartialProfile() =
        startup(CompilationMode.Partial(BaselineProfileMode.UseIfAvailable), StartupMode.COLD)

    @Test fun warmPartialProfile() =
        startup(CompilationMode.Partial(BaselineProfileMode.UseIfAvailable), StartupMode.WARM)

    private fun startup(compilation: CompilationMode, mode: StartupMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = mode,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
