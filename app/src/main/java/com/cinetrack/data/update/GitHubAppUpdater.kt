package com.cinetrack.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cinetrack.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AppUpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String,
    val checksumUrl: String?,
    val apkSize: Long,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val update: AppUpdateInfo) : AppUpdateState
    data class Downloading(val update: AppUpdateInfo, val progress: Float) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

sealed interface AppChangelogState {
    data object Idle : AppChangelogState
    data object Loading : AppChangelogState
    data class Available(val update: AppUpdateInfo) : AppChangelogState
    data object Empty : AppChangelogState
    data class Error(val message: String) : AppChangelogState
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("target_commitish") val targetCommitish: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0L,
)

object GitHubAppUpdater {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val current = versionParts(BuildConfig.VERSION_NAME)
            fetchLatestRelease()?.takeIf { compareVersions(versionParts(it.version), current) > 0 }
        }
    }

    /** Returns the newest release in this build's channel, including the
     * currently installed version, so About can display its notes in-app. */
    suspend fun latestRelease(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching { fetchLatestRelease() }
    }

    private fun fetchLatestRelease(): AppUpdateInfo? {
        val connection = open("https://api.github.com/repos/${BuildConfig.GITHUB_UPDATE_REPO}/releases?per_page=20")
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            connection.disconnect()
            error("GitHub returned HTTP $responseCode")
        }
        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val releases = json.decodeFromString<List<GitHubRelease>>(payload)
        return releases.asSequence()
            .filterNot { it.draft }
            .filter { release ->
                val channel = BuildConfig.GITHUB_UPDATE_CHANNEL
                channel.isBlank() || release.targetCommitish.startsWith(channel) ||
                    release.tagName.contains(channel, ignoreCase = true)
            }
            .mapNotNull { release ->
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@mapNotNull null
                val checksum = release.assets.firstOrNull {
                    it.name.equals("${apk.name}.sha256", ignoreCase = true) ||
                        it.name.endsWith(".apk.sha256", ignoreCase = true)
                }
                val version = release.tagName.removePrefix("v")
                AppUpdateInfo(
                    version = version,
                    title = release.name?.takeIf(String::isNotBlank) ?: "CineTrack $version",
                    notes = release.body.orEmpty(),
                    releaseUrl = release.htmlUrl,
                    apkUrl = apk.downloadUrl,
                    checksumUrl = checksum?.downloadUrl,
                    apkSize = apk.size,
                )
            }
            .firstOrNull()
    }

    suspend fun download(
        context: Context,
        update: AppUpdateInfo,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "CineTrack-${update.version}.apk")
            val temporary = File(directory, "${target.name}.part")
            val connection = open(update.apkUrl)
            check(connection.responseCode in 200..299) { "APK download returned HTTP ${connection.responseCode}" }
            val expectedSize = connection.contentLength.toLong().takeIf { it > 0L } ?: update.apkSize.takeIf { it > 0L }
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (expectedSize != null) onProgress((downloaded.toFloat() / expectedSize).coerceIn(0f, 1f))
                    }
                }
            }
            connection.disconnect()
            if (expectedSize != null) check(temporary.length() == expectedSize) { "The downloaded APK is incomplete" }
            update.checksumUrl?.let { checksumUrl ->
                val checksumConnection = open(checksumUrl)
                check(checksumConnection.responseCode in 200..299) { "Checksum download returned HTTP ${checksumConnection.responseCode}" }
                val expected = checksumConnection.inputStream.bufferedReader().use { it.readText() }
                    .trim().substringBefore(' ').lowercase()
                checksumConnection.disconnect()
                check(expected.matches(Regex("[a-f0-9]{64}"))) { "The release checksum is invalid" }
                val digest = MessageDigest.getInstance("SHA-256")
                temporary.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(actual == expected) { "The APK checksum does not match" }
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Could not prepare the downloaded APK" }
            onProgress(1f)
            target
        }
    }

    /** Returns false after opening Android's permission screen; the user can tap
     * Download & install again after allowing this app as an install source. */
    fun launchInstaller(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return true
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "CineTrack/${BuildConfig.VERSION_NAME}")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    }

    private fun versionParts(version: String): List<Int> =
        Regex("\\d+").findAll(version.substringBefore('-')).map { it.value.toIntOrNull() ?: 0 }.toList()

    private fun compareVersions(left: List<Int>, right: List<Int>): Int {
        repeat(maxOf(left.size, right.size)) { index ->
            val comparison = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }
}
