package com.cinetrack.ui

/** Main-thread state for reconciling optimistic taps with asynchronous Room reads. */
internal class PendingEpisodeEdits {
    var version: Long = 0
        private set
    private val edits = mutableMapOf<Triple<Int, Int, Int>, Boolean>()

    fun record(key: Triple<Int, Int, Int>, watched: Boolean) {
        version++
        edits[key] = watched
    }

    fun unconfirmedShows(watched: Set<Triple<Int, Int, Int>>): Set<Int> {
        edits.entries.removeAll { (key, value) -> (key in watched) == value }
        return edits.keys.map { it.first }.toSet()
    }

    operator fun get(key: Triple<Int, Int, Int>): Boolean? = edits[key]
}
