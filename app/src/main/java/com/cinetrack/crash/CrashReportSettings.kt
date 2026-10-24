package com.cinetrack.crash

import android.content.Context
import android.content.SharedPreferences

/**
 * The crash-reporting switch, in its own plain preferences file so the main
 * and `:acra` processes can both read it. It is read fresh on every call.
 */
object CrashReportSettings {
    const val FILE = "cinetrack_crash_reporting"
    private const val KEY_ENABLED = "send_crash_reports"
    private const val KEY_ANR_SEEN = "anr_last_seen"

    @Suppress("DEPRECATION")
    private fun prefs(context: Context): SharedPreferences =
        // MULTI_PROCESS makes the other process's edits visible without a restart.
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)

    /** Off until the user turns it on in Floppy settings: reports leave the device. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    fun anrLastSeen(context: Context): Long = prefs(context).getLong(KEY_ANR_SEEN, 0L)

    fun setAnrLastSeen(context: Context, timestamp: Long) {
        prefs(context).edit().putLong(KEY_ANR_SEEN, timestamp).apply()
    }
}
