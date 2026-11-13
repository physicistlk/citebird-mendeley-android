package com.refmanager.tablet.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An annotation is an app-owned record, not something stored inside the PDF.
 *
 * Like Paperpile's `Notes` table, the PDF stays untouched: the reader draws these
 * records over the rendered page, and an export step can write them into the file
 * later. Rects are normalised to the page (0..1) so they survive zoom and re-render.
 */
@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class Annotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val page: Int,
    val kind: String,
    val color: Int,
    val rectsData: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isHighlight: Boolean get() = kind == KIND_HIGHLIGHT

    val rects: List<PageRect> get() = decodeRects(rectsData)

    companion object {
        const val KIND_HIGHLIGHT = "highlight"
        const val KIND_NOTE = "note"
        const val KIND_UNDERLINE = "underline"
    }
}

/** A rectangle in page space: 0,0 is the top-left, 1,1 the bottom-right. */
data class PageRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val isBigEnough: Boolean
        get() = width >= MIN_SIZE && height >= MIN_SIZE

    fun normalised(): PageRect = PageRect(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f)
    )

    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f

    companion object {
        /** Anything smaller than this is treated as an accidental tap. */
        const val MIN_SIZE = 0.004f

        fun fromDrag(x1: Float, y1: Float, x2: Float, y2: Float): PageRect =
            PageRect(
                left = minOf(x1, x2),
                top = minOf(y1, y2),
                right = maxOf(x1, x2),
                bottom = maxOf(y1, y2)
            ).normalised()

        fun anchor(x: Float, y: Float): PageRect {
            val halfW = 0.016f
            val halfH = 0.012f
            return PageRect(
                left = x - halfW,
                top = y - halfH,
                right = x + halfW,
                bottom = y + halfH
            ).normalised()
        }
    }
}

fun encodeRects(rects: List<PageRect>): String =
    rects.joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" }

fun decodeRects(data: String): List<PageRect> =
    data.split(';').mapNotNull { chunk ->
        val parts = chunk.split(',')
        if (parts.size != 4) return@mapNotNull null
        val values = parts.map { it.toFloatOrNull() ?: return@mapNotNull null }
        PageRect(values[0], values[1], values[2], values[3])
    }

/** Highlight palette; ARGB ints so they can be stored in the database directly. */
object AnnotationColors {
    val all = listOf(
        0xFFFFE082.toInt(), // amber
        0xFFA5D6A7.toInt(), // green
        0xFFF48FB1.toInt(), // pink
        0xFF90CAF9.toInt()  // blue
    )

    val default: Int = all.first()
}
