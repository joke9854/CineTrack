package com.cinetrack.data.remote

import kotlinx.serialization.json.Json

/** Shared Json configurations, so each one is built once. */
object AppJson {
    /** Ignores keys this app does not know. */
    val tolerant = Json { ignoreUnknownKeys = true }

    /** Network DTOs: unknown keys ignored, absent nulls allowed, bad values fall back to defaults. */
    val network = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
}
