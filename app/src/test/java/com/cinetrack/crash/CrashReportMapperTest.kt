package com.cinetrack.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.UUID

class CrashReportMapperTest {
    @get:Rule val temp = TemporaryFolder()

    private val build = CrashBuildInfo("0.99.64", 9964, "debug", 34, "google Pixel 8")
    private val id = "6f1c1c2e-8a3b-4c52-9d27-0a1b2c3d4e5f"

    private fun fields(
        stack: String? = "java.lang.IllegalStateException: boom token=abc123\n\tat a.B.c(B.kt:1)",
        date: String? = "2026-10-17T14:05:09.123+0200",
    ) = AcraFields(
        reportId = id,
        appVersionName = "0.99.64",
        appVersionCode = "9964",
        phoneModel = "Pixel 8",
        brand = "google",
        stackTrace = stack,
        crashDate = date,
        threadDetails = """{"id":2,"name":"DefaultDispatcher-worker-3","priority":5}""",
    )

    @Test
    fun mapsAcraFieldsToTheContract() {
        val dto = CrashReportMapper.fromAcra(fields(), build)!!
        assertEquals(id, dto.reportId)
        assertEquals("crash", dto.kind)
        assertEquals("2026-10-17T14:05:09.123+02:00", dto.occurredAt)
        assertEquals("0.99.64", dto.appVersion)
        assertEquals(9964, dto.versionCode)
        assertEquals("debug", dto.buildType)
        assertEquals(34, dto.androidSdk)
        assertEquals("google Pixel 8", dto.deviceModel)
        assertEquals("java.lang.IllegalStateException", dto.exceptionClass)
        assertEquals("DefaultDispatcher-worker-3", dto.thread)
        assertFalse(dto.stackTrace.contains("abc123"))
        assertTrue(dto.stackTrace.contains("token=<redacted>"))
    }

    @Test
    fun missingStackTraceYieldsNoReport() {
        assertNull(CrashReportMapper.fromAcra(fields(stack = " "), build))
    }

    @Test
    fun unparseableDateFallsBackToNow() {
        val dto = CrashReportMapper.fromAcra(fields(date = "garbage"), build, Instant.parse("2026-01-02T03:04:05Z"))!!
        assertEquals("2026-01-02T03:04:05Z", dto.occurredAt)
    }

    @Test
    fun anrUsesTheExitTimestampAndAStableId() {
        val ts = 1_760_000_000_123L
        val dto = CrashReportMapper.fromAnr(4242, ts, "\"main\" prio=5 tid=1 Blocked", build)
        assertEquals(UUID.nameUUIDFromBytes("anr:4242:$ts".toByteArray()).toString(), dto.reportId)
        assertEquals("anr", dto.kind)
        assertEquals(Instant.ofEpochMilli(ts), Instant.parse(dto.occurredAt.replace("+00:00", "Z")))
        assertNull(dto.exceptionClass)
        assertEquals(dto.reportId, CrashReportMapper.fromAnr(4242, ts, "other", build).reportId)
        assertNotEquals(dto.reportId, CrashReportMapper.fromAnr(4243, ts, "x", build).reportId)
    }

    @Test
    fun intakeWritesNothingWhenCrashReportingIsOff() {
        val outbox = CrashOutbox(temp.newFolder("off"))
        CrashReportIntake(outbox, { false }, { build }).accept(fields())
        assertTrue(outbox.isEmpty())
        CrashReportIntake(outbox, { true }, { build }).accept(fields())
        assertEquals(1, outbox.files().size)
    }

    @Test
    fun outboxWritesAtomicallyAndKeepsTheNewest20() {
        val outbox = CrashOutbox(temp.newFolder("crash-outbox"))
        repeat(23) { i ->
            val dto = CrashReportMapper.fromAnr(i, 1_000L + i, "t$i", build)
            outbox.write(dto)
            outbox.files().firstOrNull { it.name.startsWith(dto.reportId) }?.setLastModified(1_000_000L + i * 1000)
        }
        val files = outbox.files()
        assertEquals(20, files.size)
        assertTrue(files.none { it.name.endsWith(".tmp") })
        val kept = files.map { outbox.read(it)!!.stackTrace }
        assertFalse(kept.contains("t0"))
        assertFalse(kept.contains("t2"))
        assertTrue(kept.contains("t22"))
    }
}
