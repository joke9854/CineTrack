package com.cinetrack.data.sync.simkl

import com.cinetrack.domain.LibraryStatus

/** The one mapping between Simkl list names and LibraryStatus, used by the host, engine and provider. */
internal object SimklStatusMapping {
    /** Unknown or missing Simkl lists are NONE. */
    fun toLibraryStatus(simklStatus: String): LibraryStatus = when (simklStatus) {
        "watching" -> LibraryStatus.WATCHING
        "hold" -> LibraryStatus.PAUSED
        "plantowatch" -> LibraryStatus.PLAN_TO_WATCH
        "completed" -> LibraryStatus.COMPLETED
        "dropped" -> LibraryStatus.DROPPED
        else -> LibraryStatus.NONE
    }

    /** NONE has no Simkl list: it is a remove operation. */
    fun toSimkl(status: LibraryStatus): String? = when (status) {
        LibraryStatus.WATCHING -> "watching"
        LibraryStatus.PLAN_TO_WATCH -> "plantowatch"
        LibraryStatus.PAUSED -> "hold"
        LibraryStatus.COMPLETED -> "completed"
        LibraryStatus.DROPPED -> "dropped"
        LibraryStatus.NONE -> null
    }
}

/** A Simkl list name as a LibraryStatus. */
internal fun String.fromSimklStatus(): LibraryStatus = SimklStatusMapping.toLibraryStatus(this)

/** A stored LibraryStatus name as a Simkl list name; null for NONE or an unknown name. */
internal fun String.toSimklStatus(): String? =
    LibraryStatus.entries.firstOrNull { it.name == this }?.let(SimklStatusMapping::toSimkl)

/** A stored LibraryStatus name; unknown names are NONE. */
internal fun String.fromCineTrackStatus(): LibraryStatus =
    runCatching { LibraryStatus.valueOf(this) }.getOrDefault(LibraryStatus.NONE)
