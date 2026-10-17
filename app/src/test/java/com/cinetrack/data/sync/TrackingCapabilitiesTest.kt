package com.cinetrack.data.sync

import com.cinetrack.domain.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCapabilitiesTest {
    private fun operation(type: SyncOperationType) = SyncOperation(
        id = "test",
        type = type,
        mediaType = MediaType.TV,
        mediaId = 1,
        title = "Show",
        sourceVersion = 1L,
    )

    @Test
    fun episodePushCanBeSupportedWithoutRemoteRemoval() {
        val capabilities = TrackingCapabilities(
            supportsWatchHistory = true,
            supported = setOf(TrackingCapability.PUSH_EPISODE_HISTORY),
        )
        assertTrue(capabilities.supports(operation(SyncOperationType.EPISODE_WATCHED)))
        assertFalse(capabilities.supports(operation(SyncOperationType.MEDIA_HISTORY_REMOVE)))
    }

    @Test
    fun libraryOnlyProviderIsOutboundOnly() {
        val capabilities = TrackingCapabilities(
            supportsTwoWaySync = false,
            supported = setOf(TrackingCapability.PUSH_LIBRARY),
        )
        assertTrue(capabilities.supports(operation(SyncOperationType.LIBRARY_STATUS)))
        assertFalse(capabilities.supports(TrackingCapability.PULL_LIBRARY))
    }

    @Test
    fun ratingsRequireGranularCapability() {
        val capabilities = TrackingCapabilities(
            supportsRatings = true,
            supported = setOf(TrackingCapability.PUSH_LIBRARY),
        )
        assertFalse(capabilities.supports(operation(SyncOperationType.SET_RATING)))
    }
}

