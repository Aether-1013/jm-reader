package com.jm.reader.data.download

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.util.ImageDescrambler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One-tap comic downloader.
 *
 * Downloads every page of every chapter of an album, restores JMComic's scrambled images,
 * and writes plain JPEGs to the user's Downloads/JMReader/<albumId>/ folder (MediaStore on
 * API 29+, public downloads dir on older APIs). A small in-app index persists which albums
 * were downloaded. Downloads run on the IO dispatcher, admit only one job per album, are
 * cancellable via [deleteAlbum], and never mark an album complete if any page failed.
 */
class DownloadManager(
    private val context: Context,
    private val repository: AppRepository,
) {

    data class DownloadedAlbum(
        val albumId: String,
        val name: String,
        val chapterCount: Int,
        val pageCount: Int,
        val timestamp: Long,
    )

    data class Progress(
        val albumId: String,
        val current: Int,
        val total: Int,
        val phase: String, // "downloading" | "done" | "failed"
        val error: String? = null,
    )

    private val prefs = context.applicationContext.getSharedPreferences("jm_downloads", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloading = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val downloading: StateFlow<Map<String, Progress>> = _downloading.asStateFlow()

    private val _albums = MutableStateFlow(loadIndex())
    val albums: StateFlow<List<DownloadedAlbum>> = _albums.asStateFlow()

    private val downloadMutex = Mutex()
    private val cancelFlags = ConcurrentHashMap<String, Boolean>()

    /** "Download/JMReader/<albumId>/" - MediaStore stores RELATIVE_PATH with a trailing slash. */
    private fun selectionPath(albumId: String): String = "Download/JMReader/$albumId/"

    fun relativePath(albumId: String): String = selectionPath(albumId)

    fun isDownloaded(albumId: String): Boolean = _albums.value.any { it.albumId == albumId }

    fun getAlbum(albumId: String): DownloadedAlbum? = _albums.value.firstOrNull { it.albumId == albumId }

    fun isIdle(albumId: String): Boolean = _downloading.value[albumId]?.phase != "downloading"

    suspend fun downloadAlbum(albumId: String): Boolean = withContext(Dispatchers.IO) {
        val admitted = downloadMutex.withLock {
            if (_downloading.value[albumId]?.phase == "downloading") {
                false
            } else {
                cancelFlags.remove(albumId)
                _downloading.update { it + (albumId to Progress(albumId, 0, 1, "downloading")) }
                true
            }
        }
        if (!admitted) return@withContext false

        var ok = false
        try {
            val detail = when (val r = repository.getAlbum(albumId)) {
                is RepoResult.Ok -> r.data
                is RepoResult.Err -> throw IllegalStateException(r.message)
            }
            val chapterIds = if (detail.series.isNotEmpty()) {
                detail.series.sortedBy { it.sort }.map { it.id }
            } else {
                listOf(albumId)
            }

            // Per-chapter read payloads: (chapterId, scrambleId, pages)
            val chapters = mutableListOf<Triple<String, Long, List<com.jm.reader.data.model.ReadPage>>>()
            for (cid in chapterIds) {
                val read = when (val r = repository.comicRead(cid)) {
                    is RepoResult.Ok -> r.data
                    is RepoResult.Err -> throw IllegalStateException(r.message)
                }
                chapters.add(Triple(read.id, read.scrambleId, read.images))
            }
            val totalPages = chapters.sumOf { it.third.size }
            if (totalPages == 0) throw IllegalStateException("no pages")

            var done = 0
            var failed = false
            outer@ for ((ci, ch) in chapters.withIndex()) {
                val cid = ch.first
                val scrambleId = ch.second
                for ((pi, page) in ch.third.withIndex()) {
                    if (cancelFlags[albumId] == true) {
                        failed = true
                        break@outer
                    }
                    val name = "%02d_%03d".format(ci + 1, page.page)
                    val saved = savePage(albumId, name, page.image, cid.toLongOrNull() ?: 0L, scrambleId)
                    done++
                    _downloading.update { it + (albumId to Progress(albumId, done, totalPages, "downloading")) }
                    if (!saved) {
                        failed = true
                        break@outer
                    }
                }
            }

            cancelFlags.remove(albumId)
            when {
                failed -> {
                    cleanupAlbum(albumId)
                    _downloading.update { it + (albumId to Progress(albumId, 0, 1, "failed", "部分頁面下載失敗")) }
                }
                else -> {
                    updateIndex(
                        DownloadedAlbum(
                            albumId = albumId,
                            name = detail.name.ifBlank { "JM$albumId" },
                            chapterCount = chapterIds.size,
                            pageCount = totalPages,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                    _downloading.update { it + (albumId to Progress(albumId, totalPages, totalPages, "done")) }
                    ok = true
                }
            }
        } catch (e: Exception) {
            cancelFlags.remove(albumId)
            _downloading.update { it + (albumId to Progress(albumId, 0, 1, "failed", e.message)) }
        }
        ok
    }

    /** Downloads one page, de-scrambles it (if the album requires it) and writes a JPEG. */
    private fun savePage(albumId: String, name: String, url: String, aid: Long, scrambleId: Long): Boolean {
        val bytes = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
        } catch (_: Exception) {
            null
        } ?: return false

        val src = runCatching { decodeSampled(bytes, MAX_SAVE_DIM) }.getOrNull() ?: return false

        val out = if (ImageDescrambler.needsDescramble(aid, scrambleId, url)) {
            val num = ImageDescrambler.sliceCount(aid, fileName(url))
            val d = ImageDescrambler.descramble(src, num)
            if (d !== src) src.recycle()
            d
        } else {
            src
        }

        return writeJpeg(albumId, name, out)
    }

    private fun writeJpeg(albumId: String, name: String, bmp: Bitmap): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                // Idempotent: remove any previous file with the same name before inserting.
                runCatching {
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(selectionPath(albumId), "$name.jpg"),
                    )
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, selectionPath(albumId))
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                try {
                    val stream = resolver.openOutputStream(uri)
                    if (stream == null) {
                        resolver.delete(uri, null, null)
                        return false
                    }
                    stream.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    true
                } catch (_: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    false
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "$name.jpg")
                FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Lists the local image Uris of a downloaded album, sorted by chapter/page name. */
    fun albumImageUris(albumId: String): List<Uri> {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
                val selArgs = arrayOf(selectionPath(albumId))
                val uris = mutableListOf<Pair<String, Uri>>()
                resolver.query(collection, projection, selection, selArgs, null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val name = c.getString(nameCol) ?: ""
                        val id = c.getLong(idCol)
                        uris.add(name to Uri.withAppendedPath(collection, id.toString()))
                    }
                }
                uris.sortedBy { it.first }.map { it.second }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                if (!dir.exists()) return emptyList()
                dir.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedBy { it.name }?.map { Uri.fromFile(it) }
                    ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun deleteAlbum(albumId: String) {
        cancelFlags[albumId] = true
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val resolver = context.contentResolver
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                        arrayOf(selectionPath(albumId)),
                    )
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "JMReader/$albumId",
                    )
                    if (dir.exists()) runCatching { dir.delete() }
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "JMReader/$albumId",
                    )
                    dir.listFiles()?.forEach { it.delete() }
                    dir.delete()
                }
            } catch (_: Exception) {
            }
            removeIndex(albumId)
        }
    }

    /** Removes all downloaded files for an album (used after a cancelled/partial download). */
    private fun cleanupAlbum(albumId: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(selectionPath(albumId)),
                )
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                if (dir.exists()) runCatching { dir.delete() }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
        } catch (_: Exception) {
        }
        removeIndex(albumId)
    }

    // -----------------------------------------------------------------------
    // Index persistence
    // -----------------------------------------------------------------------

    private fun loadIndex(): List<DownloadedAlbum> {
        val raw = prefs.getString("albums", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                DownloadedAlbum(
                    albumId = o.optString("albumId"),
                    name = o.optString("name"),
                    chapterCount = o.optInt("chapterCount"),
                    pageCount = o.optInt("pageCount"),
                    timestamp = o.optLong("timestamp"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistIndex() {
        val arr = JSONArray()
        _albums.value.forEach { a ->
            arr.put(
                JSONObject()
                    .put("albumId", a.albumId)
                    .put("name", a.name)
                    .put("chapterCount", a.chapterCount)
                    .put("pageCount", a.pageCount)
                    .put("timestamp", a.timestamp)
            )
        }
        // commit() is synchronous and durable; we run on the IO dispatcher.
        prefs.edit().putString("albums", arr.toString()).commit()
    }

    private fun updateIndex(album: DownloadedAlbum) {
        _albums.update { it.filter { a -> a.albumId != album.albumId } + album }
        persistIndex()
    }

    private fun removeIndex(albumId: String) {
        _albums.update { it.filter { a -> a.albumId != albumId } }
        persistIndex()
    }

    /** Decodes with an inSampleSize bounding the largest dimension (prevents OOM on huge pages). */
    private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun fileName(url: String): String {
        val path = url.substringBefore('?')
        val seg = path.substringAfterLast('/')
        return seg.substringBeforeLast('.').ifBlank { seg }
    }

    private companion object {
        const val MAX_SAVE_DIM = 2560
    }
}
