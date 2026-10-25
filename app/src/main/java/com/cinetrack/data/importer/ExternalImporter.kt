package com.cinetrack.data.importer

import android.content.Context
import android.net.Uri
import com.cinetrack.data.library.LibraryRepository
import com.cinetrack.data.local.AppDatabase
import com.cinetrack.data.local.toDomain
import com.cinetrack.data.personal.PersonalRepository
import com.cinetrack.data.remote.TmdbService
import com.cinetrack.data.repository.toEntity
import com.cinetrack.domain.LibraryStatus
import com.cinetrack.domain.MediaCard
import com.cinetrack.domain.MediaType
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

enum class ImportSource { LETTERBOXD, IMDB }

/** What one row asks for. Watched rows are counted but not imported (see [ImportSummary.watchedSkipped]). */
data class ImportRow(
    val title: String,
    val year: Int?,
    val imdbId: String? = null,
    val type: MediaType? = null,
    val watchlist: Boolean = false,
    /** 1-10. */
    val score: Int? = null,
    val watched: Boolean = false,
)

data class ImportSummary(
    val source: ImportSource,
    val rows: Int,
    val addedToWatchlist: Int,
    val rated: Int,
    /** Watched rows from a diary or watched list: they carry only a date, so they are not turned into plays. */
    val watchedSkipped: Int,
    val notFound: List<String>,
)

/** Recognises Letterboxd and IMDb exports by their headers; parsing has no Android or network calls. */
object ExternalCsv {
    fun parse(fileName: String, text: String): Pair<ImportSource, List<ImportRow>>? {
        val lines = csv(text.removePrefix("\uFEFF"))
        val header = lines.firstOrNull()?.map(String::trim) ?: return null
        val col = header.withIndex().associate { (i, name) -> name to i }
        fun List<String>.at(name: String) = col[name]?.let(::getOrNull)?.trim().orEmpty()
        val rows = lines.drop(1).filter { row -> row.any(String::isNotBlank) }
        return when {
            "Letterboxd URI" in col -> {
                val name = fileName.substringAfterLast('/').lowercase()
                val kind = when {
                    "watchlist" in name -> "watchlist"
                    "Watched Date" in col || "diary" in name -> "diary"
                    "ratings" in name || ("Rating" in col && "watched" !in name) -> "ratings"
                    else -> "watched"
                }
                ImportSource.LETTERBOXD to rows.mapNotNull { row ->
                    val title = row.at("Name").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val stars = row.at("Rating").toDoubleOrNull()
                    ImportRow(
                        title = title,
                        year = row.at("Year").toIntOrNull(),
                        type = MediaType.MOVIE,
                        watchlist = kind == "watchlist",
                        // Letterboxd rates 0.5-5 stars; CineTrack scores 1-10.
                        score = if (kind == "ratings" || kind == "diary") stars?.let { (it * 2).roundToInt().coerceIn(1, 10) } else null,
                        watched = kind == "watched" || kind == "diary",
                    )
                }
            }
            "Const" in col && ("Title Type" in col || "Your Rating" in col) -> {
                val watchlist = "Position" in col
                ImportSource.IMDB to rows.mapNotNull { row ->
                    val id = row.at("Const").takeIf { it.startsWith("tt") } ?: return@mapNotNull null
                    val titleType = row.at("Title Type")
                    if (titleType.equals("tvEpisode", ignoreCase = true) || titleType.equals("podcastEpisode", ignoreCase = true)) return@mapNotNull null
                    ImportRow(
                        title = row.at("Title"),
                        year = row.at("Year").toIntOrNull(),
                        imdbId = id,
                        type = if (titleType.startsWith("tv", ignoreCase = true) && !titleType.equals("tvMovie", ignoreCase = true)) MediaType.TV else MediaType.MOVIE,
                        watchlist = watchlist,
                        score = row.at("Your Rating").toIntOrNull()?.coerceIn(1, 10),
                    )
                }
            }
            else -> null
        }
    }

    /** RFC 4180: quoted fields, doubled quotes, commas and line breaks inside quotes. */
    internal fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }

    internal fun normalize(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
}

