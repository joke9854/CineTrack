package com.cinetrack.crash

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Maps an ACRA report into the outbox, unless crash reporting is switched off. */
class CrashReportIntake(
    private val outbox: CrashOutbox,
    private val enabled: () -> Boolean,
    private val build: () -> CrashBuildInfo,
) {
    fun accept(fields: AcraFields) {
        if (!enabled()) return
        CrashReportMapper.fromAcra(fields, build())?.let(outbox::write)
    }
}

/** Reports waiting for Floppy, one `<report_id>.json` file each. Plain files, so the `:acra` process can write them. */
class CrashOutbox(private val directory: File, private val maxFiles: Int = MAX_FILES) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Writes atomically (tmp + rename), then drops the oldest files beyond the cap. */
    fun write(report: CrashReportDto) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) return
        val name = fileName(report.reportId)
        val tmp = File(directory, "$name.tmp")
        tmp.writeText(json.encodeToString(report))
        try {
            Files.move(tmp.toPath(), File(directory, name).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            tmp.delete()
            throw error
        }
        prune()
    }

    /** Oldest first. */
    fun files(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))
            .orEmpty()

    fun isEmpty(): Boolean = files().isEmpty()

    /** Null when the file is unreadable or not a report. */
    fun read(file: File): CrashReportDto? =
        runCatching { json.decodeFromString<CrashReportDto>(file.readText()) }.getOrNull()

    fun delete(file: File) {
        file.delete()
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun prune() {
        val all = files()
        if (all.size > maxFiles) all.take(all.size - maxFiles).forEach { it.delete() }
    }

    private fun fileName(reportId: String): String =
        reportId.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "report" } + ".json"

    companion object {
        const val MAX_FILES = 20
        const val DIRECTORY = "crash-outbox"

        fun forContext(context: android.content.Context) = CrashOutbox(File(context.filesDir, DIRECTORY))
    }
}
