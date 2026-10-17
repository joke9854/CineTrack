package com.cinetrack.data.sync.numbering

import com.cinetrack.data.local.EpisodeNumberingDao
import com.cinetrack.data.local.EpisodeNumberingEntity
import com.cinetrack.data.remote.SimklEpisode
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklLibraryItem
import com.cinetrack.data.remote.SimklMedia
import com.cinetrack.data.remote.SimklSeason
import com.cinetrack.data.remote.SimklSyncItem
import com.cinetrack.data.sync.SyncOperation
import com.cinetrack.data.sync.SyncOperationType
import com.cinetrack.data.sync.floppy.FloppyEpisodeTranslateCoord
import com.cinetrack.data.sync.floppy.FloppyEpisodeTranslateResult
import com.cinetrack.data.sync.simkl.simklEpisodeItems
import com.cinetrack.data.sync.simkl.simklEpisodeTarget
import com.cinetrack.domain.MediaType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeNumberingDao : EpisodeNumberingDao {
    val rows = linkedMapOf<List<Any>, EpisodeNumberingEntity>()
    private fun key(r: EpisodeNumberingEntity) = listOf(r.showId, r.scheme, r.schemeSeason, r.schemeEpisode)
    override suspend fun upsert(items: List<EpisodeNumberingEntity>) { items.forEach { rows[key(it)] = it } }
    override suspend fun forShow(showId: Int, scheme: String) = rows.values.filter { it.showId == showId && it.scheme == scheme }
    override suspend fun row(showId: Int, scheme: String, schemeSeason: Int, schemeEpisode: Int) =
        rows[listOf(showId, scheme, schemeSeason, schemeEpisode)]
    override suspend fun byTmdb(showId: Int, scheme: String, tmdbSeason: Int, tmdbEpisode: Int) =
        forShow(showId, scheme).filter { it.tmdbSeason == tmdbSeason && it.tmdbEpisode == tmdbEpisode }
}

internal class FakeTransport(
    var available: Boolean = true,
    var failure: Throwable? = null,
    private val answer: (from: String, coord: Pair<Int, Int>) -> FloppyEpisodeTranslateResult,
) : EpisodeTranslateTransport {
    val calls = mutableListOf<Triple<Int, String, List<Pair<Int, Int>>>>()
    override suspend fun isAvailable() = available
    override suspend fun translate(tmdbId: Int, from: String, coords: List<Pair<Int, Int>>): List<FloppyEpisodeTranslateResult>? {
        if (!available) return null
        calls += Triple(tmdbId, from, coords)
        failure?.let { throw it }
        return coords.map { answer(from, it) }
    }
}

internal fun translated(s: Int, e: Int, ts: Int, te: Int, absolute: Int? = null, tvdb: Pair<Int, Int>? = null) =
    FloppyEpisodeTranslateResult(
        s, e, "translated", FloppyEpisodeTranslateCoord(ts, te), absolute,
        tvdb?.let { FloppyEpisodeTranslateCoord(it.first, it.second) },
    )

internal fun same(s: Int, e: Int) =
    FloppyEpisodeTranslateResult(s, e, "same", FloppyEpisodeTranslateCoord(s, e))

class EpisodeNumberingTest {
    private val naruto = 46260
    private fun narutoAnswer(from: String, c: Pair<Int, Int>): FloppyEpisodeTranslateResult =
        if (from == "tracker") {
            if (c.first == 1 && c.second >= 183) translated(c.first, c.second, 4, c.second, absolute = c.second)
            else same(c.first, c.second)
        } else {
            translated(c.first, c.second, c.first, c.second, absolute = c.second)
        }

    private fun numbering(dao: FakeNumberingDao, transport: FakeTransport?, logs: MutableList<String> = mutableListOf(), now: Long = 1_000L) =
        EpisodeNumbering(dao, transport, { logs += it }, { now })

    @Test
    fun `translate upserts rows from Floppy answers`() = runBlocking {
        val dao = FakeNumberingDao()
        val transport = FakeTransport { from, c -> narutoAnswer(from, c) }
        numbering(dao, transport).translateSimkl(naruto, listOf(1 to 183, 1 to 5))
        assertEquals(listOf("tracker"), transport.calls.map { it.second })
        val row = dao.row(naruto, "simkl", 1, 183)!!
        assertEquals("translated", row.status)
        assertEquals(4, row.tmdbSeason)
        assertEquals(183, row.tmdbEpisode)
        val same = dao.row(naruto, "simkl", 1, 5)!!
        assertEquals("same", same.status)
        assertEquals(1 to 5, same.tmdbSeason!! to same.tmdbEpisode!!)
    }

