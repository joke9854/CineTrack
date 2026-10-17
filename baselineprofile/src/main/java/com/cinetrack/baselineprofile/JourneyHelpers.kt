package com.cinetrack.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue

const val TARGET_PACKAGE = "com.cinetrack"

/** Optional library title; reused from the generator's instrumentation arguments. Never credentials. */
fun configuredMediaTitle(): String? = InstrumentationRegistry.getArguments().getString("mediaTitle")

fun MacrobenchmarkScope.launchProgress() {
    startActivityAndWait(
        Intent(Intent.ACTION_VIEW, Uri.parse("cinetrack://app/progress")).setPackage(packageName),
    )
}

/** Waits for the Progress library to show [title]; skips the benchmark (assume) when the app has no data. */
fun MacrobenchmarkScope.assumeLibraryVisible(title: String?) {
    assumeTrue("Pass mediaTitle for a configured library; see baselineprofile/README.md", title != null)
    assumeTrue(
        "App has no data for '$title' (complete onboarding and sync first)",
        device.wait(Until.hasObject(By.textContains(title!!)), 15_000),
    )
}

/** Scrolls the first scrollable container (main list) down and back up. */
fun MacrobenchmarkScope.scrollMainList() {
    val list = device.findObject(By.scrollable(true)) ?: return
    list.setGestureMargin(device.displayWidth / 10)
    repeat(3) { list.fling(Direction.DOWN) }
    device.waitForIdle()
    repeat(3) { list.fling(Direction.UP) }
    device.waitForIdle()
}
