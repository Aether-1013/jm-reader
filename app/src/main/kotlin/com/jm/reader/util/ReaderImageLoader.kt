package com.jm.reader.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Loads reader page images and applies JMComic's de-scramble transform when required.
 * Results are cached in an in-memory LRU.
 */
object ReaderImageLoader {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * @param aid         the album id (readData.id)
     * @param scrambleId  the album's scramble threshold (readData.scramble_id)
     */
    suspend fun load(url: String, aid: Long, scrambleId: Long): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }

        val bytes = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
        } catch (_: Exception) {
            null
        } ?: return@withContext null

        val src = runCatching { decodeSampled(bytes, MAX_DISPLAY_DIM) }.getOrNull()
            ?: return@withContext null
        Log.d("JMReader", "img aid=$aid scrambleId=$scrambleId ${src.width}x${src.height} name=${fileName(url)}")

        val out = if (ImageDescrambler.needsDescramble(aid, scrambleId, url)) {
            val num = ImageDescrambler.sliceCount(aid, fileName(url))
            Log.d("JMReader", "DESCRAMBLE num=$num url=$url")
            val descrambled = ImageDescrambler.descramble(src, num)
            if (descrambled !== src) src.recycle()
            descrambled
        } else {
            Log.d("JMReader", "NO-DESCRAMBLE url=$url")
            src
        }

        if (out.byteCount <= 32 * 1024 * 1024) cache.put(url, out)
        out
    }

    private fun fileName(url: String): String {
        val path = url.substringBefore('?')
        val seg = path.substringAfterLast('/')
        return seg.substringBeforeLast('.').ifBlank { seg }
    }

    /** Decodes with an inSampleSize that keeps the largest dimension <= maxDim (memory bound). */
    private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private const val MAX_DISPLAY_DIM = 2400
}