    @Test
    fun `known rows are not asked again and old not_found rows are`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(
            listOf(
                EpisodeNumberingEntity(naruto, "simkl", 1, 1, "same", 1, 1, updatedAt = 0),
                EpisodeNumberingEntity(naruto, "simkl", 1, 2, "not_found", updatedAt = 0),
                EpisodeNumberingEntity(naruto, "simkl", 1, 3, "not_found", updatedAt = 8L * 24 * 3600 * 1000 - 100),
            ),
        )
        val transport = FakeTransport { from, c -> narutoAnswer(from, c) }
        numbering(dao, transport, now = 8L * 24 * 3600 * 1000).translateSimkl(naruto, listOf(1 to 1, 1 to 2, 1 to 3))
        assertEquals(listOf(listOf(1 to 2)), transport.calls.map { it.third })
    }

    @Test
    fun `failures and metadata_unavailable write nothing and never throw`() = runBlocking {
        val dao = FakeNumberingDao()
        val logs = mutableListOf<String>()
        val failing = FakeTransport(failure = java.io.IOException("secret-body")) { f, c -> narutoAnswer(f, c) }
        numbering(dao, failing, logs).translateSimkl(naruto, listOf(1 to 183))
        assertTrue(dao.rows.isEmpty())
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("show=$naruto") && logs.single().contains("count=1") && logs.single().contains("IOException"))
        assertTrue(!logs.single().contains("secret-body"))
        val unavailable = FakeTransport { _, c -> FloppyEpisodeTranslateResult(c.first, c.second, "metadata_unavailable") }
        numbering(dao, unavailable).translateSimkl(naruto, listOf(1 to 183))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `requests are chunked at 400`() = runBlocking {
        val dao = FakeNumberingDao()
        val transport = FakeTransport { f, c -> narutoAnswer(f, c) }
        numbering(dao, transport).translateSimkl(naruto, (1..401).map { 1 to it })
        assertEquals(listOf(400, 1), transport.calls.map { it.third.size })
    }

    @Test
    fun `toLocal uses translated and same rows only`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(
            listOf(
                EpisodeNumberingEntity(naruto, "simkl", 1, 183, "translated", 4, 183, updatedAt = 1),
                EpisodeNumberingEntity(naruto, "simkl", 1, 9, "not_found", updatedAt = 1),
            ),
        )
        val n = numbering(dao, null)
        assertEquals(4 to 183, n.toLocal(naruto, 1, 183))
        assertEquals(1 to 9, n.toLocal(naruto, 1, 9))
        assertEquals(2 to 2, n.toLocal(naruto, 2, 2))
    }

    @Test
    fun `toSimkl prefers the stored reverse mapping`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(listOf(EpisodeNumberingEntity(naruto, "simkl", 1, 183, "translated", 4, 183, updatedAt = 1)))
        val transport = FakeTransport { f, c -> narutoAnswer(f, c) }
        assertEquals(SimklTarget.Native(1, 183), numbering(dao, transport).toSimkl(naruto, 4, 183))
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `continuous numbering asks Floppy for the absolute number`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(listOf(EpisodeNumberingEntity(naruto, "simkl", 1, 183, "translated", 4, 183, absolute = 183, updatedAt = 1)))
        val transport = FakeTransport { from, c ->
            if (from == "tmdb") translated(c.first, c.second, c.first, c.second, absolute = 190, tvdb = 5 to 1) else narutoAnswer(from, c)
        }
        assertEquals(SimklTarget.Native(1, 190), numbering(dao, transport).toSimkl(naruto, 4, 190))
        assertEquals(listOf("tmdb"), transport.calls.map { it.second })
        // the answer is used in memory only
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `non continuous numbering with a tvdb answer sends tvdb`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(
            listOf(
                EpisodeNumberingEntity(naruto, "simkl", 1, 183, "translated", 4, 183, updatedAt = 1),
                EpisodeNumberingEntity(naruto, "simkl", 2, 1, "same", 2, 1, updatedAt = 1),
            ),
        )
        val transport = FakeTransport { _, c -> translated(c.first, c.second, c.first, c.second, absolute = 300, tvdb = 6 to 2) }
        assertEquals(SimklTarget.Tvdb(6, 2), numbering(dao, transport).toSimkl(naruto, 5, 2))
    }

    @Test
    fun `unchanged for ordinary shows without any request`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(listOf(EpisodeNumberingEntity(7, "simkl", 1, 1, "same", 1, 1, updatedAt = 1)))
        val transport = FakeTransport { f, c -> narutoAnswer(f, c) }
        assertEquals(SimklTarget.Unchanged, numbering(dao, transport).toSimkl(7, 3, 4))
        assertEquals(SimklTarget.Unchanged, numbering(dao, transport).toSimkl(99, 1, 1))
        assertTrue(transport.calls.isEmpty())
        dao.upsert(listOf(EpisodeNumberingEntity(naruto, "simkl", 1, 183, "translated", 4, 183, updatedAt = 1)))
        val down = FakeTransport(available = false) { f, c -> narutoAnswer(f, c) }
        assertEquals(SimklTarget.Unchanged, numbering(dao, down).toSimkl(naruto, 4, 200))
    }

    @Test
    fun `unsettled shows stay in Simkl numbering both ways`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(listOf(EpisodeNumberingEntity(naruto, "simkl", 1, 183, "translated", 4, 183, updatedAt = 1)))
        val n = EpisodeNumbering(dao, null, settled = { false })
        assertEquals(SimklTarget.Unchanged, n.toSimkl(naruto, 4, 183))
        val pulled = item(naruto, SimklSeason(1, listOf(SimklEpisode(183, "2026-01-01T00:00:00Z"))))
        assertEquals(pulled, n.localizeItem(naruto, pulled))
    }

    private fun item(tmdb: Int, vararg seasons: SimklSeason) = SimklLibraryItem(
        status = "watching",
        show = SimklMedia(title = "Naruto", ids = SimklIds(simkl = 1, tmdb = tmdb.toString())),
        seasons = seasons.toList(),
    )

    @Test
    fun `pulled anime is renumbered keeping every exact watched_at and colliding entries`() = runBlocking {
        val dao = FakeNumberingDao()
        val transport = FakeTransport { _, c ->
            when (c) {
                1 to 183, 1 to 184 -> translated(c.first, c.second, 4, 183, 183) // two Simkl entries, one TMDB coordinate
                else -> same(c.first, c.second)
            }
        }
        val pulled = item(
            naruto,
            SimklSeason(
                1,
                listOf(
                    SimklEpisode(5, "2020-01-05T10:00:00Z"),
                    SimklEpisode(183, "2026-02-03T04:05:06Z", "A"),
                    SimklEpisode(184, "2026-02-04T05:06:07Z", "B"),
                ),
            ),
        )
        val local = numbering(dao, transport).localizeItem(naruto, pulled)
        assertEquals(listOf(1, 4), local.seasons.map { it.number })
        assertEquals(listOf(5), local.seasons[0].episodes.map { it.number })
        assertEquals(listOf(183, 183), local.seasons[1].episodes.map { it.number })
        assertEquals(listOf("2026-02-03T04:05:06Z", "2026-02-04T05:06:07Z"), local.seasons[1].episodes.map { it.watchedAt })
        assertEquals(listOf("A", "B"), local.seasons[1].episodes.map { it.title })
        val none = numbering(FakeNumberingDao(), FakeTransport(failure = RuntimeException()) { f, c -> narutoAnswer(f, c) })
        assertEquals(pulled.seasons, none.localizeItem(naruto, pulled).seasons)
    }

    private fun watched(show: Int, s: Int, e: Int, at: String) =
        SyncOperation("write:$show$s$e", SyncOperationType.EPISODE_WATCHED, MediaType.TV, show, "s", payload = "$s:$e:$at", sourceVersion = 1L)

    @Test
    fun `push sends Simkl native numbers with the exact time`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(listOf(EpisodeNumberingEntity(naruto, "simkl", 1, 183, "translated", 4, 183, updatedAt = 1)))
        val op = watched(naruto, 4, 183, "2026-02-03T04:05:06Z")
        val item = simklEpisodeItems(numbering(dao, null), naruto, listOf(op.simklEpisodeTarget())).single()
        assertEquals(naruto.toString(), item.ids.tmdb)
        assertEquals(1, item.seasons.single().number)
        assertEquals(183, item.seasons.single().episodes.single().number)
        assertEquals("2026-02-03T04:05:06Z", item.seasons.single().episodes.single().watchedAt)
        assertNull(item.useTvdbAnimeSeasons)
    }

    @Test
    fun `push of a tvdb target sets use_tvdb_anime_seasons and omits it otherwise`() = runBlocking {
        val dao = FakeNumberingDao()
        dao.upsert(
            listOf(
                EpisodeNumberingEntity(naruto, "simkl", 1, 183, "translated", 4, 183, updatedAt = 1),
                EpisodeNumberingEntity(naruto, "simkl", 2, 1, "same", 2, 1, updatedAt = 1),
            ),
        )
        val transport = FakeTransport { _, c -> translated(c.first, c.second, c.first, c.second, tvdb = 6 to 2) }
        val item = simklEpisodeItems(numbering(dao, transport), naruto, listOf(watched(naruto, 5, 2, "2026-03-01T00:00:00Z").simklEpisodeTarget())).single()
        assertEquals(true, item.useTvdbAnimeSeasons)
        assertEquals(6, item.seasons.single().number)
        assertEquals(2, item.seasons.single().episodes.single().number)
        val json = Json { explicitNulls = false }
        val plain = SimklSyncItem(ids = SimklIds(tmdb = "1"))
        assertTrue(!json.encodeToString(SimklSyncItem.serializer(), plain).contains("use_tvdb"))
        assertTrue(json.encodeToString(SimklSyncItem.serializer(), item).contains("\"use_tvdb_anime_seasons\":true"))
    }

    @Test
    fun `push without numbering is unchanged`() = runBlocking {
        val items = simklEpisodeItems(null, 7, listOf(watched(7, 2, 3, "2026-01-01T00:00:00Z").simklEpisodeTarget()))
        assertEquals(2, items.single().seasons.single().number)
        assertEquals(3, items.single().seasons.single().episodes.single().number)
    }
}
