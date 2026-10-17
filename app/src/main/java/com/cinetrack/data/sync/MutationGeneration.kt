package com.cinetrack.data.sync

import java.util.concurrent.atomic.AtomicLong

/**
 * Produces causal mutation generations that remain strictly increasing even
 * when the wall clock returns the same millisecond twice.
 */
object MutationGeneration {
    private val lastIssued = AtomicLong(0L)

    fun next(previous: Long? = null, now: Long = System.currentTimeMillis()): Long {
        val floor = maxOf(now, previous?.let { it + 1L } ?: Long.MIN_VALUE + 1L)
        return lastIssued.updateAndGet { current -> maxOf(floor, current + 1L) }
    }
}
