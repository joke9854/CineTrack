package com.cinetrack.data.importer

import com.cinetrack.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalCsvTest {
    @Test
    fun letterboxdRatingsBecomeScoresOutOfTen() {
        val (source, rows) = ExternalCsv.parse(
            "ratings.csv",
            "Date,Name,Year,Letterboxd URI,Rating\n2024-01-02,\"Dune: Part Two\",2024,https://boxd.it/x,4.5\n2024-01-03,Alien,1979,https://boxd.it/y,0.5\n",
        )!!
        assertEquals(ImportSource.LETTERBOXD, source)
        assertEquals(listOf(9, 1), rows.map(ImportRow::score))
        assertEquals("Dune: Part Two", rows.first().title)
        assertFalse(rows.first().watchlist)
    }

    @Test
    fun letterboxdWatchlistAndDiaryAreToldApart() {
        val watchlist = ExternalCsv.parse("watchlist.csv", "Date,Name,Year,Letterboxd URI\n2024-01-02,Heat,1995,https://boxd.it/z\n")!!.second
        assertTrue(watchlist.single().watchlist)
        val diary = ExternalCsv.parse("diary.csv", "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n2024-01-02,Heat,1995,https://boxd.it/z,5,,,2024-01-01\n")!!.second
        assertTrue(diary.single().watched)
        assertEquals(10, diary.single().score)
    }

    @Test
    fun imdbWatchlistKeepsIdsAndSkipsEpisodes() {
        val csv = "Position,Const,Created,Modified,Description,Title,Original Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors,Your Rating,Date Rated\n" +
            "1,tt0903747,2024-01-01,,,Breaking Bad,Breaking Bad,https://imdb,tvSeries,9.5,49,2008,Drama,1,2008-01-20,,,\n" +
            "2,tt0959621,2024-01-01,,,Pilot,Pilot,https://imdb,tvEpisode,9,58,2008,Drama,1,2008-01-20,,,\n" +
            "3,tt0133093,2024-01-01,,,The Matrix,The Matrix,https://imdb,movie,8.7,136,1999,Action,1,1999-03-31,,8,2024-01-01\n"
        val (source, rows) = ExternalCsv.parse("WATCHLIST.csv", csv)!!
        assertEquals(ImportSource.IMDB, source)
        assertEquals(listOf("tt0903747", "tt0133093"), rows.map(ImportRow::imdbId))
        assertEquals(listOf(MediaType.TV, MediaType.MOVIE), rows.map(ImportRow::type))
        assertTrue(rows.all(ImportRow::watchlist))
        assertEquals(listOf(null, 8), rows.map(ImportRow::score))
    }

    @Test
    fun quotedFieldsKeepCommasQuotesAndLineBreaks() {
        val rows = ExternalCsv.csv("a,\"b, \"\"c\"\"\",\"d\r\ne\"\r\nf,g")
        assertEquals(listOf(listOf("a", "b, \"c\"", "d\r\ne"), listOf("f", "g")), rows)
    }

    @Test
    fun otherFilesAreNotRecognised() {
        assertNull(ExternalCsv.parse("notes.csv", "foo,bar\n1,2\n"))
    }
}
