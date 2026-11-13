package com.refmanager.tablet.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.refmanager.tablet.data.Annotation
import com.refmanager.tablet.data.AnnotationColors
import com.refmanager.tablet.data.Document
import com.refmanager.tablet.data.PageRect
import com.refmanager.tablet.pdf.PdfPageLoader
import com.refmanager.tablet.ui.theme.CiteColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class ReaderMode { READ, PEN, HIGHLIGHT, UNDERLINE, NOTE, ERASER }

private data class NoteDraft(val page: Int, val anchor: PageRect)

@Composable
fun ReaderScreen(
    document: Document,
    annotations: List<Annotation>,
    onBack: (() -> Unit)? = null,
    tabsBar: (@Composable () -> Unit)? = null,
    onProgress: (Int) -> Unit,
    onAddHighlight: (Int, Int, List<PageRect>) -> Unit,
    onAddUnderline: (Int, Int, List<PageRect>) -> Unit,
    onAddInk: (Int, Int, List<PageRect>) -> Unit,
    onAddNote: (Int, Int, PageRect, String) -> Unit,
    onUpdateNote: (Annotation, String) -> Unit,
    onDeleteAnnotation: (Annotation) -> Unit
) {
    if (document.fileUri.isBlank()) {
        NoPdfPane(document = document, onBack = onBack)
        return
    }

    val context = LocalContext.current
    val uri = remember(document.fileUri) { Uri.parse(document.fileUri) }
    val scope = rememberCoroutineScope()

    var pageIndex by remember(document.id) { mutableIntStateOf(document.lastPage.coerceAtLeast(0)) }
    var pageCount by remember(document.id) { mutableIntStateOf(document.pageCount ?: 0) }
    var error by remember(document.id) { mutableStateOf<String?>(null) }

    var zoom by remember(document.id) { mutableFloatStateOf(1f) }
    var pan by remember(document.id) { mutableStateOf(Offset.Zero) }
    var isPinching by remember(document.id) { mutableStateOf(false) }

    var mode by remember(document.id) { mutableStateOf(ReaderMode.READ) }
    var activeColor by remember(document.id) { mutableIntStateOf(AnnotationColors.default) }

    var continuousMode by remember(document.id) { mutableStateOf(true) }
    var pagesPerView by remember(document.id) { mutableIntStateOf(1) }
    var showJump by remember(document.id) { mutableStateOf(false) }
    var showAnnotations by remember(document.id) { mutableStateOf(false) }
    var draft by remember(document.id) { mutableStateOf<NoteDraft?>(null) }
    var editing by remember(document.id) { mutableStateOf<Annotation?>(null) }
    var deletingAnnotation by remember(document.id) { mutableStateOf<Annotation?>(null) }

    ClearFocusWhenDialogsClose(
        anyDialogOpen = showJump || showAnnotations || draft != null || editing != null || deletingAnnotation != null
    )

    // Ensure pageCount is known & prewarm adjacent pages
    LaunchedEffect(document.id, uri) {
        if (pageCount == 0) {
            val res = withContext(Dispatchers.IO) { runCatching { PdfPageLoader.render(context, uri, 0) } }
            res.onSuccess { p ->
                if (p.pageCount > 0) pageCount = p.pageCount
            }.onFailure { f ->
                error = f.message ?: "Cannot open PDF"
            }
        }
    }

    LaunchedEffect(document.id, pageIndex, pageCount) {
        if (pageCount > 0) {
            withContext(Dispatchers.IO) {
                for (offset in 1..2) {
                    val next = pageIndex + offset
                    if (next < pageCount && PdfPageLoader.getCached(uri, next) == null) {
                        runCatching { PdfPageLoader.render(context, uri, next) }
                    }
                }
            }
        }
    }

    val continuousListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (if (pagesPerView == 2) pageIndex / 2 else pageIndex).coerceAtLeast(0)
    )

    val spreadCount = if (pageCount <= 0) 1 else if (pagesPerView == 2) (pageCount + 1) / 2 else pageCount
    val pagerState = rememberPagerState(
        initialPage = (if (pagesPerView == 2) pageIndex / 2 else pageIndex).coerceIn(0, (spreadCount - 1).coerceAtLeast(0)),
        pageCount = { spreadCount }
    )

    // Sync pageIndex when swiping in Single/Book mode
    LaunchedEffect(pagerState.currentPage, continuousMode, pagesPerView, pageCount) {
        if (!continuousMode && pageCount > 0) {
            val p = (if (pagesPerView == 2) pagerState.currentPage * 2 else pagerState.currentPage)
                .coerceIn(0, pageCount - 1)
            if (p != pageIndex) {
                pageIndex = p
                onProgress(p)
            }
        }
    }

    // Sync pageIndex when scrolling in Continuous mode
    LaunchedEffect(continuousListState.firstVisibleItemIndex, continuousMode, pagesPerView, pageCount) {
        if (continuousMode && pageCount > 0) {
            val p = (if (pagesPerView == 2) continuousListState.firstVisibleItemIndex * 2 else continuousListState.firstVisibleItemIndex)
                .coerceIn(0, pageCount - 1)
            if (p != pageIndex) {
                pageIndex = p
                onProgress(p)
            }
        }
    }

    val jumpToPage: (Int) -> Unit = { target ->
        val clamped = target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        pageIndex = clamped
        onProgress(clamped)
        val itemIdx = if (pagesPerView == 2) clamped / 2 else clamped
        scope.launch {
            if (continuousMode) continuousListState.scrollToItem(itemIdx)
            else pagerState.scrollToPage(itemIdx.coerceIn(0, (spreadCount - 1).coerceAtLeast(0)))
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF2B2D30))) {
        // Solid top tab & page bar
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    if (tabsBar != null) {
                        Box(Modifier.weight(1f)) {
                            tabsBar()
                        }
                    } else {
                        Column(Modifier.weight(1f)) {
                            Text(
                                document.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = buildString {
                                if (document.authors.isNotBlank()) append(document.authors)
                                if (document.year != null) {
                                    if (isNotEmpty()) append(" · ")
                                    append(document.year)
                                }
                            }
                            if (sub.isNotBlank()) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { jumpToPage((pageIndex - pagesPerView).coerceAtLeast(0)) },
                        enabled = pageIndex > 0
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
                    }
                    TextButton(
                        onClick = { showJump = true },
                        enabled = pageCount > 0
                    ) {
                        Text(
                            if (pageCount > 0) "${pageIndex + 1} / $pageCount" else "–",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    IconButton(
                        onClick = { jumpToPage(pageIndex + pagesPerView) },
                        enabled = pageIndex + pagesPerView < pageCount
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
                    }
                    if (zoom > 1.02f) {
                        TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) {
                            Text("${(zoom * 100).toInt()}%")
                        }
                    }
                    IconButton(
                        onClick = {
                            val ctx = context
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    val src = java.io.File(Uri.parse(document.fileUri).path.orEmpty())
                                    val out = java.io.File(
                                        android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS
                                        ),
                                        "CiteBird_${document.title.take(40).replace(Regex("[^A-Za-z0-9._-]"), "_")}.pdf"
                                    )
                                    src.copyTo(out, overwrite = true)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(ctx, "Exported to Downloads", Toast.LENGTH_SHORT).show()
                                    }
                                }.onFailure { e ->
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Export PDF")
                    }
                }
                HorizontalDivider(color = CiteColors.Border)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Floating Liquid Glass tools bar with backdrop blur, lens refraction, and specular reflection
            val glassShape = RoundedCornerShape(22.dp)
            val glassBackdrop = remember(uri, pageIndex) { PdfPageLoader.getCached(uri, pageIndex) }
            Surface(
                color = Color.Transparent,
                shadowElevation = 14.dp,
                shape = glassShape,
                border = BorderStroke(
                    1.25.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.96f),
                            Color(0xFFBCE3FF).copy(alpha = 0.72f),
                            Color.White.copy(alpha = 0.45f),
                            Color(0xFFD8F0FF).copy(alpha = 0.80f)
                        ),
                        start = Offset.Zero,
                        end = Offset(600f, 120f)
                    )
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(glassShape)
            ) {
                Box {
                    if (glassBackdrop != null && !glassBackdrop.isRecycled) {
                        Image(
                            bitmap = glassBackdrop.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.36f,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer(
                                    scaleX = 1.12f,
                                    scaleY = 1.12f,
                                    translationY = -12f
                                )
                                .blur(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.62f),
                                        Color(0xFFE4F1FC).copy(alpha = 0.40f),
                                        Color.White.copy(alpha = 0.52f)
                                    )
                                )
                            )
                            .drawBehind {
                                // Diagonal specular glass reflection sweep
                                drawRect(
                                    brush = Brush.linearGradient(
                                        0.0f to Color.White.copy(alpha = 0.48f),
                                        0.28f to Color.White.copy(alpha = 0.14f),
                                        0.52f to Color.Transparent,
                                        0.78f to Color(0xFFBFE6FF).copy(alpha = 0.16f),
                                        1.0f to Color.White.copy(alpha = 0.28f),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width * 0.75f, size.height * 1.4f)
                                    )
                                )
                                // Crisp top specular rim reflection
                                drawLine(
                                    color = Color.White.copy(alpha = 0.92f),
                                    start = Offset(18f, 1.5f),
                                    end = Offset(size.width - 18f, 1.5f),
                                    strokeWidth = 2f
                                )
                            }
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    FilterChip(
                        selected = continuousMode,
                        onClick = {
                            val target = pageIndex
                            continuousMode = !continuousMode
                            jumpToPage(target)
                        },
                        label = { Text(if (continuousMode) "Continuous scroll" else "Single page") }
                    )
                    FilterChip(
                        selected = pagesPerView == 2,
                        onClick = {
                            val target = pageIndex
                            pagesPerView = if (pagesPerView == 1) 2 else 1
                            jumpToPage(target)
                        },
                        label = { Text(if (pagesPerView == 1) "1 page" else "2 pages (book)") }
                    )
                    TextButton(onClick = { showAnnotations = true }) {
                        Text("Annotations (${annotations.size})")
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(CiteColors.Border)
                    )

                    ToolButton(
                        icon = Icons.Filled.PanTool,
                        label = "Read",
                        selected = mode == ReaderMode.READ,
                        onClick = { mode = ReaderMode.READ }
                    )
                    ToolButton(
                        icon = Icons.Filled.Draw,
                        label = "Pen",
                        selected = mode == ReaderMode.PEN,
                        onClick = { mode = ReaderMode.PEN }
                    )
                    ToolButton(
                        icon = Icons.Filled.BorderColor,
                        label = "Highlight",
                        selected = mode == ReaderMode.HIGHLIGHT,
                        onClick = { mode = ReaderMode.HIGHLIGHT }
                    )
                    ToolButton(
                        icon = Icons.Filled.FormatUnderlined,
                        label = "Underline",
                        selected = mode == ReaderMode.UNDERLINE,
                        onClick = { mode = ReaderMode.UNDERLINE }
                    )
                    ToolButton(
                        icon = Icons.AutoMirrored.Filled.StickyNote2,
                        label = "Note",
                        selected = mode == ReaderMode.NOTE,
                        onClick = { mode = ReaderMode.NOTE }
                    )
                    ToolButton(
                        icon = Icons.Filled.AutoFixNormal,
                        label = "Eraser",
                        selected = mode == ReaderMode.ERASER,
                        onClick = { mode = ReaderMode.ERASER }
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(CiteColors.Border)
                    )

                    AnnotationColors.all.forEach { color ->
                        val chosen = color == activeColor
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (chosen) 2.5.dp else 1.dp,
                                    color = if (chosen) CiteColors.TitleNavy else CiteColors.Border,
                                    shape = CircleShape
                                )
                                .clickable { activeColor = color }
                        )
                    }
                }
                }
            }

            // Main reader area (full width, scrolls behind the Liquid Glass tools bar)
            BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2B2D30))
                .clipToBounds()
                    // Pinch-to-zoom (2+ fingers always, 1-finger pan only when zoomed > 1f) + double-tap zoom
                    .pointerInput(mode) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoom > 1.05f) {
                                    zoom = 1f
                                    pan = Offset.Zero
                                } else {
                                    zoom = 2.2f
                                }
                            }
                        )
                    }
                    .pointerInput(mode) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.count { it.pressed }
                                    isPinching = pointerCount >= 2
                                    if (pointerCount >= 2 || (zoom > 1.02f && mode == ReaderMode.READ)) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (zoomChange != 1f || panChange != Offset.Zero) {
                                            val nextZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                            zoom = nextZoom
                                            pan = if (nextZoom <= 1.01f) Offset.Zero else pan + panChange
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                isPinching = false
                            }
                        }
                    }
            ) {
                when {
                    error != null -> Text(
                        error!!,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )

                    pageCount <= 0 -> CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> {
                        val zoomTier = when {
                            zoom <= 1.2f -> 1f
                            zoom <= 2.2f -> 2f
                            else -> 3f
                        }
                        if (continuousMode) {
                        val listModifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoom,
                                scaleY = zoom,
                                translationX = pan.x,
                                translationY = pan.y
                            )
                        LazyColumn(
                            state = continuousListState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 64.dp, bottom = 16.dp),
                            userScrollEnabled = !isPinching && zoom <= 1.02f && mode == ReaderMode.READ,
                            modifier = listModifier
                        ) {
                            if (pagesPerView == 1) {
                                items(pageCount, key = { "p1-$it" }) { index ->
                                    PdfPageSurface(
                                        context = context,
                                        uri = uri,
                                        pageIndex = index,
                                        pageCount = pageCount,
                                        annotations = annotations.filter { it.page == index },
                                        mode = mode,
                                        activeColor = activeColor,
                                        onAddHighlight = { rects -> onAddHighlight(index, activeColor, rects) },
                                        onAddUnderline = { rects -> onAddUnderline(index, activeColor, rects) },
                                        onAddInk = { pts -> onAddInk(index, activeColor, pts) },
                                        onTapNote = { anchor -> draft = NoteDraft(index, anchor) },
                                        onEditAnnotation = { editing = it },
                                        onLongPressAnnotation = { deletingAnnotation = it },
                                        onEraseAnnotation = onDeleteAnnotation,
                                        zoomTier = zoomTier,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items((pageCount + 1) / 2, key = { "p2-$it" }) { row ->
                                    val left = row * 2
                                    val right = left + 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(Modifier.weight(1f)) {
                                            PdfPageSurface(
                                                context = context,
                                                uri = uri,
                                                pageIndex = left,
                                                pageCount = pageCount,
                                                annotations = annotations.filter { it.page == left },
                                                mode = mode,
                                                activeColor = activeColor,
                                                onAddHighlight = { rects -> onAddHighlight(left, activeColor, rects) },
                                                onAddUnderline = { rects -> onAddUnderline(left, activeColor, rects) },
                                                onAddInk = { pts -> onAddInk(left, activeColor, pts) },
                                                onTapNote = { anchor -> draft = NoteDraft(left, anchor) },
                                                onEditAnnotation = { editing = it },
                                                onLongPressAnnotation = { deletingAnnotation = it },
                                                onEraseAnnotation = onDeleteAnnotation,
                                                zoomTier = zoomTier,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        Box(Modifier.weight(1f)) {
                                            if (right < pageCount) {
                                                PdfPageSurface(
                                                    context = context,
                                                    uri = uri,
                                                    pageIndex = right,
                                                    pageCount = pageCount,
                                                    annotations = annotations.filter { it.page == right },
                                                    mode = mode,
                                                    activeColor = activeColor,
                                                    onAddHighlight = { rects -> onAddHighlight(right, activeColor, rects) },
                                                    onAddUnderline = { rects -> onAddUnderline(right, activeColor, rects) },
                                                    onAddInk = { pts -> onAddInk(right, activeColor, pts) },
                                                    onTapNote = { anchor -> draft = NoteDraft(right, anchor) },
                                                    onEditAnnotation = { editing = it },
                                                    onLongPressAnnotation = { deletingAnnotation = it },
                                                    onEraseAnnotation = onDeleteAnnotation,
                                                    zoomTier = zoomTier,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        } else {
                            // Single Page or 2-Page Book View with swipe via HorizontalPager
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            key = { it },
                            userScrollEnabled = !isPinching && zoom <= 1.02f && mode == ReaderMode.READ,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 60.dp, bottom = 6.dp)
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = pan.x,
                                    translationY = pan.y
                                )
                        ) { spreadIdx ->
                            if (pagesPerView == 1) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PdfPageSurface(
                                        context = context,
                                        uri = uri,
                                        pageIndex = spreadIdx,
                                        pageCount = pageCount,
                                        annotations = annotations.filter { it.page == spreadIdx },
                                        mode = mode,
                                        activeColor = activeColor,
                                        onAddHighlight = { rects -> onAddHighlight(spreadIdx, activeColor, rects) },
                                        onAddUnderline = { rects -> onAddUnderline(spreadIdx, activeColor, rects) },
                                        onAddInk = { pts -> onAddInk(spreadIdx, activeColor, pts) },
                                        onTapNote = { anchor -> draft = NoteDraft(spreadIdx, anchor) },
                                        onEditAnnotation = { editing = it },
                                        onLongPressAnnotation = { deletingAnnotation = it },
                                        onEraseAnnotation = onDeleteAnnotation,
                                        zoomTier = zoomTier,
                                        fitHeight = true
                                    )
                                }
                            } else {
                                val left = spreadIdx * 2
                                val right = left + 1
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        PdfPageSurface(
                                            context = context,
                                            uri = uri,
                                            pageIndex = left,
                                            pageCount = pageCount,
                                            annotations = annotations.filter { it.page == left },
                                            mode = mode,
                                            activeColor = activeColor,
                                            onAddHighlight = { rects -> onAddHighlight(left, activeColor, rects) },
                                            onAddUnderline = { rects -> onAddUnderline(left, activeColor, rects) },
                                            onAddInk = { pts -> onAddInk(left, activeColor, pts) },
                                            onTapNote = { anchor -> draft = NoteDraft(left, anchor) },
                                            onEditAnnotation = { editing = it },
                                            onLongPressAnnotation = { deletingAnnotation = it },
                                            onEraseAnnotation = onDeleteAnnotation,
                                            zoomTier = zoomTier,
                                            fitHeight = true
                                        )
                                    }
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        if (right < pageCount) {
                                            PdfPageSurface(
                                                context = context,
                                                uri = uri,
                                                pageIndex = right,
                                                pageCount = pageCount,
                                                annotations = annotations.filter { it.page == right },
                                                mode = mode,
                                                activeColor = activeColor,
                                                onAddHighlight = { rects -> onAddHighlight(right, activeColor, rects) },
                                                onAddUnderline = { rects -> onAddUnderline(right, activeColor, rects) },
                                                onAddInk = { pts -> onAddInk(right, activeColor, pts) },
                                                onTapNote = { anchor -> draft = NoteDraft(right, anchor) },
                                                onEditAnnotation = { editing = it },
                                                onLongPressAnnotation = { deletingAnnotation = it },
                                                onEraseAnnotation = onDeleteAnnotation,
                                                zoomTier = zoomTier,
                                                fitHeight = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }

        if (pageCount > 0) {
            LinearProgressIndicator(
                progress = { (pageIndex + 1).toFloat() / pageCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
            )
        }
        }
    }

    if (showJump) {
        var pageInput by remember(pageIndex) { mutableStateOf((pageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all(Char::isDigit)) pageInput = value
                    },
                    singleLine = true,
                    label = { Text("Page (1–$pageCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pageInput.toIntOrNull()?.minus(1)?.coerceIn(0, pageCount - 1)
                        if (target != null) jumpToPage(target)
                        showJump = false
                    }
                ) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("Cancel") } }
        )
    }

    if (showAnnotations) {
        AlertDialog(
            onDismissRequest = { showAnnotations = false },
            title = { Text("Annotations (${annotations.size})") },
            text = {
                if (annotations.isEmpty()) {
                    Text("No annotations yet. Use the Pen, Highlighter, Underline, or Note tools on the right bar.")
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        items(annotations.size, key = { annotations[it].id }) { idx ->
                            val annotation = annotations[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        jumpToPage(annotation.page)
                                        showAnnotations = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 4.dp, height = 30.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(annotation.color))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    val kindLabel = when (annotation.kind) {
                                        Annotation.KIND_HIGHLIGHT -> "Highlight"
                                        Annotation.KIND_UNDERLINE -> "Underline"
                                        Annotation.KIND_INK -> "Pen"
                                        else -> "Note"
                                    }
                                    Text("Page ${annotation.page + 1} · $kindLabel", style = MaterialTheme.typography.titleSmall)
                                    if (annotation.note.isNotBlank()) {
                                        Text(
                                            annotation.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteAnnotation(annotation) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete annotation")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAnnotations = false }) { Text("Close") } }
        )
    }

    val pending = draft
    if (pending != null) {
        var text by remember(pending) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { draft = null },
            title = { Text("New note (Page ${pending.page + 1})") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Note") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) onAddNote(pending.page, activeColor, pending.anchor, text)
                        draft = null
                    },
                    enabled = text.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { draft = null }) { Text("Cancel") } }
        )
    }

    val toDelete = deletingAnnotation
    if (toDelete != null) {
        val kindLabel = when (toDelete.kind) {
            Annotation.KIND_HIGHLIGHT -> "highlight"
            Annotation.KIND_UNDERLINE -> "underline"
            Annotation.KIND_INK -> "pen stroke"
            else -> "note"
        }
        AlertDialog(
            onDismissRequest = { deletingAnnotation = null },
            title = { Text("Delete $kindLabel?") },
            text = { Text("Remove this $kindLabel from Page ${toDelete.page + 1}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAnnotation(toDelete)
                    deletingAnnotation = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingAnnotation = null }) { Text("Cancel") }
            }
        )
    }

    val selected = editing
    if (selected != null) {
        var text by remember(selected.id) { mutableStateOf(selected.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit annotation (Page ${selected.page + 1})") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Note") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateNote(selected, text)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onDeleteAnnotation(selected); editing = null }) { Text("Delete") }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) CiteColors.SidebarSelected else Color.Transparent,
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else CiteColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PdfPageSurface(
    context: android.content.Context,
    uri: Uri,
    pageIndex: Int,
    pageCount: Int,
    annotations: List<Annotation>,
    mode: ReaderMode,
    activeColor: Int,
    onAddHighlight: (List<PageRect>) -> Unit,
    onAddUnderline: (List<PageRect>) -> Unit,
    onAddInk: (List<PageRect>) -> Unit,
    onTapNote: (PageRect) -> Unit,
    onEditAnnotation: (Annotation) -> Unit,
    onLongPressAnnotation: (Annotation) -> Unit,
    onEraseAnnotation: (Annotation) -> Unit,
    modifier: Modifier = Modifier,
    zoomTier: Float = 1f,
    fitHeight: Boolean = false
) {
    var bitmap by remember(uri, pageIndex) { mutableStateOf(PdfPageLoader.getCached(uri, pageIndex)) }
    LaunchedEffect(uri, pageIndex, zoomTier) {
        if (zoomTier > 1f) {
            kotlinx.coroutines.delay(120)
            val rendered = withContext(Dispatchers.IO) {
                runCatching { PdfPageLoader.render(context, uri, pageIndex, zoomTier) }.getOrNull()
            }
            if (rendered?.bitmap != null) bitmap = rendered.bitmap
        } else if (bitmap == null || (bitmap?.width ?: 0) > 2048 || (bitmap?.height ?: 0) > 2048) {
            val rendered = withContext(Dispatchers.IO) {
                runCatching { PdfPageLoader.render(context, uri, pageIndex, 1f) }.getOrNull()
            }
            if (rendered?.bitmap != null) bitmap = rendered.bitmap
        }
    }

    val ratio = remember(uri, pageIndex, bitmap != null) {
        val b = bitmap
        if (b != null && b.height > 0) b.width.toFloat() / b.height.toFloat() else 0.707f
    }

    var dragFrom by remember(pageIndex, mode) { mutableStateOf<Offset?>(null) }
    var dragTo by remember(pageIndex, mode) { mutableStateOf<Offset?>(null) }
    var inkPoints by remember(pageIndex, mode) { mutableStateOf<List<Offset>>(emptyList()) }

    val cardModifier = if (fitHeight) {
        modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
    } else {
        modifier.fillMaxWidth().aspectRatio(ratio)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = cardModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(mode, pageIndex, annotations) {
                        if (mode == ReaderMode.NOTE || mode == ReaderMode.ERASER || mode == ReaderMode.READ || mode == ReaderMode.HIGHLIGHT || mode == ReaderMode.UNDERLINE) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val hit = annotations.hitTest(nx, ny)
                                    when (mode) {
                                        ReaderMode.ERASER -> if (hit != null) onEraseAnnotation(hit)
                                        ReaderMode.NOTE -> if (hit != null) onEditAnnotation(hit) else onTapNote(PageRect.anchor(nx, ny))
                                        ReaderMode.READ -> if (hit != null && hit.note.isNotBlank()) onEditAnnotation(hit)
                                        else -> Unit
                                    }
                                },
                                onLongPress = { offset ->
                                    val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    annotations.hitTest(nx, ny)?.let(onLongPressAnnotation)
                                }
                            )
                        }
                    }
                    .pointerInput(mode, pageIndex, annotations) {
                        if (mode == ReaderMode.PEN || mode == ReaderMode.HIGHLIGHT || mode == ReaderMode.UNDERLINE || mode == ReaderMode.ERASER) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    if (mode == ReaderMode.PEN) {
                                        inkPoints = listOf(start)
                                    } else if (mode == ReaderMode.HIGHLIGHT || mode == ReaderMode.UNDERLINE) {
                                        dragFrom = start
                                        dragTo = start
                                    } else if (mode == ReaderMode.ERASER) {
                                        val nx = (start.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (start.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        annotations.hitTest(nx, ny)?.let(onEraseAnnotation)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pos = change.position
                                    if (mode == ReaderMode.PEN) {
                                        inkPoints = inkPoints + pos
                                    } else if (mode == ReaderMode.HIGHLIGHT || mode == ReaderMode.UNDERLINE) {
                                        dragTo = pos
                                    } else if (mode == ReaderMode.ERASER) {
                                        val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        annotations.hitTest(nx, ny)?.let(onEraseAnnotation)
                                    }
                                },
                                onDragEnd = {
                                    val w = size.width.coerceAtLeast(1).toFloat()
                                    val h = size.height.coerceAtLeast(1).toFloat()
                                    if (mode == ReaderMode.PEN && inkPoints.size >= 2) {
                                        val normalized = inkPoints.map { pt ->
                                            val nx = (pt.x / w).coerceIn(0f, 1f)
                                            val ny = (pt.y / h).coerceIn(0f, 1f)
                                            PageRect(nx, ny, nx, ny)
                                        }
                                        onAddInk(normalized)
                                    } else if ((mode == ReaderMode.HIGHLIGHT || mode == ReaderMode.UNDERLINE) && dragFrom != null && dragTo != null) {
                                        val s = dragFrom!!
                                        val e = dragTo!!
                                        val x1 = (s.x / w).coerceIn(0f, 1f)
                                        val y1 = (s.y / h).coerceIn(0f, 1f)
                                        val x2 = (e.x / w).coerceIn(0f, 1f)
                                        val y2 = (e.y / h).coerceIn(0f, 1f)
                                        // Snap thin horizontal drags to a crisp text-line height (~1.8% of page)
                                        val dy = abs(y2 - y1)
                                        val rect = if (dy < 0.015f) {
                                            val cy = (y1 + y2) / 2f
                                            PageRect.fromDrag(x1, cy - 0.009f, x2, cy + 0.009f)
                                        } else {
                                            PageRect.fromDrag(x1, y1, x2, y2)
                                        }
                                        if (rect.width >= PageRect.MIN_SIZE) {
                                            if (mode == ReaderMode.HIGHLIGHT) onAddHighlight(listOf(rect))
                                            else onAddUnderline(listOf(rect))
                                        }
                                    }
                                    dragFrom = null
                                    dragTo = null
                                    inkPoints = emptyList()
                                },
                                onDragCancel = {
                                    dragFrom = null
                                    dragTo = null
                                    inkPoints = emptyList()
                                }
                            )
                        }
                    }
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = CiteColors.SidebarSelected)
                    }
                }

                // Annotations overlay
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width.coerceAtLeast(1f)
                    val h = size.height.coerceAtLeast(1f)
                    annotations.forEach { annotation ->
                        val color = Color(annotation.color)
                        when (annotation.kind) {
                            Annotation.KIND_HIGHLIGHT -> {
                                annotation.rects.forEach { r ->
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(r.left * w, r.top * h),
                                        size = Size(r.width * w, r.height * h),
                                        alpha = 0.5f,
                                        blendMode = BlendMode.Multiply
                                    )
                                }
                            }
                            Annotation.KIND_UNDERLINE -> {
                                annotation.rects.forEach { r ->
                                    val y = r.bottom * h
                                    drawLine(
                                        color = color,
                                        start = Offset(r.left * w, y),
                                        end = Offset(r.right * w, y),
                                        strokeWidth = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                            Annotation.KIND_INK -> {
                                val pts = annotation.rects
                                if (pts.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(pts.first().left * w, pts.first().top * h)
                                        for (i in 1 until pts.size) {
                                            lineTo(pts[i].left * w, pts[i].top * h)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = color,
                                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                            else -> {
                                annotation.rects.firstOrNull()?.let { r ->
                                    val center = Offset(r.centerX() * w, r.centerY() * h)
                                    val radius = 8.dp.toPx()
                                    drawCircle(color = color, radius = radius, center = center)
                                    drawCircle(color = Color.White, radius = radius * 0.4f, center = center)
                                }
                            }
                        }
                    }

                    // Live drag preview
                    if (inkPoints.size >= 2) {
                        val path = Path().apply {
                            moveTo(inkPoints.first().x, inkPoints.first().y)
                            for (i in 1 until inkPoints.size) lineTo(inkPoints[i].x, inkPoints[i].y)
                        }
                        drawPath(
                            path = path,
                            color = Color(activeColor),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    } else if (dragFrom != null && dragTo != null) {
                        val s = dragFrom!!
                        val e = dragTo!!
                        val left = minOf(s.x, e.x)
                        val right = maxOf(s.x, e.x)
                        val top = minOf(s.y, e.y)
                        val bottom = maxOf(s.y, e.y)
                        if (mode == ReaderMode.UNDERLINE) {
                            drawLine(
                                color = Color(activeColor),
                                start = Offset(left, bottom),
                                end = Offset(right, bottom),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        } else {
                            drawRect(
                                color = Color(activeColor),
                                topLeft = Offset(left, top),
                                size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(8.dp.toPx())),
                                alpha = 0.55f
                            )
                        }
                    }
                }
            }
        }
        if (!fitHeight) {
            Text(
                "Page ${pageIndex + 1} of $pageCount",
                fontSize = 11.sp,
                color = Color(0xFFBDBDBD),
                modifier = Modifier.padding(top = 3.dp, bottom = 4.dp)
            )
        }
    }
}

private fun List<Annotation>.hitTest(x: Float, y: Float): Annotation? =
    lastOrNull { annotation ->
        annotation.rects.any { rect ->
            x >= rect.left - HIT_SLOP &&
                x <= rect.right + HIT_SLOP &&
                y >= rect.top - HIT_SLOP &&
                y <= rect.bottom + HIT_SLOP
        }
    }

private const val HIT_SLOP = 0.018f

@Composable
private fun NoPdfPane(document: Document, onBack: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = CiteColors.TextMuted, modifier = Modifier.size(48.dp))
        Text(document.title, style = MaterialTheme.typography.titleLarge)
        Text("No PDF attached to this reference.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
