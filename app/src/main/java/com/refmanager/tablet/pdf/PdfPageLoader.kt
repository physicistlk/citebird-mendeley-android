package com.refmanager.tablet.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import kotlin.math.min

/**
 * Renders PDF pages with a small bounded LRU cache (8 pages max) so scrolling and
 * swiping are instant and blink-free while staying well inside the 512 MB budget.
 */
object PdfPageLoader {

    private const val MAX_DIMENSION = 2048
    private const val CACHE_PAGES = 24
    private const val HI_RES_CACHE_PAGES = 12

    private val cache = object : LruCache<String, Bitmap>(CACHE_PAGES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val hiResCache = object : LruCache<String, Bitmap>(HI_RES_CACHE_PAGES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    data class Page(val pageCount: Int, val bitmap: Bitmap?)

    private fun key(uri: Uri, pageIndex: Int): String = "$uri#$pageIndex"
    private fun hiResKey(uri: Uri, pageIndex: Int, maxDim: Int): String = "$uri#$pageIndex#$maxDim"

    fun getCached(uri: Uri, pageIndex: Int): Bitmap? =
        cache.get(key(uri, pageIndex))?.takeIf { !it.isRecycled }

    fun render(context: Context, uri: Uri, pageIndex: Int, zoomTier: Float = 1f): Page {
        val targetMaxDim = when {
            zoomTier <= 1.2f -> MAX_DIMENSION
            zoomTier <= 2.2f -> 3200
            else -> 4096
        }
        val isBase = targetMaxDim == MAX_DIMENSION
        val k = if (isBase) key(uri, pageIndex) else hiResKey(uri, pageIndex, targetMaxDim)
        val activeCache = if (isBase) cache else hiResCache
        val cached = activeCache.get(k)?.takeIf { !it.isRecycled }
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return Page(0, cached)

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                if (pageIndex !in 0 until count) return Page(count, null)
                if (cached != null) return Page(count, cached)

                renderer.openPage(pageIndex).use { page ->
                    val maxScale = if (isBase) 3f else 6f
                    val scale = min(
                        targetMaxDim.toFloat() / page.width,
                        targetMaxDim.toFloat() / page.height
                    ).coerceIn(0.5f, maxScale)

                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    activeCache.put(k, bitmap)
                    return Page(count, bitmap)
                }
            }
        }
    }
}
