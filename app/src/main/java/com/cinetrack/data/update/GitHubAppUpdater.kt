package com.cinetrack.data.update

import com.cinetrack.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
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
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val update: AppUpdateInfo) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
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
)

object GitHubAppUpdater {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open("https://api.github.com/repos/${BuildConfig.GITHUB_UPDATE_REPO}/releases?per_page=20")
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.disconnect()
                error("GitHub returned HTTP $responseCode")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val releases = json.decodeFromString<List<GitHubRelease>>(payload)
            val current = versionParts(BuildConfig.VERSION_NAME)
            releases.asSequence()
                .filterNot { it.draft }
                .filter { release ->
                    val channel = BuildConfig.GITHUB_UPDATE_CHANNEL
                    channel.isBlank() || release.targetCommitish.startsWith(channel) || release.tagName.contains(channel, ignoreCase = true)
                }
                .mapNotNull { release ->
                    if (release.assets.none { it.name.endsWith(".apk", ignoreCase = true) }) return@mapNotNull null
                    val version = release.tagName.removePrefix("v")
                    if (compareVersions(versionParts(version), current) <= 0) return@mapNotNull null
                    AppUpdateInfo(
                        version = version,
                        title = release.name?.takeIf(String::isNotBlank) ?: "CineTrack $version",
                        notes = release.body.orEmpty(),
                        releaseUrl = release.htmlUrl,
                    )
                }
                .firstOrNull()
        }
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
