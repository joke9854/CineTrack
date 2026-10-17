package com.cinetrack.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Scrolls the Progress list. Skipped unless -P...mediaTitle names a title present in the synced library. */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun progressScrollNoCompilation() = scroll(CompilationMode.None())

    @Test fun progressScrollPartialProfile() =
        scroll(CompilationMode.Partial(BaselineProfileMode.UseIfAvailable))

    private fun scroll(compilation: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            launchProgress()
            assumeLibraryVisible(configuredMediaTitle())
        },
    ) {
        scrollMainList()
    }
}
