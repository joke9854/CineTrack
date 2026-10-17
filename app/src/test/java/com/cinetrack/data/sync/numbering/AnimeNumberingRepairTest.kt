package com.cinetrack.data.sync.numbering

import com.cinetrack.data.local.WatchHistoryEntity
import com.cinetrack.data.remote.SimklEpisode
import com.cinetrack.data.remote.SimklIds
import com.cinetrack.data.remote.SimklLibraryItem
import com.cinetrack.data.remote.SimklMedia
import com.cinetrack.data.remote.SimklSeason
import com.cinetrack.data.sync.MediaIds
import com.cinetrack.data.sync.TrackedEpisodeState
import com.cinetrack.data.sync.TrackingSnapshot
import com.cinetrack.domain.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRepairStore(
    val dao: FakeNumberingDao,
    var history: List<WatchHistoryEntity>,
    var baseline: TrackingSnapshot,
    val anime: List<SimklLibraryItem>,
    val activeShows: Set<Int> = emptySet(),
) : AnimeRepairStore {
    var done = false
    val doneShows = mutableSetOf<Int>()
    val logs = mutableListOf<String>()
    var refreshes = 0
    val invalidated = mutableListOf<Int>()
    override suspend fun isDone() = done
    override suspend fun markDone() { done = true }
    override suspend fun doneShows() = doneShows.toSet()
    override suspend fun markShowDone(showId: Int) { doneShows += showId }
    override suspend fun fetchSimklAnime() = anime
    override suspend fun hasActiveWork(showId: Int) = showId in activeShows
    override suspend fun episodeHistory(showId: Int) = history.filter { it.mediaId == showId }
    override suspend fun translatedMapping(showId: Int) =
        dao.forShow(showId, "simkl").filter { it.status == "translated" }
            .associate { (it.schemeSeason to it.schemeEpisode) to (it.tmdbSeason!! to it.tmdbEpisode!!) }
    override suspend fun answered(showId: Int, coords: Collection<Pair<Int, Int>>): Boolean {
        val known = dao.forShow(showId, "simkl").map { it.schemeSeason to it.schemeEpisode }.toSet()
        return coords.all { it in known }
    }
    override suspend fun apply(showId: Int, plan: RenumberPlan) {
        plan.moves.forEach { (id, s, e) ->
            history = history.map { if (it.id == id) it.copy(season = s, episodeNumber = e) else it }
        }
        history = history.filterNot { it.id in plan.mergedRowIds }
    }
    override suspend fun rewriteBaseline(showId: Int, mapping: Map<Pair<Int, Int>, Pair<Int, Int>>) {
        baseline = renumberBaseline(baseline, showId, mapping)
    }
    override suspend fun refreshProgress() { refreshes++ }
    override fun invalidate(showId: Int) { invalidated += showId }
    override fun log(message: String) { logs += message }
}

class AnimeNumberingRepairTest {
    private val naruto = 46260
    private fun row(id: Long, s: Int, e: Int, at: String, show: Int = naruto) =
        WatchHistoryEntity(id = id, mediaType = MediaType.TV.name, mediaId = show, season = s, episodeNumber = e, watchedAt = at)

    private fun animeList(vararg coords: Pair<Int, Int>) = listOf(
        SimklLibraryItem(
            show = SimklMedia(ids = SimklIds(simkl = 1, tmdb = naruto.toString())),
            seasons = coords.groupBy({ it.first }, { SimklEpisode(it.second) }).map { (s, e) -> SimklSeason(s, e) },
        ),
    )

    private fun transport() = FakeTransport { _, c ->
        if (c.first == 1 && c.second >= 183) translated(c.first, c.second, 4, c.second) else same(c.first, c.second)
    }

    private fun repair(store: FakeRepairStore, transport: FakeTransport = transport()) =
        AnimeNumberingRepair(EpisodeNumbering(store.dao, transport, { store.logs += it }), store)

    private fun ep(s: Int, e: Int, watched: Boolean) = TrackedEpisodeState(MediaIds(tmdb = naruto.toLong()), s, e, watched)

    @Test
    fun `renumbers in place and merges only exact duplicates`() {
        val rows = listOf(
            row(1, 1, 183, "2026-01-01T00:00:00Z"),
            row(2, 4, 184, "2026-01-02T00:00:00Z"),
            row(3, 1, 184, "2026-01-02T00:00:00Z"),
            row(4, 1, 185, "2026-01-03T00:00:00Z"),
            row(5, 4, 185, "2026-02-03T00:00:00Z"),
            row(6, 1, 5, "2020-01-01T00:00:00Z"),
        )
        val mapping = mapOf((1 to 183) to (4 to 183), (1 to 184) to (4 to 184), (1 to 185) to (4 to 185))
        val plan = planRenumber(rows, mapping)
        assertEquals(listOf(Triple(1L, 4, 183), Triple(4L, 4, 185)), plan.moves)
        assertEquals(listOf(3L), plan.mergedRowIds)
    }

