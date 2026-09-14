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
    /** Production Room queues enforce the exact-generation delivery invariant. */
    val requiresPersistedDeliveryRows: Boolean get() = false
    suspend fun pending(operationIds: Set<String>? = null): List<SyncOperation>
    suspend fun enqueue(operations: List<SyncOperation>) {}
    /**
     * Persists operations with the exact provider targets captured while the
     * routing mutex was held. Implementations that do not persist deliveries
     * may keep using the compatibility enqueue above.
     */
    suspend fun enqueue(
        operations: List<SyncOperation>,
        targets: List<SyncOperationDelivery>,
        removeOperationId: String? = null,
        supersedeOperationIds: Set<String> = emptySet(),
    ) {
        enqueue(operations)
    }
    suspend fun cards(): List<SyncOperationCard>
    suspend fun complete(operations: List<SyncOperation>)
    suspend fun fail(operations: List<SyncOperation>, error: Throwable)

    /** Provider delivery hooks. Defaults keep lightweight test repositories source-compatible. */
    suspend fun ensureDeliveries(operations: List<SyncOperation>, targets: List<SyncOperationDelivery>) {}
    suspend fun deliveries(operationIds: Set<String>): List<SyncOperationDelivery> = emptyList()
    /** Returns true only when this exact generation is still active for the provider. */
    suspend fun currentIntentTargetsProvider(
        operationId: String,
        operationVersion: Long,
        provider: TrackingProviderId,
    ): Boolean = false
    suspend fun acknowledge(provider: TrackingProviderId, operationIds: Set<String>) {}
    suspend fun failDelivery(provider: TrackingProviderId, operationIds: Set<String>, error: Throwable) {}
    suspend fun skipUnsupported(provider: TrackingProviderId, operationIds: Set<String>) {}
    suspend fun acknowledge(provider: TrackingProviderId, operations: List<SyncOperation>) = acknowledge(provider, operations.mapTo(linkedSetOf(), SyncOperation::id))
    suspend fun failDelivery(provider: TrackingProviderId, operations: List<SyncOperation>, error: Throwable) = failDelivery(provider, operations.mapTo(linkedSetOf(), SyncOperation::id), error)
    suspend fun skipUnsupported(provider: TrackingProviderId, operations: List<SyncOperation>) = skipUnsupported(provider, operations.mapTo(linkedSetOf(), SyncOperation::id))
    suspend fun completeReady(operations: List<SyncOperation>) = complete(operations)

    /** Marks stale SECONDARY deliveries terminal without retargeting history. */
    suspend fun supersedeSecondary(operation: SyncOperation) {}

    /** Atomically replaces the current SECONDARY mirror for one logical field. */
    suspend fun replaceSecondaryMirror(operation: SyncOperation, target: SyncOperationDelivery) {
        enqueue(listOf(operation), listOf(target))
    }

    /** Binds pre-delivery operations to one startup MAIN provider exactly once. */
    suspend fun backfillLegacyDeliveries() {}

    /** Terminates pending deliveries when a provider is explicitly removed. */
    suspend fun cancelProviderDeliveries(provider: TrackingProviderId, reason: String = "Provider removed") {}
    /** Keeps deliveries retryable when only the provider instance changed. */
    suspend fun failProviderDeliveries(provider: TrackingProviderId, reason: String = "Provider instance changed") {}
    /** Terminally cancels deliveries targeted at an old provider instance. */
    suspend fun cancelProviderInstanceDeliveries(provider: TrackingProviderId, reason: String = "Provider instance changed") {}

    /** Binds only current, never-targeted local intents after initial MAIN setup. */
    suspend fun bindUnboundCurrentIntents(provider: TrackingProviderId) {}
}

/**
 * Adapts the existing Room queue to provider-neutral operations. The legacy
 * pending rows remain the durable payload/source-of-truth during migration.
 */
class RoomSyncOperationRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences,
) : SyncOperationRepository {
    override val requiresPersistedDeliveryRows: Boolean = true
    override suspend fun pending(operationIds: Set<String>?): List<SyncOperation> {
        backfillLegacyDeliveries()
        ensureLegacyOperationsMaterialized()
        repairDeliveryRows()
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
        ensureLegacyOperationsMaterialized()
        repairDeliveryRows()
        repairBlankTitles()
        val cards = database.syncDao().syncOperations().mapNotNull(SyncOperationEntity::toCard)
        val currentGenerations = cards.associate { it.id to it.createdAt }
        val deliveryRows = if (cards.isEmpty()) emptyList() else database.syncDao().deliveries(cards.map(SyncOperationCard::id))
            .filter { row -> currentGenerations[row.operationId] == row.operationVersion }
            .mapNotNull(SyncOperationDeliveryEntity::toDomainOrNull)
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
        if (operations.isNotEmpty()) {
            error(
                "Production sync operations require persisted provider targets; " +
                    "use DurableSyncOperationWriter",
            )
        }
    }

    override suspend fun enqueue(
        operations: List<SyncOperation>,
        targets: List<SyncOperationDelivery>,
        removeOperationId: String?,
        supersedeOperationIds: Set<String>,
    ) {
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
                payload = operation.payload,
            )
        }
        database.withTransaction {
            removeOperationId?.let { conflictId ->
                val conflict = database.syncDao().syncOperation(conflictId)
                require(conflict?.status == SyncOperationStatus.CONFLICT.name) {
                    "Conflict no longer exists"
                }
            }
            // A new local generation makes every older intent for the same
            // logical field obsolete across every provider.  Delivery history
            // is retained; only non-terminal rows transition to SUPERSEDED.
            val existingOperations = database.syncDao().syncOperations()
            val staleByField = operations.flatMap { operation ->
                val field = operation.logicalField() ?: return@flatMap emptyList()
                existingOperations.filter { entity ->
                    entity.operationId != operation.id && entity.logicalField() == field
                }.map(SyncOperationEntity::operationId)
            }.toSet()
            val newIds = operations.mapTo(linkedSetOf(), SyncOperation::id)
            val explicitSuperseded = supersedeOperationIds.filter { it !in newIds }.toSet()
            val supersededIds = staleByField + explicitSuperseded
            if (supersededIds.isNotEmpty()) {
                database.syncDao().supersedeAllDeliveries(supersededIds.toList())
            }
            database.syncDao().upsertOperations(entities)
            ensureDeliveries(operations, targets)
            removeOperationId?.let { conflictId ->
                database.syncDao().deleteOperation(conflictId)
            }
            retireTerminalOperations(supersededIds)
        }
    }

    override suspend fun complete(operations: List<SyncOperation>) {
        if (operations.isEmpty()) return
        database.withTransaction {
            operations.forEach { operation ->
                val current = database.syncDao().syncOperation(operation.id)
                val isCurrentGeneration = current?.createdAt == operation.sourceVersion
                if (isCurrentGeneration && (
                        operation.type == SyncOperationType.LIBRARY_STATUS ||
                            operation.id.startsWith("state:")
                        )) {
                    database.stateDao().markCleanIfUnchanged(
                        operation.mediaType.name,
                        operation.mediaId,
                        operation.sourceVersion,
                    )
                }
                if (isCurrentGeneration && operation.id.startsWith("write:")) {
                    operation.id.removePrefix("write:").toLongOrNull()?.let { writeId ->
                        database.syncDao().deletePendingWriteIfGeneration(writeId, operation.sourceVersion)
                    }
                }
                // Delivery history is generation-scoped. An old response may
                // retire its own rows, but can never delete a newer generation.
                database.syncDao().deleteDeliveriesForGeneration(operation.id, operation.sourceVersion)
                if (isCurrentGeneration) database.syncDao().deleteOperationIfGeneration(operation.id, operation.sourceVersion)
            }
        }
    }

    override suspend fun fail(operations: List<SyncOperation>, error: Throwable) {
        val message = error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
        operations.forEach { database.syncDao().markOperationFailedIfGeneration(it.id, it.sourceVersion, message) }
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
        if (staleIds.isNotEmpty()) database.syncDao().supersedeAllDeliveries(staleIds.toList())
        val currentRows = existingRows
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
        val main = preferences.mainTrackingProvider.first() ?: return
        if (database.syncDao().get(LEGACY_DELIVERY_BACKFILL_AREA) != null) return
        ensureLegacyOperationsMaterialized()
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
                        providerInstanceId = null,
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

    override suspend fun deliveries(operationIds: Set<String>): List<SyncOperationDelivery> {
        repairDeliveryRows()
        return if (operationIds.isEmpty()) emptyList()
        else database.syncDao().deliveries(operationIds.toList()).mapNotNull(SyncOperationDeliveryEntity::toDomainOrNull)
    }

    override suspend fun currentIntentTargetsProvider(
        operationId: String,
        operationVersion: Long,
        provider: TrackingProviderId,
    ): Boolean {
        val operation = database.syncDao().syncOperation(operationId) ?: return false
        if (operation.createdAt != operationVersion ||
            operation.status !in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name)
        ) return false
        val delivery = database.syncDao().delivery(operationId, operationVersion, provider.name) ?: return false
        return delivery.status == DeliveryStatus.PENDING.name || delivery.status == DeliveryStatus.FAILED.name
    }

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
            rows.isNotEmpty() && rows.filter(SyncOperationDelivery::required).all {
                it.status == DeliveryStatus.ACKNOWLEDGED ||
                    it.status == DeliveryStatus.SKIPPED_UNSUPPORTED ||
                    it.status == DeliveryStatus.CANCELLED_PROVIDER_REMOVED ||
                    it.status == DeliveryStatus.CANCELLED_PROVIDER_INSTANCE_CHANGED ||
                    it.status == DeliveryStatus.SUPERSEDED
            }
        }
        complete(ready)
    }

    override suspend fun supersedeSecondary(operation: SyncOperation) {
        val secondary = preferences.secondaryTrackingProvider.first() ?: return
        database.withTransaction {
            val matching = database.syncDao().syncOperations().filter { entity ->
                entity.operationId != operation.id && entity.logicalField() == operation.logicalField()
            }
            if (matching.isNotEmpty()) {
                database.syncDao().supersedeDeliveries(
                    providerId = secondary.name,
                    operationIds = matching.map(SyncOperationEntity::operationId),
                )
                retireTerminalOperations(matching.map(SyncOperationEntity::operationId).toSet())
            }
        }
    }

    override suspend fun replaceSecondaryMirror(operation: SyncOperation, target: SyncOperationDelivery) {
        require(target.providerId == TrackingProviderId.FLOPPY || target.providerId == TrackingProviderId.SIMKL)
        database.withTransaction {
            val matching = database.syncDao().syncOperations().filter { entity ->
                entity.operationId != operation.id && entity.logicalField() == operation.logicalField()
            }
            val staleIds = matching.map(SyncOperationEntity::operationId).toSet()
            if (staleIds.isNotEmpty()) {
                database.syncDao().supersedeDeliveries(target.providerId.name, staleIds.toList())
            }
            val entity = SyncOperationEntity(
                operationId = operation.id,
                operation = operation.type.name,
                mediaType = operation.mediaType.name,
                mediaId = operation.mediaId,
                title = operation.title,
                status = SyncOperationStatus.PENDING.name,
                localValue = operation.value,
                createdAt = operation.sourceVersion,
                updatedAt = System.currentTimeMillis(),
                season = operation.payload?.episodePart(0),
                episode = operation.payload?.episodePart(1),
                payload = operation.payload,
            )
            database.syncDao().upsertOperation(entity)
            ensureDeliveries(listOf(operation), listOf(target))
            retireTerminalOperations(staleIds)
        }
    }

    override suspend fun cancelProviderDeliveries(provider: TrackingProviderId, reason: String) {
        database.withTransaction {
            database.syncDao().cancelOutstandingDeliveries(provider.name, reason)
        }
    }

    override suspend fun failProviderDeliveries(provider: TrackingProviderId, reason: String) {
        database.withTransaction {
            database.syncDao().failOutstandingDeliveries(provider.name, reason)
        }
    }

    override suspend fun cancelProviderInstanceDeliveries(provider: TrackingProviderId, reason: String) {
        database.withTransaction {
            database.syncDao().cancelInstanceDeliveries(provider.name, reason)
            retireTerminalOperations(database.syncDao().syncOperations().map(SyncOperationEntity::operationId).toSet())
        }
    }

    override suspend fun bindUnboundCurrentIntents(provider: TrackingProviderId) {
        val operations = database.syncDao().syncOperations().filter { operation ->
            operation.status in setOf(SyncOperationStatus.PENDING.name, SyncOperationStatus.FAILED.name) &&
                !operation.operationId.startsWith("reconcile:") &&
                !operation.operationId.startsWith("conflict:")
        }
        if (operations.isEmpty()) return
        database.withTransaction {
            operations.forEach { entity ->
                val write = entity.operationId.removePrefix("write:").toLongOrNull()
                    ?.let { database.syncDao().pendingWrite(it) }
                val operation = entity.toSyncOperation(write) ?: return@forEach
                val current = when {
                    operation.id.startsWith("state:") -> {
                        val state = database.stateDao().get(operation.mediaType.name, operation.mediaId)
                        state?.dirty == true &&
                            state.status == operation.value &&
                            state.updatedAt == operation.sourceVersion
                    }
                    operation.id.startsWith("write:") -> write?.createdAt == operation.sourceVersion
                    else -> true
                }
                if (!current) return@forEach
                // A generation's target snapshot is immutable. Binding is only
                // allowed for genuinely unbound current work: no delivery row
                // for any provider exists at this generation. A provider row
                // that is cancelled, superseded, or targets another provider
                // is still historical evidence and must never be reopened or
                // extended with a new target.
                val hasAnyTarget = database.syncDao().deliveries(listOf(operation.id))
                    .any { it.operationVersion == operation.sourceVersion }
                if (hasAnyTarget) return@forEach
                ensureDeliveries(
                    listOf(operation),
                    listOf(
                        SyncOperationDelivery(
                            operationId = operation.id,
                            operationVersion = operation.sourceVersion,
                            providerId = provider,
                            required = true,
                            roleAtEnqueue = TrackingRole.MAIN,
                            providerInstanceId = null,
                            createdAt = operation.sourceVersion,
                            updatedAt = operation.sourceVersion,
                        ),
                    ),
                )
            }
        }
    }

    /** Removes 0.89 orphan rows and stale generations without retargeting modern work. */
    private suspend fun repairDeliveryRows() {
        val operations = database.syncDao().syncOperations().associateBy(SyncOperationEntity::operationId)
        database.syncDao().allDeliveries().forEach { row ->
            val provider = runCatching { TrackingProviderId.valueOf(row.providerId) }.getOrNull()
            val status = runCatching { DeliveryStatus.valueOf(row.status) }.getOrNull()
            val role = runCatching { TrackingRole.valueOf(row.roleAtEnqueue) }.getOrNull()
            if (provider == null || status == null || role == null) {
                android.util.Log.e("CineTrackSync", "Quarantining invalid delivery row ${row.operationId}/${row.providerId}/${row.status}/${row.roleAtEnqueue}")
                database.syncDao().deleteDelivery(row.operationId, row.operationVersion, row.providerId)
                return@forEach
            }
            val operation = operations[row.operationId]
            val expected = operation?.let { database.syncDao().pendingWrite(row.operationId.removePrefix("write:").toLongOrNull() ?: -1L)?.createdAt ?: it.createdAt }
            if (operation == null) {
                database.syncDao().deleteDelivery(row.operationId, row.operationVersion, row.providerId)
            } else if (expected != row.operationVersion && status in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED)) {
                database.syncDao().supersedeDeliveries(row.providerId, listOf(row.operationId))
            }
        }
    }

    private suspend fun retireTerminalOperations(operationIds: Set<String>) {
        operationIds.forEach { id ->
            val entity = database.syncDao().syncOperation(id) ?: return@forEach
            val rows = database.syncDao().deliveries(listOf(id))
            val required = rows.filter { it.required }
            if (rows.isEmpty()) {
                entity.operationId.removePrefix("write:").toLongOrNull()?.let { writeId ->
                    database.syncDao().deletePendingWriteIfGeneration(writeId, entity.createdAt)
                }
                database.syncDao().deleteOperationIfGeneration(id, entity.createdAt)
                return@forEach
            }
            if (required.isEmpty() || required.any {
                    it.status !in setOf(
                        DeliveryStatus.ACKNOWLEDGED.name,
                        DeliveryStatus.SKIPPED_UNSUPPORTED.name,
                        DeliveryStatus.CANCELLED_PROVIDER_REMOVED.name,
                        DeliveryStatus.CANCELLED_PROVIDER_INSTANCE_CHANGED.name,
                        DeliveryStatus.SUPERSEDED.name,
                    )
                }) return@forEach
            entity.operationId.removePrefix("write:").toLongOrNull()?.let { writeId ->
                database.syncDao().deletePendingWriteIfGeneration(writeId, entity.createdAt)
            }
            database.syncDao().deleteDeliveriesForGeneration(id, entity.createdAt)
            database.syncDao().deleteOperationIfGeneration(id, entity.createdAt)
        }
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

    private suspend fun ensureLegacyOperationsMaterialized() {
        if (database.syncDao().get(LEGACY_OPERATION_MATERIALIZATION_AREA) != null) return
        materializeLegacyOperations()
        database.syncDao().upsert(
            SyncStateEntity(
                area = LEGACY_OPERATION_MATERIALIZATION_AREA,
                remoteTimestamp = "complete",
                lastSuccessfulSync = System.currentTimeMillis(),
            ),
        )
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
private const val LEGACY_OPERATION_MATERIALIZATION_AREA = "sync_operation_materialization_096"

private fun SyncOperationDelivery.toEntity(now: Long) = SyncOperationDeliveryEntity(
    operationId = operationId,
    operationVersion = operationVersion,
    providerId = providerId.name,
    status = status.name,
    required = required,
    roleAtEnqueue = roleAtEnqueue.name,
    providerInstanceId = providerInstanceId,
    attemptCount = attemptCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = now,
)

private fun SyncOperationDeliveryEntity.toDomainOrNull(): SyncOperationDelivery? {
    val provider = runCatching { TrackingProviderId.valueOf(providerId) }.getOrNull() ?: return null
    val parsedStatus = runCatching { DeliveryStatus.valueOf(status) }.getOrNull() ?: return null
    val role = runCatching { TrackingRole.valueOf(roleAtEnqueue) }.getOrNull() ?: return null
    return SyncOperationDelivery(
    operationId = operationId,
    operationVersion = operationVersion,
    providerId = provider,
    status = parsedStatus,
    required = required,
    roleAtEnqueue = role,
    providerInstanceId = providerInstanceId,
    attemptCount = attemptCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
    )
}

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
        payload = write?.payload ?: payload ?: when (type) {
            SyncOperationType.EPISODE_WATCHED -> {
                if (season == null || episode == null) null
                else "$season:$episode:${java.time.Instant.ofEpochMilli(write?.createdAt ?: createdAt)}"
            }
            SyncOperationType.EPISODE_UNWATCHED -> {
                if (season == null || episode == null) null else "$season:$episode"
            }
            else -> null
        },
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

