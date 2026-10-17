package com.cinetrack.crash

import com.cinetrack.data.sync.TrackingSyncError
import com.cinetrack.data.sync.floppy.FloppySession
import com.cinetrack.data.sync.floppy.network.CrashSubmitOutcome
import com.cinetrack.data.sync.floppy.network.FloppyRemoteDataSource
import kotlinx.coroutines.CancellationException

/** What the worker should report to WorkManager. */
enum class CrashUploadResult { SUCCESS, RETRY }

/**
 * Sends the outbox to Floppy one file at a time. A file is deleted only when
 * Floppy's outcome for it is known (stored, already stored, or permanently
 * rejected); everything else stays for the next run. Nothing here logs a
 * report, a key or a header.
 */
class CrashReportUploader(
    private val outbox: CrashOutbox,
    private val enabled: () -> Boolean,
    private val session: suspend () -> FloppySession?,
    /** Whether this server takes crash reports; may throw when that cannot be determined now. */
    private val hasCapability: suspend (FloppySession) -> Boolean,
    private val remote: FloppyRemoteDataSource,
) {
    suspend fun upload(): CrashUploadResult {
        if (!enabled()) {
            outbox.clear()
            return CrashUploadResult.SUCCESS
        }
        if (outbox.isEmpty()) return CrashUploadResult.SUCCESS
        val current = session() ?: return CrashUploadResult.SUCCESS
        val capable = try {
            hasCapability(current)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return CrashUploadResult.RETRY
        }
        if (!capable) return CrashUploadResult.SUCCESS

        var unresolved = false
        for (file in outbox.files()) {
            val report = outbox.read(file)
            if (report == null) {
                // Unreadable or not a report: it can never be sent.
                outbox.delete(file)
                continue
            }
            try {
                when (remote.submitCrashReport(current, report)) {
                    CrashSubmitOutcome.CREATED,
                    CrashSubmitOutcome.ALREADY_RECEIVED,
                    CrashSubmitOutcome.REJECTED,
                    -> outbox.delete(file)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TrackingSyncError) {
                when (error) {
                    // The key cannot send reports: keep the rest, stop for good.
                    is TrackingSyncError.AuthenticationRequired,
                    is TrackingSyncError.TokenScope,
                    -> return CrashUploadResult.SUCCESS
                    // The server or the link is busy: do not push the remaining files now.
                    is TrackingSyncError.RateLimited,
                    is TrackingSyncError.NetworkUnavailable,
                    is TrackingSyncError.Timeout,
                    is TrackingSyncError.DnsFailure,
                    is TrackingSyncError.TlsFailure,
                    -> return CrashUploadResult.RETRY
                    // 5xx, invalid body, redirect: this file stays, the others may still go.
                    else -> unresolved = true
                }
            } catch (_: Throwable) {
                unresolved = true
            }
        }
        return if (unresolved) CrashUploadResult.RETRY else CrashUploadResult.SUCCESS
    }
}
