package com.cinetrack.data.sync

import androidx.room.withTransaction
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.PendingWriteEntity
import com.cinetrack.data.local.SyncOperationEntity
import com.cinetrack.data.local.SyncOperationDeliveryEntity
import com.cinetrack.data.local.SyncStateEntity
import com.cinetrack.data.repository.AppPreferences
import com.cinetrack.domain.MediaType
import com.cinetrack.domain.SyncOperationCard
import com.cinetrack.domain.SyncDeliveryCard
import com.cinetrack.domain.SyncOperationStatus
import kotlinx.coroutines.flow.first

interface SyncOperationRepository {
    suspend fun pending(operationIds: Set<String>? = null): List<SyncOperation>
    suspend fun enqueue(operations: List<SyncOperation>) {}
    suspend fun cards(): List<SyncOperationCard>
    suspend fun complete(operations: List<SyncOperation>)
    suspend fun fail(operations: List<SyncOperation>, error: Throwable)

    /** Provider delivery hooks. Defaults keep lightweight test repositories source-compatible. */
    suspend fun ensureDeliveries(operations: List<SyncOperation>, targets: List<SyncOperationDelivery>) {}
    suspend fun deliveries(operationIds: Set<String>): List<SyncOperationDelivery> = emptyList()
    suspend fun acknowledge(provider: TrackingProviderId, operationIds: Set<String>) {}
    suspend fun failDelivery(provider: TrackingProviderId, operationIds: Set<String>, error: Throwable) {}
    suspend fun skipUnsupported(provider: TrackingProviderId, operationIds: Set<String>) {}
    suspend fun acknowledge(provider: TrackingProviderId, operations: List<SyncOperation>) = acknowledge(provider, operations.mapTo(linkedSetOf(), SyncOperation::id))
    suspend fun failDelivery(provider: TrackingProviderId, operations: List<SyncOperation>, error: Throwable) = failDelivery(provider, operations.mapTo(linkedSetOf(), SyncOperation::id), error)
    suspend fun skipUnsupported(provider: TrackingProviderId, operations: List<SyncOperation>) = skipUnsupported(provider, operations.mapTo(linkedSetOf(), SyncOperation::id))
    suspend fun completeReady(operations: List<SyncOperation>) = complete(operations)

    /** Binds pre-delivery operations to one startup MAIN provider exactly once. */
    suspend fun backfillLegacyDeliveries() {}
}

/**
 * Adapts the existing Room queue to provider-neutral operations. The legacy
 * pending rows remain the durable payload/source-of-truth during migration.
 */
class RoomSyncOperationRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences? = null,
) : SyncOperationRepository {
    override suspend fun pending(operationIds: Set<String>?): List<SyncOperation> {
        backfillLegacyDeliveries()
        materializeLegacyOperations()
        repairBlankTitles()
        val writes = database.syncDao().pendingWrites().associateBy { "write:${it.id}" }
        val entities = database.syncDao().syncOperations().asSequence()
            .filter { it.status == SyncOperationStatus.PENDING.name || it.status == SyncOperationStatus.FAILED.name }
            .filterNot { it.operationId.startsWith("reconcile:") }
            .filter { operationIds == null || it.operationId in operationIds }
            .toList()
        return entities.filter { entity ->
            if (entity.operationId.startsWith("write:")) return@filter writes.containsKey(entity.operationId)
            if (!entity.operationId.startsWith("state:")) return@filter true
            val state = database.stateDao().get(entity.mediaType, entity.mediaId)
            state?.dirty == true && state.status == entity.localValue && state.updatedAt == entity.createdAt
        }.mapNotNull { entity -> entity.toSyncOperation(writes[entity.operationId]) }
    }

    override suspend fun cards(): List<SyncOperationCard> {
        backfillLegacyDeliveries()
        materializeLegacyOperations()
        repairBlankTitles()
        val cards = database.syncDao().syncOperations().mapNotNull(SyncOperationEntity::toCard)
        val deliveryRows = if (cards.isEmpty()) emptyList() else database.syncDao().deliveries(cards.map(SyncOperationCard::id)).map(SyncOperationDeliveryEntity::toDomain)
        return cards.map { card ->
            val deliveries = deliveryRows.filter { it.operationId == card.id }
            card.copy(
                status = card.status.aggregateWith(deliveries),
                deliveries = deliveries.map {
                    SyncDeliveryCard(it.providerId.name, it.status.name, it.required, it.attemptCount, it.lastError)
                },
            )
        }
    }

    override suspend fun enqueue(operations: List<SyncOperation>) {
        if (operations.isEmpty()) return
        val entities = operations.map { operation ->
            val title = operation.title.ifBlank {
                resolveDisplayTitle(operation.mediaType.name, operation.mediaId, operation.payload, null)
            }
            SyncOperationEntity(
                operationId = operation.id,
                operation = operation.type.name,
                mediaType = operation.mediaType.name,
                mediaId = operation.mediaId,
                title = title,
                status = SyncOperationStatus.PENDING.name,
                localValue = operation.value,
                createdAt = operation.sourceVersion,
                updatedAt = System.currentTimeMillis(),
                season = operation.payload?.episodePart(0),
                episode = operation.payload?.episodePart(1),
            )
        }
        database.withTransaction {
            database.syncDao().upsertOperations(entities)
            val main = preferences?.mainTrackingProvider?.first()
            val secondary = preferences?.secondaryTrackingProvider?.first()
            val now = System.currentTimeMillis()
            ensureDeliveries(operations, buildList {
                main?.let { provider -> operations.forEach { add(SyncOperationDelivery(operationId = it.id, operationVersion = it.sourceVersion, providerId = provider, roleAtEnqueue = TrackingRole.MAIN, createdAt = now, updatedAt = now)) } }
                secondary?.let { provider -> operations.forEach { add(SyncOperationDelivery(operationId = it.id, operationVersion = it.sourceVersion, providerId = provider, roleAtEnqueue = TrackingRole.SECONDARY, createdAt = now, updatedAt = now)) } }
            })
        }
    }

    override suspend fun complete(operations: List<SyncOperation>) {
        if (operations.isEmpty()) return
        database.withTransaction {
            operations.forEach { operation ->
                if (operation.type in setOf(
                        SyncOperationType.LIBRARY_STATUS,
                        SyncOperationType.MOVIE_WATCHED,
                        SyncOperationType.MOVIE_UNWATCHED,
                    ) || operation.id.startsWith("state:")) {
                    database.stateDao().markCleanIfUnchanged(
                        operation.mediaType.name,
                        operation.mediaId,
                        operation.sourceVersion,
                    )
                }
                if (operation.id.startsWith("write:")) operation.id.removePrefix("write:").toLongOrNull()
                    ?.let { database.syncDao().deleteWrite(it) }
            }
            val ids = operations.map(SyncOperation::id)
            database.syncDao().deleteOperations(ids)
            database.syncDao().deleteDeliveries(ids)
        }
    }

    override suspend fun fail(operations: List<SyncOperation>, error: Throwable) {
        val message = error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
        operations.forEach { database.syncDao().markOperationFailed(it.id, message) }
    }

    override suspend fun ensureDeliveries(operations: List<SyncOperation>, targets: List<SyncOperationDelivery>) {
        if (operations.isEmpty() || targets.isEmpty()) return
        val ids = operations.mapTo(linkedSetOf(), SyncOperation::id)
        val existingRows = database.syncDao().deliveries(ids.toList())
        val existing = existingRows.map { "${it.operationId}:${it.operationVersion}:${it.providerId}" }.toSet()
        val staleIds = operations.filter { operation ->
            existingRows.any { it.operationId == operation.id } &&
                existingRows.none { it.operationId == operation.id && it.operationVersion == operation.sourceVersion }
        }.mapTo(linkedSetOf(), SyncOperation::id)
        if (staleIds.isNotEmpty()) database.syncDao().deleteDeliveries(staleIds.toList())
        val currentRows = existingRows.filterNot { it.operationId in staleIds }
        val hasGeneration = operations.associate { it.id to it.sourceVersion }
        val now = System.currentTimeMillis()
        val missing = targets.filter { target ->
            target.operationId in ids &&
                hasGeneration[target.operationId] == target.operationVersion &&
                currentRows.none { it.operationId == target.operationId && it.operationVersion == target.operationVersion } &&
                "${target.operationId}:${target.operationVersion}:${target.providerId.name}" !in existing
        }
            .map { it.toEntity(now) }
        if (missing.isNotEmpty()) database.syncDao().upsertDeliveries(missing)
    }

    override suspend fun backfillLegacyDeliveries() {
        val main = preferences?.mainTrackingProvider?.first() ?: return
        if (database.syncDao().get(LEGACY_DELIVERY_BACKFILL_AREA) != null) return
        materializeLegacyOperations()
        database.withTransaction {
            if (database.syncDao().get(LEGACY_DELIVERY_BACKFILL_AREA) != null) return@withTransaction
            val operations = database.syncDao().syncOperations().filter { operation ->
                operation.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) &&
                    !operation.operationId.startsWith("reconcile:")
            }
            val writes = database.syncDao().pendingWrites().associateBy { "write:${it.id}" }
            val existing = if (operations.isEmpty()) emptyList() else database.syncDao().deliveries(operations.map(SyncOperationEntity::operationId))
            val rows = mutableListOf<SyncOperationDeliveryEntity>()
            val updatedOperations = mutableListOf<SyncOperationEntity>()
            operations.forEach { operation ->
                val sourceVersion = writes[operation.operationId]?.createdAt ?: operation.createdAt
                val generationRows = existing.filter { it.operationId == operation.operationId && it.operationVersion == sourceVersion }
                if (generationRows.isEmpty()) {
                    // Rows from a prior reused logical key belong to an obsolete
                    // generation and must not be carried into this backfill.
                    if (existing.any { it.operationId == operation.operationId }) {
                        database.syncDao().deleteDeliveries(listOf(operation.operationId))
                    }
                    rows += SyncOperationDeliveryEntity(
                        operationId = operation.operationId,
                        operationVersion = sourceVersion,
                        providerId = main.name,
                        status = DeliveryStatus.PENDING.name,
                        required = true,
                        roleAtEnqueue = TrackingRole.MAIN.name,
                        createdAt = sourceVersion,
                        updatedAt = System.currentTimeMillis(),
                    )
                    updatedOperations += operation.copy(providerId = main.name)
                }
            }
            if (rows.isNotEmpty()) database.syncDao().upsertDeliveries(rows)
            if (updatedOperations.isNotEmpty()) database.syncDao().upsertOperations(updatedOperations)
            database.syncDao().upsert(
                SyncStateEntity(
                    area = LEGACY_DELIVERY_BACKFILL_AREA,
                    remoteTimestamp = main.name,
                    lastSuccessfulSync = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun deliveries(operationIds: Set<String>): List<SyncOperationDelivery> =
        if (operationIds.isEmpty()) emptyList() else database.syncDao().deliveries(operationIds.toList()).map(SyncOperationDeliveryEntity::toDomain)

    override suspend fun acknowledge(provider: TrackingProviderId, operationIds: Set<String>) {
        operationIds.forEach { id -> database.syncDao().deliveries(listOf(id)).filter { it.providerId == provider.name }.forEach { row ->
            database.syncDao().updateDelivery(provider.name, id, row.operationVersion, DeliveryStatus.ACKNOWLEDGED.name, null)
        } }
    }

    override suspend fun acknowledge(provider: TrackingProviderId, operations: List<SyncOperation>) {
        operations.forEach { operation -> database.syncDao().updateDelivery(provider.name, operation.id, operation.sourceVersion, DeliveryStatus.ACKNOWLEDGED.name, null) }
    }

    override suspend fun failDelivery(provider: TrackingProviderId, operationIds: Set<String>, error: Throwable) {
        operationIds.forEach { id -> database.syncDao().deliveries(listOf(id)).filter { it.providerId == provider.name }.forEach { row ->
            database.syncDao().updateDelivery(provider.name, id, row.operationVersion, DeliveryStatus.FAILED.name, error.message)
        } }
    }

    override suspend fun failDelivery(provider: TrackingProviderId, operations: List<SyncOperation>, error: Throwable) {
        operations.forEach { operation -> database.syncDao().updateDelivery(provider.name, operation.id, operation.sourceVersion, DeliveryStatus.FAILED.name, error.message) }
    }

    override suspend fun skipUnsupported(provider: TrackingProviderId, operationIds: Set<String>) {
        operationIds.forEach { id -> database.syncDao().deliveries(listOf(id)).filter { it.providerId == provider.name }.forEach { row ->
            database.syncDao().updateDelivery(provider.name, id, row.operationVersion, DeliveryStatus.SKIPPED_UNSUPPORTED.name, null)
        } }
    }

    override suspend fun skipUnsupported(provider: TrackingProviderId, operations: List<SyncOperation>) {
        operations.forEach { operation -> database.syncDao().updateDelivery(provider.name, operation.id, operation.sourceVersion, DeliveryStatus.SKIPPED_UNSUPPORTED.name, null) }
    }

    override suspend fun completeReady(operations: List<SyncOperation>) {
        if (operations.isEmpty()) return
        val deliveries = deliveries(operations.mapTo(linkedSetOf(), SyncOperation::id))
        val ready = operations.filter { operation ->
            val rows = deliveries.filter { it.operationId == operation.id && it.operationVersion == operation.sourceVersion }
            rows.isEmpty() || rows.filter(SyncOperationDelivery::required).all { it.status == DeliveryStatus.ACKNOWLEDGED || it.status == DeliveryStatus.SKIPPED_UNSUPPORTED }
        }
        complete(ready)
    }

    private suspend fun materializeLegacyOperations() {
        database.withTransaction {
            val existing = database.syncDao().syncOperations().associateBy(SyncOperationEntity::operationId)
            val media = database.mediaDao().mediaSnapshot().associateBy { "${it.mediaType}:${it.tmdbId}" }
            val missing = buildList {
                database.stateDao().pendingStates().forEach { state ->
                    val id = "state:${state.mediaType}:${state.mediaId}"
                    if (id !in existing) add(
                        SyncOperationEntity(
                            operationId = id,
                            operation = SyncOperationType.LIBRARY_STATUS.name,
                            mediaType = state.mediaType,
                            mediaId = state.mediaId,
                            title = media["${state.mediaType}:${state.mediaId}"]?.title
                                ?: "${state.mediaType} #${state.mediaId}",
                            status = SyncOperationStatus.PENDING.name,
                            localValue = state.status,
                            createdAt = state.updatedAt,
                            updatedAt = state.updatedAt,
                        ),
                    )
                }
                database.syncDao().pendingWrites().forEach { write ->
                    val id = "write:${write.id}"
                    if (id !in existing) add(write.toOperationEntity(media["${write.mediaType}:${write.mediaId}"]?.title))
                }
            }
            if (missing.isNotEmpty()) database.syncDao().upsertOperations(missing)
        }
    }

    private suspend fun repairBlankTitles() {
        val operations = database.syncDao().syncOperations()
        operations.forEach { operation ->
            val title = resolveDisplayTitle(
                mediaType = operation.mediaType,
                mediaId = operation.mediaId,
                payload = operation.season?.let { season -> "$season:${operation.episode ?: 0}" },
                fallback = operation.title,
            )
            if (title != operation.title) database.syncDao().upsertOperation(operation.copy(title = title))
        }
    }

    private suspend fun resolveDisplayTitle(mediaType: String, mediaId: Int, payload: String?, fallback: String?): String {
        val mediaTitle = database.mediaDao().get(mediaType, mediaId)?.title?.takeIf(String::isNotBlank)
        val parts = payload.orEmpty().split(':', limit = 3)
        val season = parts.getOrNull(0)?.toIntOrNull()
        val episode = parts.getOrNull(1)?.toIntOrNull()
        if (season != null && episode != null && mediaType == MediaType.TV.name) {
            val episodeTitle = database.mediaDao().episode(mediaId, season, episode)?.title?.takeIf(String::isNotBlank)
            return listOfNotNull(
                mediaTitle ?: "TV #$mediaId",
                "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}" + (episodeTitle?.let { " · $it" } ?: ""),
            ).joinToString(" · ")
        }
        return mediaTitle ?: fallback?.takeIf(String::isNotBlank) ?: "$mediaType #$mediaId"
    }
}

private const val LEGACY_DELIVERY_BACKFILL_AREA = "sync_delivery_backfill_089"

private fun SyncOperationDelivery.toEntity(now: Long) = SyncOperationDeliveryEntity(
    operationId = operationId,
    operationVersion = operationVersion,
    providerId = providerId.name,
    status = status.name,
    required = required,
    roleAtEnqueue = roleAtEnqueue.name,
    attemptCount = attemptCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = now,
)

private fun SyncOperationDeliveryEntity.toDomain() = SyncOperationDelivery(
    operationId = operationId,
    operationVersion = operationVersion,
    providerId = runCatching { TrackingProviderId.valueOf(providerId) }.getOrDefault(TrackingProviderId.SIMKL),
    status = runCatching { DeliveryStatus.valueOf(status) }.getOrDefault(DeliveryStatus.PENDING),
    required = required,
    roleAtEnqueue = runCatching { TrackingRole.valueOf(roleAtEnqueue) }.getOrDefault(TrackingRole.MAIN),
    attemptCount = attemptCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun PendingWriteEntity.toOperationEntity(mediaTitle: String?) = SyncOperationEntity(
    operationId = "write:$id",
    operation = operation,
    mediaType = mediaType,
    mediaId = mediaId,
    title = mediaTitle ?: "$mediaType #$mediaId",
    status = SyncOperationStatus.PENDING.name,
    localValue = if (operation.startsWith("EPISODE_")) {
        payload.split(':').take(2).joinToString(" · ") { it.padStart(2, '0') }
    } else operation,
    createdAt = createdAt,
    updatedAt = createdAt,
    attemptCount = attemptCount,
    season = payload.episodePart(0),
    episode = payload.episodePart(1),
)

private fun String.episodePart(index: Int): Int? = split(':', limit = 3).getOrNull(index)?.toIntOrNull()

private fun SyncOperationEntity.toSyncOperation(write: PendingWriteEntity?): SyncOperation? {
    val type = runCatching { SyncOperationType.valueOf(operation) }.getOrNull() ?: return null
    val media = runCatching { MediaType.valueOf(mediaType) }.getOrNull() ?: return null
    return SyncOperation(
        id = operationId,
        type = type,
        mediaType = media,
        mediaId = mediaId,
        title = title,
        value = localValue,
        payload = write?.payload,
        sourceVersion = write?.createdAt ?: createdAt,
    )
}

private fun SyncOperationEntity.toCard(): SyncOperationCard? {
    val type = runCatching { MediaType.valueOf(mediaType) }.getOrNull() ?: return null
    val operationStatus = runCatching { SyncOperationStatus.valueOf(status) }.getOrNull() ?: return null
    return SyncOperationCard(
        id = operationId,
        operation = operation,
        mediaType = type,
        mediaId = mediaId,
        title = title,
        status = operationStatus,
        message = message,
        localValue = localValue,
        remoteValue = remoteValue,
        createdAt = createdAt,
        updatedAt = updatedAt,
        attemptCount = attemptCount,
        providerId = providerId,
    )
}

private fun SyncOperationStatus.aggregateWith(deliveries: List<SyncOperationDelivery>): SyncOperationStatus {
    if (this == SyncOperationStatus.CONFLICT) return this
    val required = deliveries.filter(SyncOperationDelivery::required)
    val hasFailed = required.any { it.status == DeliveryStatus.FAILED }
    val hasAcknowledged = required.any { it.status == DeliveryStatus.ACKNOWLEDGED }
    return if (hasFailed && hasAcknowledged) SyncOperationStatus.PARTIAL else this
}

