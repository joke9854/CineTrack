package com.cinetrack.crash

import com.cinetrack.data.sync.floppy.FloppyCapabilities
import com.cinetrack.data.sync.floppy.FloppySession
import com.cinetrack.data.sync.floppy.network.FloppyApiClientFactory
import com.cinetrack.data.sync.floppy.network.FloppyRemoteDataSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashReportUploaderTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var outbox: CrashOutbox
    private lateinit var session: FloppySession
    private val build = CrashBuildInfo("0.99.64", 9964, "debug", 34, "google Pixel 8")
    private var enabled = true
    private var capable = true

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        outbox = CrashOutbox(temp.newFolder("crash-outbox"))
        session = FloppySession(
            instanceId = "instance",
            baseUrl = server.url("/proxy/").toString(),
            accountIdentity = "alice",
            credentialAlias = "alias",
            apiKey = "secret",
            capabilities = FloppyCapabilities(canCrashReports = true),
            serverVersion = "test",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun uploader() = CrashReportUploader(
        outbox = outbox,
        enabled = { enabled },
        session = { session },
        hasCapability = { capable },
        remote = FloppyRemoteDataSource(FloppyApiClientFactory()),
    )

    private fun queue(count: Int) {
        repeat(count) { i ->
            outbox.write(CrashReportMapper.fromAnr(i, 5_000L + i, "trace $i", build))
            // Distinct, ordered modification times so the send order is the creation order.
            outbox.files().first { outbox.read(it)!!.stackTrace == "trace $i" }.setLastModified(1_000_000L + i * 1000)
        }
    }

    private fun answer(code: Int, body: String = "") =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private fun run() = runBlocking { uploader().upload() }

    @Test
    fun createdAndAlreadyReceivedDeleteTheirFiles() {
        queue(2)
        answer(201, """{"report_id":"x","status":"created"}""")
        answer(200, """{"report_id":"x","status":"already_received"}""")

        assertEquals(CrashUploadResult.SUCCESS, run())

        assertTrue(outbox.isEmpty())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/proxy/api/v1/cinetrack/crash-reports/", request.path)
        assertEquals("secret", request.getHeader("X-API-Key"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"kind\":\"anr\""))
        assertTrue(body.contains("\"report_id\""))
    }

    @Test
    fun badRequestDropsTheInvalidReport() {
        queue(1)
        answer(400, """{"detail":"bad"}""")
        assertEquals(CrashUploadResult.SUCCESS, run())
        assertTrue(outbox.isEmpty())
    }

    @Test
    fun unauthorizedAndForbiddenStopAndKeepEverything() {
        for (code in listOf(401, 403)) {
            queue(2)
            val before = server.requestCount
            answer(code)
            assertEquals(CrashUploadResult.SUCCESS, run())
            assertEquals(2, outbox.files().size)
            assertEquals(1, server.requestCount - before)
            outbox.clear()
        }
    }

    @Test
    fun rateLimitKeepsFilesAndRetriesWithoutSendingTheRest() {
        queue(2)
        answer(429)
        assertEquals(CrashUploadResult.RETRY, run())
        assertEquals(2, outbox.files().size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun serverErrorKeepsThatFileAndRetries() {
        queue(1)
        answer(500)
        assertEquals(CrashUploadResult.RETRY, run())
        assertEquals(1, outbox.files().size)
    }

    @Test
    fun unknownStatusInTheBodyIsNotAnAck() {
        queue(1)
        answer(200, """{"report_id":"x","status":"surprise"}""")
        assertEquals(CrashUploadResult.RETRY, run())
        assertEquals(1, outbox.files().size)
    }

    @Test
    fun networkFailureKeepsFilesAndRetries() {
        queue(1)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(CrashUploadResult.RETRY, run())
        assertEquals(1, outbox.files().size)
    }

    @Test
    fun mixedBatchDeletesSuccessesAndKeepsOnlyTheFailure() {
        queue(3)
        answer(201, """{"report_id":"a","status":"created"}""")
        answer(500)
        answer(200, """{"report_id":"c","status":"already_received"}""")

        assertEquals(CrashUploadResult.RETRY, run())

        val left = outbox.files().map { outbox.read(it)!!.stackTrace }
        assertEquals(listOf("trace 1"), left)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun missingCapabilitySendsNothingAndKeepsFiles() {
        queue(1)
        capable = false
        assertEquals(CrashUploadResult.SUCCESS, run())
        assertEquals(0, server.requestCount)
        assertEquals(1, outbox.files().size)
    }

    @Test
    fun settingOffSendsNothingAndClearsTheOutbox() {
        queue(2)
        enabled = false
        assertEquals(CrashUploadResult.SUCCESS, run())
        assertEquals(0, server.requestCount)
        assertTrue(outbox.isEmpty())
    }

    @Test
    fun capabilityLookupFailureRetriesWithoutSending() {
        queue(1)
        val failing = CrashReportUploader(outbox, { true }, { session }, { error("offline") }, FloppyRemoteDataSource(FloppyApiClientFactory()))
        assertEquals(CrashUploadResult.RETRY, runBlocking { failing.upload() })
        assertEquals(0, server.requestCount)
        assertFalse(outbox.isEmpty())
    }
}
