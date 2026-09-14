package com.cinetrack.data.sync.floppy

import java.time.Instant

/**
 * Applies one deterministic policy to Floppy's multi-consumption model.
 * Library state and play history are intentionally resolved independently.
 */
class FloppyConsumptionResolver {
    fun resolve(consumptions: List<FloppyConsumption>): Resolution {
        val completed = consumptions
            .filter(::isCompleted)
            .sortedByDescending { it.completionInstant() ?: Instant.MIN }
        val active = consumptions
            .filter(::isActive)
            .maxWithOrNull(compareBy<FloppyConsumption>({ it.progressedInstant() ?: Instant.MIN }, { it.createdInstant() ?: Instant.MIN }, { it.consumptionId }))
        return Resolution(
            active = active,
            completed = completed,
            latestCompleted = completed.firstOrNull(),
        )
    }

    fun findExactWatch(consumptions: List<FloppyConsumption>, watchedAt: Instant): FloppyConsumption? =
        resolve(consumptions).completed.firstOrNull { it.completionInstant() == watchedAt }

    fun isCompleted(consumption: FloppyConsumption): Boolean =
        consumption.status == COMPLETED_STATUS || consumption.endDate != null

    fun isActive(consumption: FloppyConsumption): Boolean =
        !isCompleted(consumption) && consumption.status in ACTIVE_STATUSES

    data class Resolution(
        val active: FloppyConsumption?,
        val completed: List<FloppyConsumption>,
        val latestCompleted: FloppyConsumption?,
    )

    private companion object {
        const val COMPLETED_STATUS = 3
        val ACTIVE_STATUSES = setOf(0, 1, 2, 4)
    }
}

private fun FloppyConsumption.completionInstant(): Instant? =
    endDate.toInstantOrNull()

private fun FloppyConsumption.progressedInstant(): Instant? = progressedAt.toInstantOrNull()

private fun FloppyConsumption.createdInstant(): Instant? = created.toInstantOrNull()

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
