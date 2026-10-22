import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.roborazzi)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun secret(name: String): String =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name)
        ?: ""

fun escaped(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull

fun git(vararg args: String): String = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

// Debug and preview builds carry their own number (commit count + short commit) so a test
// can name the exact build, e.g. 0.99.64-preview.1042+96d14a6. Beta versionName/versionCode
// are untouched, and the in-app updater ignores everything after the first '-'.
// ".dirty-<hash>" marks a build made from uncommitted changes on top of that commit; the hash
// of the diff tells two such builds apart.
val devBuildTag = "${git("rev-list", "--count", "HEAD").ifBlank { "0" }}+${git("rev-parse", "--short", "HEAD").ifBlank { "nogit" }}" +
    git("diff", "HEAD").let { diff -> if (diff.isBlank()) "" else ".dirty-" + "%08x".format(diff.hashCode()).take(6) }

android {
    namespace = "com.cinetrack"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cinetrack"
        minSdk = 25
        targetSdk = 36
        versionCode = 171
        versionName = "0.99.72"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Personal credentials are entered in Settings, never compiled into APKs.
        buildConfigField("String", "TMDB_API_TOKEN", "\"\"")
        buildConfigField("String", "MDBLIST_API_KEY", "\"\"")
        buildConfigField("String", "SIMKL_CLIENT_ID", "\"${escaped(secret("SIMKL_CLIENT_ID"))}\"")
        buildConfigField("String", "SIMKL_REDIRECT_URI", "\"cinetrack://simkl\"")
        buildConfigField("String", "GITHUB_UPDATE_REPO", "\"joke9854/CineTrack\"")
        buildConfigField("String", "GITHUB_UPDATE_CHANNEL", "\"testing\"")
    }

    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug.$devBuildTag"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (!releaseKeystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release-optimised build installed over the debug app (same id and key, so its
        // data stays): previews and performance checks on a phone without the release key.
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-preview.$devBuildTag"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    // MigrationTestHelper loads exported Room schemas from the instrumentation
    // APK's assets. Keep the historical schemas alongside the generated ones
    // and package them for every connectedDebugAndroidTest run.
    // Robolectric needs merged resources/manifest on the unit-test classpath (Roborazzi screenshots).
    testOptions.unitTests.isIncludeAndroidResources = true

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    // Matches the Compose BOM PrismalAGSL 1.0.4 exposes (needs compileSdk 37, AGP 9).
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    // Crash capture only: reports go to Floppy through our own sender, never an ACRA HTTP/mail plugin.
    implementation(libs.acra.core)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.glance.appwidget)
    // Stable Haze API compatible with the app's current Compose toolchain.
    implementation(libs.haze)
    // Liquid glass (AGSL refraction, specular rim) for compact controls.
    implementation(libs.prismal.agsl)
    implementation(libs.youtube.player.core)
    implementation(libs.errorprone.annotations)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