/**
 * Imports a Letterboxd or IMDb export: watchlists become Plan to watch (only for titles not
 * in the library yet), ratings become your scores (only where you have none). Every change
 * goes through the normal library path, so it syncs like a tap would.
 */
class ExternalImporter(
    private val tmdb: () -> TmdbService,
    private val tmdbConfigured: () -> Boolean,
    private val database: AppDatabase,
    private val library: () -> LibraryRepository,
    private val personal: PersonalRepository,
) {
    /** Reads one CSV, or every CSV inside a Letterboxd zip. */
    fun read(context: Context, uri: Uri): Map<String, String> {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("The file could not be read")
        require(bytes.size <= MAX_BYTES) { "The file is too large" }
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return mapOf(name to bytes.toString(Charsets.UTF_8))
        val files = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var total = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                // Letterboxd's zip also holds deleted/ and orphaned/ folders and likes; only the top lists count.
                val entryName = entry.name
                if (entry.isDirectory || !entryName.endsWith(".csv", ignoreCase = true) || '/' in entryName.trimStart('/')) continue
                val content = zip.readBytes()
                total += content.size
                require(total <= MAX_BYTES) { "The file is too large" }
                files[entryName] = content.toString(Charsets.UTF_8)
            }
        }
        return files
    }

    suspend fun import(files: Map<String, String>, onProgress: (done: Int, total: Int) -> Unit): ImportSummary {
        check(tmdbConfigured()) { "TMDB is needed to match titles" }
        val parsed = files.entries.mapNotNull { (name, text) -> ExternalCsv.parse(name, text) }
        require(parsed.isNotEmpty()) { "This isn't a Letterboxd or IMDb export" }
        val source = parsed.first().first
        val rows = parsed.flatMap { it.second }
        // One lookup per title, even when it is in the watchlist and the ratings.
        val grouped = rows.groupBy { it.imdbId ?: "${ExternalCsv.normalize(it.title)}:${it.year}" }
        var added = 0
        var rated = 0
        val notFound = mutableListOf<String>()
        grouped.values.forEachIndexed { index, group ->
            onProgress(index, grouped.size)
            val first = group.first()
            val media = runCatching { resolve(first) }.getOrNull()
            if (media == null) {
                if (group.any { it.watchlist || it.score != null }) notFound += listOfNotNull(first.title.ifBlank { first.imdbId }, first.year?.toString()).joinToString(" ")
                return@forEachIndexed
            }
            if (group.any(ImportRow::watchlist)) {
                val current = database.stateDao().get(media.type.name, media.id)?.status
                if (current == null || current == LibraryStatus.NONE.name) {
                    library().setLibraryStatus(media, LibraryStatus.PLAN_TO_WATCH)
                    added++
                }
            }
            group.firstNotNullOfOrNull(ImportRow::score)?.let { score ->
                if (personal.setScoreIfMissing(media, score)) rated++
            }
        }
        onProgress(grouped.size, grouped.size)
        return ImportSummary(source, rows.size, added, rated, rows.count { it.watched && !it.watchlist && it.score == null }, notFound)
    }

    private suspend fun resolve(row: ImportRow): MediaCard? {
        val api = tmdb()
        row.imdbId?.let { imdb ->
            val found = api.find(imdb, "imdb_id")
            val tv = found.tvResults.firstOrNull()
            val movie = found.movieResults.firstOrNull()
            val pick = if (row.type == MediaType.TV) tv ?: movie else movie ?: tv
            val type = if (pick === tv && tv != null) MediaType.TV else MediaType.MOVIE
            return pick?.toEntity(type)?.toDomain()
        }
        val wanted = ExternalCsv.normalize(row.title)
        val candidates = api.search(row.title).results.filter { it.mediaType == "movie" }
        val match = candidates.firstOrNull { ExternalCsv.normalize(it.title.orEmpty()) == wanted && (row.year == null || it.releaseDate?.take(4)?.toIntOrNull() == row.year) }
            ?: candidates.firstOrNull { row.year != null && it.releaseDate?.take(4)?.toIntOrNull() == row.year && ExternalCsv.normalize(it.originalTitle.orEmpty()) == wanted }
        return match?.toEntity(MediaType.MOVIE)?.toDomain()
    }

    private companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
