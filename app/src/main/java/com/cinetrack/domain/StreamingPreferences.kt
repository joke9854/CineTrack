package com.cinetrack.domain

import java.util.Locale

/** Explicit streaming country wins; old preferences remain the default until changed. */
fun resolveProviderRegion(selected: String, content: Set<String>, metadata: String, device: String): String =
    (selected.takeUnless { it.isBlank() || it == "system" }
        ?: content.sorted().firstOrNull()
        ?: metadata.takeUnless { it.isBlank() || it == "system" }
        ?: device.ifBlank { "US" }).uppercase(Locale.ROOT)

/** Visibility is a presentation filter. Keep raw subscription availability for notifications. */
fun visibleProviderOffers(media: MediaCard): Map<String, List<String>> = linkedMapOf(
    "flatrate" to media.subscriptionProviders,
    "rent" to media.rentProviders,
    "buy" to media.buyProviders,
    "free" to media.freeProviders,
    "ads" to media.adsProviders,
).filterKeys { it in media.visibleProviderTypes }
