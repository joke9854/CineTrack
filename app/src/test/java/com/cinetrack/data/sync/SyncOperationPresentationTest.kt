package com.cinetrack.data.sync

import com.cinetrack.domain.SyncOperationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncOperationPresentationTest {
    private fun delivery(provider: TrackingProviderId, status: DeliveryStatus) =
        SyncOperationDelivery("op", 1L, provider, status = status, required = true)

    @Test
    fun `Simkl success and Floppy failure is partial`() {
        assertEquals(
            SyncOperationStatus.PARTIAL,
            SyncOperationStatus.PENDING.aggregateWith(
                listOf(
                    delivery(TrackingProviderId.SIMKL, DeliveryStatus.ACKNOWLEDGED),
                    delivery(TrackingProviderId.FLOPPY, DeliveryStatus.FAILED),
                ),
            ),
        )
    }

    @Test
    fun `all failed required deliveries are failed`() {
        assertEquals(
            SyncOperationStatus.FAILED,
            SyncOperationStatus.PENDING.aggregateWith(listOf(delivery(TrackingProviderId.FLOPPY, DeliveryStatus.FAILED))),
        )
    }

    @Test
    fun `pending required delivery remains pending`() {
        assertEquals(
            SyncOperationStatus.PENDING,
            SyncOperationStatus.PENDING.aggregateWith(listOf(delivery(TrackingProviderId.SIMKL, DeliveryStatus.PENDING))),
        )
    }
}

