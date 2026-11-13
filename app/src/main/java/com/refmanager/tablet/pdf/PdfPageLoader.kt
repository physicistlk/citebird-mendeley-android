package com.refmanager.tablet.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlin.math.min

/**
 * Renders a single PDF page at a time and never keeps more than one bitmap alive.
 * That keeps peak memory well inside the 512 MB budget on a 1600x2560 tablet.
 */
object PdfPageLoader {

    private const val MAX_DIMENSION = 2048

    data class Page(val pageCount: Int, val bitmap: Bitmap?)

    fun render(context: Context, uri: Uri, pageIndex: Int): Page {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return Page(0, null)

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                if (pageIndex !in 0 until count) return Page(count, null)

                renderer.openPage(pageIndex).use { page ->
                    val scale = min(
                        MAX_DIMENSION.toFloat() / page.width,
                        MAX_DIMENSION.toFloat() / page.height
                    ).coerceIn(0.5f, 3f)

                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return Page(count, bitmap)
                }
            }
        }
    }
}
