package com.cinetrack.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run against a configured, representative library; never embeds API keys or fake profiles. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startup() = rule.collect(
        packageName = "com.cinetrack",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 10_000))
        device.waitForIdle()
    }

    @Test fun progressAndDetail() {
        val args = InstrumentationRegistry.getArguments()
        val mediaId = requireNotNull(args.getString("mediaId")) {
            "Pass -Pandroid.testInstrumentationRunnerArguments.mediaId=<cached TMDB id>; see baselineprofile/README.md"
        }
        val mediaType = args.getString("mediaType") ?: "TV"
        val mediaTitle = requireNotNull(args.getString("mediaTitle")) {
            "Pass mediaTitle matching a cached title and complete onboarding before profiling."
        }
        rule.collect(packageName = "com.cinetrack") {
            startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("cinetrack://app/progress")).setPackage(packageName))
            check(device.wait(Until.hasObject(By.textContains(mediaTitle)), 15_000)) {
                "Expected a configured Progress library containing $mediaTitle"
            }
            startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("cinetrack://detail/$mediaType/$mediaId")).setPackage(packageName))
            check(device.wait(Until.hasObject(By.textContains(mediaTitle)), 15_000))
            device.waitForIdle()
        }
    }
}
