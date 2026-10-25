package com.cinetrack.data.media

import com.cinetrack.data.remote.FanartService
import com.cinetrack.data.remote.TmdbService
import com.cinetrack.data.remote.pickLogo
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import androidx.core.content.edit
import java.util.concurrent.ConcurrentHashMap

/**
 * Title logos from Fanart.tv for the Detail page. Movies are looked up by TMDB id; shows need
 * their TVDB id, which TMDB's external ids give. Without a key nothing is requested.
 * Found logos are kept in [persisted] too, so a title opened before shows its logo at once, and
 * [prefetch] looks up recent titles in the background so most pages open with theirs ready.
 * A title with no logo is remembered for a week; a failed request is not remembered at all.
 */
class FanartRepository(
    private val fanart: () -> FanartService,
    private val tmdb: () -> TmdbService,
    private val apiKey: suspend () -> String,
    private val language: () -> String,
    private val persisted: (() -> android.content.SharedPreferences)? = null,
) {
    private val logos = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /** Reads the saved logos; call off the main thread before the first Detail page. */
    suspend fun warm() {
        val now = System.currentTimeMillis()
        persisted?.invoke()?.all?.forEach { (key, value) ->
            val text = value as? String ?: return@forEach
            if (key.startsWith(NONE)) {
                if (now - (text.toLongOrNull() ?: 0L) < NONE_TTL_MS) missing += key.removePrefix(NONE)
            } else if (text.isNotBlank()) {
                logos.putIfAbsent(key, text)
            }
        }
    }

    /**
     * Looks up the logos of [titles] one at a time (skipping known ones) and hands each new URL to
     * [fetchImage], which stores the picture in the image cache. Does nothing without a key.
     */
    suspend fun prefetch(titles: List<MediaCard>, fetchImage: suspend (String) -> Unit) {
        if (apiKey().isBlank()) return
        titles.asSequence().filter { !logos.containsKey(it.stableKey) && it.stableKey !in missing }.take(PREFETCH_MAX).forEach { media ->
            logo(media)?.let { fetchImage(it) }
        }
    }

    /** The logo already known for [media], without any request. */
    fun cached(media: MediaCard): String? = logos[media.stableKey]

    suspend fun logo(media: MediaCard): String? {
        logos[media.stableKey]?.let { return it }
        if (media.stableKey in missing) return null
        val key = apiKey().trim().takeIf(String::isNotBlank) ?: return null
        val lang = language()
        val url = try {
            if (media.type == MediaType.MOVIE) {
                val dto = fanart().movie(media.id, key)
                pickLogo(dto.hdLogos, lang) ?: pickLogo(dto.logos, lang)
            } else {
                tmdb().tvExternalIds(media.id).tvdbId?.let { tvdb ->
                    val dto = fanart().show(tvdb, key)
                    pickLogo(dto.hdLogos, lang) ?: pickLogo(dto.logos, lang)
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: retrofit2.HttpException) {
            // Fanart.tv answers 404 for a title it has no art for; anything else may work next time.
            if (error.code() != 404) return null
            null
        } catch (_: Exception) {
            return null
        }
        if (url == null) {
            missing += media.stableKey
            persisted?.invoke()?.edit { putString(NONE + media.stableKey, System.currentTimeMillis().toString()) }
        } else {
            logos[media.stableKey] = url
            persisted?.invoke()?.edit { putString(media.stableKey, url) }
        }
        return url
    }

    fun clear() {
        logos.clear()
        missing.clear()
        persisted?.invoke()?.edit { clear() }
    }

    private companion object {
        const val NONE = "none:"
        const val NONE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        /** Enough for the titles you open most; each costs one or two small requests. */
        const val PREFETCH_MAX = 40
    }
}
