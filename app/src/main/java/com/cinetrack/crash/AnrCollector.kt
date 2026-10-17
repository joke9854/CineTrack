package com.cinetrack.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.InputStream

/** Picks up ANRs the system recorded for earlier runs (API 30+) and queues them in the outbox. */
object AnrCollector {
    private const val MAX_TRACE_BYTES = 64 * 1024
    private const val MAX_EXITS = 10

    /** Blocking; call off the main thread. */
    fun collect(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return
            val lastSeen = CrashReportSettings.anrLastSeen(context)
            val anrs = manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
                .filter { it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp > lastSeen }
                .sortedBy { it.timestamp }
            if (anrs.isEmpty()) return
            // A switched-off setting still consumes the entries, so turning it on never uploads old ones.
            if (CrashReportSettings.isEnabled(context)) {
                val outbox = CrashOutbox.forContext(context)
                val build = currentBuildInfo()
                anrs.forEach { exit ->
                    val trace = runCatching { exit.traceInputStream?.use { readCapped(it) } }.getOrNull().orEmpty()
                    outbox.write(CrashReportMapper.fromAnr(exit.pid, exit.timestamp, trace, build))
                }
            }
            CrashReportSettings.setAnrLastSeen(context, anrs.last().timestamp)
        }
    }

    internal fun readCapped(input: InputStream, max: Int = MAX_TRACE_BYTES): String {
        val buffer = ByteArray(max)
        var read = 0
        while (read < max) {
            val n = input.read(buffer, read, max - read)
            if (n < 0) break
            read += n
        }
        return String(buffer, 0, read, Charsets.UTF_8)
    }
}
