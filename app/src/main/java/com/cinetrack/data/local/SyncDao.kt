package com.cinetrack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<SyncStateEntity>)

    @Query("SELECT * FROM sync_state WHERE area = :area LIMIT 1")
    suspend fun get(area: String): SyncStateEntity?

    @Query("DELETE FROM sync_state WHERE area = :area")
    suspend fun delete(area: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queue(write: PendingWriteEntity): Long

    @Query("SELECT COUNT(*) FROM sync_operations WHERE mediaType = 'TV' AND mediaId = :showId")
    suspend fun operationCountForShow(showId: Int): Int

    @Query("SELECT COUNT(*) FROM pending_writes WHERE mediaType = 'TV' AND mediaId = :showId")
    suspend fun pendingWriteCountForShow(showId: Int): Int

    @Query("SELECT * FROM pending_writes ORDER BY createdAt")
    suspend fun pendingWrites(): List<PendingWriteEntity>

    @Query("SELECT * FROM pending_writes WHERE id = :id LIMIT 1")
    suspend fun pendingWrite(id: Long): PendingWriteEntity?

    @Query("DELETE FROM pending_writes WHERE id = :id")
    suspend fun deleteWrite(id: Long)

    @Query("DELETE FROM pending_writes WHERE id = :id AND createdAt = :createdAt")
    suspend fun deletePendingWriteIfGeneration(id: Long, createdAt: Long): Int

    @Query("DELETE FROM pending_writes WHERE id IN (:ids)")
    suspend fun deleteWrites(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperation(operation: SyncOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperations(operations: List<SyncOperationEntity>)

    @Query("SELECT * FROM sync_operations ORDER BY CASE status WHEN 'CONFLICT' THEN 0 WHEN 'FAILED' THEN 1 ELSE 2 END, updatedAt DESC")
    suspend fun syncOperations(): List<SyncOperationEntity>

    /** Current-generation Floppy bootstrap rows only; avoids the broad queue projection. */
    @Query("""
        SELECT o.* FROM sync_operations o
        INNER JOIN sync_operation_deliveries d
          ON d.operationId = o.operationId AND d.operationVersion = o.createdAt
        WHERE o.operationId LIKE :prefix || '%'
          AND d.providerId = 'FLOPPY'
          AND d.providerInstanceId = :connectionId
          AND d.status IN ('PENDING', 'FAILED')
        ORDER BY o.createdAt, o.operationId
        LIMIT :limit
    """)
    suspend fun pendingFloppyBootstrapOperations(prefix: String, connectionId: String, limit: Int): List<SyncOperationEntity>

    @Query("""
        SELECT COUNT(*) FROM sync_operations o
        INNER JOIN sync_operation_deliveries d
          ON d.operationId = o.operationId AND d.operationVersion = o.createdAt
        WHERE o.operationId LIKE :prefix || '%'
          AND d.providerId = 'FLOPPY'
          AND d.providerInstanceId = :connectionId
          AND d.status IN ('PENDING', 'FAILED')
    """)
    suspend fun pendingFloppyBootstrapCount(prefix: String, connectionId: String): Int

    /** Minimal remote-index seed query; avoids loading the complete bootstrap
     * plan just to decide whether episode history must be prepared. */
    @Query("""
        SELECT o.* FROM sync_operations o
        INNER JOIN sync_operation_deliveries d
          ON d.operationId = o.operationId AND d.operationVersion = o.createdAt
        WHERE o.operationId LIKE :prefix || '%'
          AND o.operation = 'EPISODE_WATCHED'
          AND d.providerId = 'FLOPPY'
          AND d.providerInstanceId = :connectionId
          AND d.status IN ('PENDING', 'FAILED')
        ORDER BY o.createdAt, o.operationId
        LIMIT :limit
    """)
    suspend fun pendingFloppyBootstrapEpisodeOperations(prefix: String, connectionId: String, limit: Int): List<SyncOperationEntity>

    @Query("""
        SELECT o.* FROM sync_operations o
        INNER JOIN sync_operation_deliveries d
          ON d.operationId = o.operationId AND d.operationVersion = o.createdAt
        WHERE o.operationId IN (:operationIds)
          AND d.providerId = 'FLOPPY'
          AND d.providerInstanceId = :connectionId
          AND d.status IN ('PENDING', 'FAILED')
        ORDER BY o.createdAt, o.operationId
    """)
    suspend fun pendingFloppyBootstrapOperationsByIds(operationIds: List<String>, connectionId: String): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun syncOperation(operationId: String): SyncOperationEntity?

    @Query("UPDATE sync_operations SET status = 'FAILED', message = :message, updatedAt = :updatedAt, attemptCount = attemptCount + 1 WHERE operationId = :operationId AND createdAt = :operationVersion")
    suspend fun markOperationFailedIfGeneration(operationId: String, operationVersion: Long, message: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM sync_operations WHERE operationId = :operationId")
    suspend fun deleteOperation(operationId: String)

    @Query("DELETE FROM sync_operations WHERE operationId = :operationId AND createdAt = :operationVersion")
    suspend fun deleteOperationIfGeneration(operationId: String, operationVersion: Long): Int

    @Query("DELETE FROM sync_operations WHERE operationId IN (:operationIds)")
    suspend fun deleteOperations(operationIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeliveries(items: List<SyncOperationDeliveryEntity>)

    @Query("SELECT * FROM sync_operation_deliveries WHERE operationId IN (:operationIds)")
    suspend fun deliveries(operationIds: List<String>): List<SyncOperationDeliveryEntity>

    @Query("SELECT * FROM sync_operation_deliveries WHERE operationId = :operationId AND operationVersion = :operationVersion AND providerId = :providerId LIMIT 1")
    suspend fun delivery(operationId: String, operationVersion: Long, providerId: String): SyncOperationDeliveryEntity?

    @Query("UPDATE sync_operation_deliveries SET status = :status, attemptCount = attemptCount + 1, lastError = :lastError, updatedAt = :updatedAt WHERE operationId = :operationId AND operationVersion = :operationVersion AND providerId = :providerId AND status IN ('PENDING','FAILED')")
    suspend fun updateDelivery(providerId: String, operationId: String, operationVersion: Long, status: String, lastError: String?, updatedAt: Long = System.currentTimeMillis())

    /** Acknowledges exact current-generation deliveries in one set-based update. */
    @Query("""
        UPDATE sync_operation_deliveries
        SET status = 'ACKNOWLEDGED', attemptCount = attemptCount + 1, lastError = NULL, updatedAt = :updatedAt
        WHERE providerId = :providerId
          AND operationId IN (:operationIds)
          AND status IN ('PENDING','FAILED')
          AND operationVersion = (
              SELECT createdAt FROM sync_operations
              WHERE operationId = sync_operation_deliveries.operationId
          )
    """)
    suspend fun acknowledgeCurrentDeliveries(providerId: String, operationIds: List<String>, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM sync_operation_deliveries WHERE operationId IN (:operationIds)")
    suspend fun deleteDeliveries(operationIds: List<String>)

    @Query("DELETE FROM sync_operation_deliveries WHERE operationId = :operationId AND operationVersion = :operationVersion")
    suspend fun deleteDeliveriesForGeneration(operationId: String, operationVersion: Long): Int

    @Query("SELECT * FROM sync_operation_deliveries")
    suspend fun allDeliveries(): List<SyncOperationDeliveryEntity>

    @Query("DELETE FROM sync_operation_deliveries WHERE operationId = :operationId AND operationVersion = :operationVersion AND providerId = :providerId")
    suspend fun deleteDelivery(operationId: String, operationVersion: Long, providerId: String)

    @Query("UPDATE sync_operation_deliveries SET status = 'CANCELLED_PROVIDER_REMOVED', lastError = :reason, updatedAt = :updatedAt WHERE providerId = :providerId AND status IN ('PENDING','FAILED')")
    suspend fun cancelOutstandingDeliveries(providerId: String, reason: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_operation_deliveries SET status = 'FAILED', lastError = :reason, updatedAt = :updatedAt WHERE providerId = :providerId AND status IN ('PENDING','FAILED')")
    suspend fun failOutstandingDeliveries(providerId: String, reason: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_operation_deliveries SET status = 'CANCELLED_PROVIDER_INSTANCE_CHANGED', lastError = :reason, updatedAt = :updatedAt WHERE providerId = :providerId AND status IN ('PENDING','FAILED')")
    suspend fun cancelInstanceDeliveries(providerId: String, reason: String, updatedAt: Long = System.currentTimeMillis())

    /** Repairs rows left behind when a provider instance was persisted before
     * process death interrupted the cleanup phase.  Identity is immutable:
     * stale rows are terminally cancelled, never retargeted. */
    @Query("UPDATE sync_operation_deliveries SET status = 'CANCELLED_PROVIDER_INSTANCE_CHANGED', lastError = :reason, updatedAt = :updatedAt WHERE providerId = :providerId AND (providerInstanceId IS NULL OR providerInstanceId != :currentInstanceId) AND status IN ('PENDING','FAILED')")
    suspend fun repairInstanceDeliveries(providerId: String, currentInstanceId: String, reason: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_operation_deliveries SET status = 'SUPERSEDED', lastError = :reason, updatedAt = :updatedAt WHERE providerId = :providerId AND operationId IN (:operationIds) AND status IN ('PENDING','FAILED')")
    suspend fun supersedeDeliveries(providerId: String, operationIds: List<String>, reason: String = "Superseded by newer canonical generation", updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_operation_deliveries SET status = 'SUPERSEDED', lastError = :reason, updatedAt = :updatedAt WHERE operationId IN (:operationIds) AND status IN ('PENDING','FAILED')")
    suspend fun supersedeAllDeliveries(operationIds: List<String>, reason: String = "Superseded by newer canonical generation", updatedAt: Long = System.currentTimeMillis())
}
