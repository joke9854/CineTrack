package com.cinetrack.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.File

/** Your own profile photo and banner. [maxSide] keeps the saved file small: the avatar is drawn at 76dp, the banner full width. */
enum class ProfileImageKind(val key: String, val maxSide: Int) {
    AVATAR("avatar", 512),
    BANNER("banner", 1440),
}

/** Absolute paths of the saved images; null shows the initial, or the recent posters as the banner. */
data class ProfileImages(val avatar: String? = null, val banner: String? = null) {
    operator fun get(kind: ProfileImageKind): String? = if (kind == ProfileImageKind.AVATAR) avatar else banner
}

/**
 * The images live in app-private storage (filesDir/profile), one file per kind. Each save gets a
 * new name ("avatar-<time>.jpg") so Coil never shows a cached older picture, and the old one goes.
 */
class ProfileImageStore(private val directory: File) {
    fun path(name: String?): String? = name?.takeIf(String::isNotBlank)?.let { File(directory, it) }?.takeIf(File::isFile)?.absolutePath

    /** Decodes the picked image, scales it down and saves it as JPEG; returns the new file name. */
    fun import(resolver: ContentResolver, uri: Uri, kind: ProfileImageKind, now: Long = System.currentTimeMillis()): String {
        val bitmap = decode(resolver, uri, kind.maxSide)
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
            out.toByteArray()
        }
        bitmap.recycle()
        return write(kind, bytes, now)
    }

    /** Saves already encoded image bytes (a restored backup); returns the new file name. */
    fun write(kind: ProfileImageKind, bytes: ByteArray, now: Long = System.currentTimeMillis()): String {
        require(bytes.isNotEmpty()) { "Empty image" }
        directory.mkdirs()
        // Two saves in the same millisecond still get distinct names.
        var stamp = now
        while (File(directory, "${kind.key}-$stamp.jpg").exists()) stamp++
        val name = "${kind.key}-$stamp.jpg"
        val target = File(directory, name)
        val temporary = File(directory, "$name.tmp")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(target)) { "Could not save the image" }
        removeExcept(kind, name)
        return name
    }

    fun read(name: String?): ByteArray? = path(name)?.let { File(it).readBytes() }

    /** Deletes every file of [kind] except [keep] (null deletes them all). */
    fun removeExcept(kind: ProfileImageKind, keep: String?) {
        directory.listFiles()?.filter { it.name.startsWith("${kind.key}-") && it.name != keep }?.forEach { it.delete() }
    }

    private fun decode(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the photo's EXIF rotation and scales while decoding.
            val source = android.graphics.ImageDecoder.createSource(resolver, uri)
            return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (width, height) = info.size.width to info.size.height
                val scale = minOf(1f, maxSide.toFloat() / maxOf(width, height))
                decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("Could not read the image")
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        if (scale >= 1f) return decoded
        return decoded.scale((decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1))
            .also { if (it !== decoded) decoded.recycle() }
    }
}