    @Test
    fun `an equal instant written differently still counts as the same play`() {
        val rows = listOf(row(1, 4, 183, "2026-01-01T00:00:00Z"), row(2, 1, 183, "2026-01-01T00:00:00.000Z"))
        val plan = planRenumber(rows, mapOf((1 to 183) to (4 to 183)))
        assertEquals(listOf(2L), plan.mergedRowIds)
        assertTrue(plan.moves.isEmpty())
    }

    @Test
    fun `end to end renumber rewrites history and baseline and logs`() = runBlocking {
        val store = FakeRepairStore(
            FakeNumberingDao(),
            listOf(row(1, 1, 183, "2026-01-01T00:00:00Z"), row(2, 1, 184, "2026-01-02T00:00:00Z"), row(3, 4, 184, "2026-01-02T00:00:00Z")),
            TrackingSnapshot(episodes = listOf(ep(1, 183, true), ep(1, 184, false), ep(4, 184, true), ep(1, 5, true))),
            animeList(1 to 183, 1 to 184, 1 to 5),
        )
        repair(store).runIfNeeded()
        assertEquals(listOf(Triple(1L, 4, 183), Triple(3L, 4, 184)), store.history.map { Triple(it.id, it.season!!, it.episodeNumber!!) })
        assertEquals(listOf("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"), store.history.map { it.watchedAt })
        assertEquals(
            listOf(Triple(4, 183, true), Triple(4, 184, true), Triple(1, 5, true)),
            store.baseline.episodes.map { Triple(it.season, it.episode, it.watched) },
        )
        assertTrue(store.logs.contains("Anime numbering: show=$naruto renumbered=1 merged=1"))
        assertTrue(store.done)
        assertEquals(setOf(naruto), store.doneShows)
        assertEquals(1, store.refreshes)
        assertEquals(listOf(naruto), store.invalidated)
    }

    @Test
    fun `a show with active work is skipped and retried later`() = runBlocking {
        val store = FakeRepairStore(
            FakeNumberingDao(), listOf(row(1, 1, 183, "2026-01-01T00:00:00Z")),
            TrackingSnapshot(episodes = listOf(ep(1, 183, true))), animeList(1 to 183), activeShows = setOf(naruto),
        )
        repair(store).runIfNeeded()
        assertEquals(1, store.history.single().season)
        assertEquals(1, store.baseline.episodes.single().season)
        assertFalse(store.done)
        assertTrue(store.doneShows.isEmpty())
    }

    @Test
    fun `unavailable translation or a finished repair does nothing`() = runBlocking {
        val store = FakeRepairStore(
            FakeNumberingDao(), listOf(row(1, 1, 183, "2026-01-01T00:00:00Z")), TrackingSnapshot(), animeList(1 to 183),
        )
        repair(store, FakeTransport(available = false) { _, c -> translated(c.first, c.second, 4, c.second) }).runIfNeeded()
        assertEquals(1, store.history.single().season)
        assertFalse(store.done)
        store.done = true
        repair(store).runIfNeeded()
        assertEquals(1, store.history.single().season)
    }

    @Test
    fun `failed translation leaves the show for the next run`() = runBlocking {
        val store = FakeRepairStore(
            FakeNumberingDao(), listOf(row(1, 1, 183, "2026-01-01T00:00:00Z")),
            TrackingSnapshot(episodes = listOf(ep(1, 183, true))), animeList(1 to 183),
        )
        repair(store, FakeTransport(failure = java.io.IOException()) { _, c -> translated(c.first, c.second, 4, c.second) }).runIfNeeded()
        assertEquals(1, store.history.single().season)
        assertFalse(store.done)
    }

    @Test
    fun `settled follows the done flag and the per show set`() = runBlocking {
        val store = FakeRepairStore(FakeNumberingDao(), emptyList(), TrackingSnapshot(), emptyList())
        val r = AnimeNumberingRepair(EpisodeNumbering(store.dao, null), store)
        assertFalse(r.isSettled(naruto))
        store.doneShows += naruto
        assertTrue(r.isSettled(naruto))
        assertFalse(r.isSettled(1))
        store.done = true
        assertTrue(r.isSettled(1))
    }

    @Test
    fun `baseline collisions keep the watched line`() {
        val out = renumberBaseline(
            TrackingSnapshot(episodes = listOf(ep(1, 183, false), ep(4, 183, true), ep(1, 5, true))),
            naruto, mapOf((1 to 183) to (4 to 183)),
        )
        assertEquals(listOf(Triple(4, 183, true), Triple(1, 5, true)), out.episodes.map { Triple(it.season, it.episode, it.watched) })
    }
}
