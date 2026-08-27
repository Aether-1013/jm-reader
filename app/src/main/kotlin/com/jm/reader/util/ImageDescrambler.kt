package com.jm.reader.util

import android.graphics.Bitmap
import android.graphics.Canvas
import com.jm.reader.data.net.Crypto

/**
 * Reconstructs JMComic's server-side "scrambled" page images.
 *
 * The CDN stores some album pages with their horizontal strips in reverse vertical order.
 * The web app de-scrambles them in the browser (utils/Function.js `scramble_image` /
 * `get_num` / `onImageLoaded`); this is the same algorithm for native Bitmaps.
 *
 * Slice count depends only on (album id, page file name):
 *   key = md5("<aid><pageName>").lastChar.code
 *   mod 10 for aid in [268850, 421925], mod 8 for aid >= 421926, else unchanged (-> 10)
 */
object ImageDescrambler {

    /** pageName is the image file base name, e.g. "00001" for 00001.webp. */
    fun sliceCount(aid: Long, pageName: String): Int {
        val keyHex = Crypto.md5Hex("$aid$pageName")
        var key = keyHex.last().code
        if (aid in 268_850L..421_925L) key %= 10
        else if (aid >= 421_926L) key %= 8
        return when (key) {
            0 -> 2
            1 -> 4
            2 -> 6
            3 -> 8
            4 -> 10
            5 -> 12
            6 -> 14
            7 -> 16
            8 -> 18
            9 -> 20
            else -> 10
        }
    }

    /** GIFs and albums below scramble_id are never scrambled. */
    fun needsDescramble(aid: Long, scrambleId: Long, imageUrl: String): Boolean {
        if (imageUrl.contains(".gif")) return false
        return aid >= scrambleId
    }

    /**
     * Re-slices `src` into `num` horizontal strips (bottom strip first) and reorders them
     * back to normal top-to-bottom order.
     */
    fun descramble(src: Bitmap, num: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (num <= 1 || h < num) return src
        val baseH = h / num
        val rem = h % num
        val out = Bitmap.createBitmap(w, h, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        for (i in 0 until num) {
            val copyH = if (i == 0) baseH + rem else baseH
            val srcY = h - baseH * (i + 1) - rem
            val dstY = if (i == 0) 0 else baseH * i + rem
            if (srcY >= 0 && srcY + copyH <= h) {
                val strip = Bitmap.createBitmap(src, 0, srcY, w, copyH)
                canvas.drawBitmap(strip, 0f, dstY.toFloat(), null)
                strip.recycle()
            }
        }
        return out
    }
}
