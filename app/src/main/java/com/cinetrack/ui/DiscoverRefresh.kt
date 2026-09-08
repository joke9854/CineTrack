package com.cinetrack.ui

import kotlinx.coroutines.withTimeout

/** Discover refresh owns only Discover rails; library/schedule refresh belongs to Sync. */
internal suspend fun runDiscoverRefresh(refreshRails: suspend () -> Unit) {
    withTimeout(45_000) { refreshRails() }
}
