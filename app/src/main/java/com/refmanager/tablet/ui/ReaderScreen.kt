package com.refmanager.tablet.ui

import android.widget.Toast
import androidx.compose.material.icons.filled.FileDownload
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.refmanager.tablet.data.Annotation
import com.refmanager.tablet.data.AnnotationColors
import com.refmanager.tablet.data.Document
import com.refmanager.tablet.data.PageRect
import com.refmanager.tablet.pdf.PdfPageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/** What a drag or tap on the page does. */
private enum class ReaderMode { READ, HIGHLIGHT, NOTE }

private data class NoteDraft(val page: Int, val anchor: PageRect)

@Composable
fun ReaderScreen(
    document: Document,
    annotations: List<Annotation>,
    onBack: (() -> Unit)?,
    onProgress: (Int) -> Unit,
    onAddHighlight: (Int, Int, List<PageRect>) -> Unit,
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

    var pageIndex by remember(document.id) {
        mutableIntStateOf(document.lastPage.coerceAtLeast(0))
    }
    var pageCount by remember(document.id) {
        mutableIntStateOf(document.pageCount ?: 0)
    }
    var pageBitmap by remember(document.id) { mutableStateOf<Bitmap?>(null) }
    var secondBitmap by remember(document.id) { mutableStateOf<Bitmap?>(null) }
    var error by remember(document.id) { mutableStateOf<String?>(null) }
    var isLoading by remember(document.id) { mutableStateOf(true) }

    var zoom by remember(document.id) { mutableFloatStateOf(1f) }
    var pan by remember(document.id) { mutableStateOf(Offset.Zero) }

    var mode by remember(document.id) { mutableStateOf(ReaderMode.READ) }
    var activeColor by remember(document.id) { mutableIntStateOf(AnnotationColors.default) }

    var continuousMode by remember(document.id) { mutableStateOf(true) }
    var pagesPerView by remember(document.id) { mutableIntStateOf(1) }
    var showJump by remember(document.id) { mutableStateOf(false) }
    var showAnnotations by remember(document.id) { mutableStateOf(false) }
    var draft by remember(document.id) { mutableStateOf<NoteDraft?>(null) }
    var editing by remember(document.id) { mutableStateOf<Annotation?>(null) }

    // In-progress highlight, in page pixels of the page canvas.
    var dragFrom by remember(document.id) { mutableStateOf<Offset?>(null) }
    var dragTo by remember(document.id) { mutableStateOf<Offset?>(null) }

    ClearFocusWhenDialogsClose(
        anyDialogOpen = showJump || showAnnotations || draft != null || editing != null
    )

    LaunchedEffect(document.id, pageIndex, pagesPerView, continuousMode) {
        if (continuousMode) {
            if (pageCount == 0) {
                val p = withContext(Dispatchers.IO) { runCatching { PdfPageLoader.render(context, uri, 0) }.getOrNull() }
                if (p != null && p.pageCount > 0) pageCount = p.pageCount
                p?.bitmap?.recycle()
            }
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { PdfPageLoader.render(context, uri, pageIndex) }
        }
        result.onSuccess { page ->
            if (page.pageCount > 0) pageCount = page.pageCount
            pageBitmap = page.bitmap
            if (page.bitmap != null) onProgress(pageIndex)
            if (pagesPerView == 2 && pageIndex + 1 < page.pageCount) {
                val second = withContext(Dispatchers.IO) {
                    runCatching { PdfPageLoader.render(context, uri, pageIndex + 1) }.getOrNull()
                }
                secondBitmap = second?.bitmap
            } else {
                secondBitmap = null
            }
        }.onFailure { failure ->
            error = failure.message ?: "Cannot render this PDF"
        }
        isLoading = false
    }

    // Annotation gestures conflict with zoom/pan, so annotating always starts unzoomed.
    LaunchedEffect(mode) {
        if (mode != ReaderMode.READ) {
            zoom = 1f
            pan = Offset.Zero
        }
        dragFrom = null
        dragTo = null
    }

    val pageAnnotations = remember(annotations, pageIndex) {
        annotations.filter { it.page == pageIndex }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (zoom * zoomChange).coerceIn(1f, 5f)
        zoom = next
        pan = if (next == 1f) Offset.Zero else pan + panChange
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (document.authors.isNotBlank()) append(document.authors)
                        if (document.year != null) {
                            if (isNotEmpty()) append(" · ")
                            append(document.year)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { pageIndex = (pageIndex - pagesPerView).coerceAtLeast(0) },
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
                onClick = {
                    val next = pageIndex + pagesPerView
                    if (next < pageCount) pageIndex = next
                },
                enabled = pageIndex + pagesPerView < pageCount
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
            }
            IconButton(
                onClick = {
                    val ctx = context
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        runCatching {
                            val src = java.io.File(Uri.parse(document.fileUri).path.orEmpty())
                            val out = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "CiteNest_${document.title.take(40)}.pdf")
                            src.copyTo(out, overwrite = true)
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(ctx, "Exported to Downloads", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure { e ->
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = "Export annotated PDF")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = continuousMode,
                onClick = { continuousMode = !continuousMode },
                label = { Text(if (continuousMode) "Continuous scroll" else "Single page") }
            )
            FilterChip(
                selected = pagesPerView == 2,
                onClick = { pagesPerView = if (pagesPerView == 1) 2 else 1 },
                label = { Text(if (pagesPerView == 1) "1 page" else "2 pages (book)") }
            )
            ReaderMode.entries.forEach { entry ->
                FilterChip(
                    selected = mode == entry,
                    onClick = { mode = entry },
                    label = {
                        Text(
                            when (entry) {
                                ReaderMode.READ -> "Read"
                                ReaderMode.HIGHLIGHT -> "Highlight"
                                ReaderMode.NOTE -> "Note"
                            }
                        )
                    }
                )
            }

            if (mode != ReaderMode.READ) {
                Spacer(Modifier.width(4.dp))
                AnnotationColors.all.forEach { color ->
                    val selected = color == activeColor
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            )
                            .clickable { activeColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(if (selected) 18.dp else 22.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                        )
                    }
                }
            }

            TextButton(onClick = { showAnnotations = true }) {
                Text("Annotations (${annotations.size})")
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF2A2724))
                .clipToBounds()
        ) {
            val bitmap = pageBitmap
            val rightBitmap = secondBitmap
            when {
                error != null -> Text(
                    error!!,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )

                continuousMode && pageCount > 0 -> {
                    val initialRow = if (pagesPerView == 2) pageIndex / 2 else pageIndex
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow.coerceAtLeast(0))
                    LaunchedEffect(listState.firstVisibleItemIndex, pagesPerView) {
                        val currentPage = if (pagesPerView == 2) listState.firstVisibleItemIndex * 2 else listState.firstVisibleItemIndex
                        if (currentPage in 0 until pageCount && currentPage != pageIndex) {
                            pageIndex = currentPage
                            onProgress(currentPage)
                        }
                    }
                    if (pagesPerView == 1) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .transformable(transformState, enabled = mode == ReaderMode.READ)
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = pan.x,
                                    translationY = pan.y
                                )
                        ) {
                            items(pageCount) { index ->
                                ContinuousPage(context = context, uri = uri, pageIndex = index, pageCount = pageCount)
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .transformable(transformState, enabled = mode == ReaderMode.READ)
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = pan.x,
                                    translationY = pan.y
                                )
                        ) {
                            items((pageCount + 1) / 2) { row ->
                                val left = row * 2
                                val right = left + 1
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        ContinuousPage(context = context, uri = uri, pageIndex = left, pageCount = pageCount)
                                    }
                                    Box(Modifier.weight(1f)) {
                                        if (right < pageCount) {
                                            ContinuousPage(context = context, uri = uri, pageIndex = right, pageCount = pageCount)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                !continuousMode && pagesPerView == 2 && bitmap != null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .transformable(transformState, enabled = mode == ReaderMode.READ)
                            .graphicsLayer(
                                scaleX = zoom,
                                scaleY = zoom,
                                translationX = pan.x,
                                translationY = pan.y
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                        if (rightBitmap != null) {
                            Image(
                                bitmap = rightBitmap.asImageBitmap(),
                                contentDescription = "Page ${pageIndex + 2}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.weight(1f).fillMaxSize()
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                bitmap != null -> {
                    val density = LocalDensity.current
                    val availableWidth = with(density) {
                        (maxWidth - 32.dp).toPx()
                    }.coerceAtLeast(1f)
                    val availableHeight = with(density) {
                        (maxHeight - 32.dp).toPx()
                    }.coerceAtLeast(1f)
                    val fit = min(
                        availableWidth / bitmap.width,
                        availableHeight / bitmap.height
                    )
                    val pageWidth = with(density) { (bitmap.width * fit).toDp() }
                    val pageHeight = with(density) { (bitmap.height * fit).toDp() }

                    val canvasSize = Size(
                        width = with(density) { pageWidth.toPx() },
                        height = with(density) { pageHeight.toPx() }
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(pageWidth, pageHeight)
                            .transformable(transformState, enabled = mode == ReaderMode.READ)
                            .pointerInput(mode, pageIndex, zoom, pan, pageAnnotations) {
                                val size = Size(
                                    width = size.width.toFloat().coerceAtLeast(1f),
                                    height = size.height.toFloat().coerceAtLeast(1f)
                                )
                                fun toPage(position: Offset): Offset =
                                    position.toPagePosition(size, zoom, pan)

                                when (mode) {
                                    ReaderMode.READ -> detectTapGestures(
                                        onDoubleTap = {
                                            if (zoom > 1f) {
                                                zoom = 1f
                                                pan = Offset.Zero
                                            } else {
                                                zoom = 2f
                                            }
                                        },
                                        onTap = { tap ->
                                            val point = toPage(tap)
                                            pageAnnotations
                                                .hitTest(point.x, point.y)
                                                ?.let { editing = it }
                                        }
                                    )

                                    ReaderMode.NOTE -> detectTapGestures(
                                        onTap = { tap ->
                                            val point = toPage(tap)
                                            val hit = pageAnnotations.hitTest(point.x, point.y)
                                            if (hit != null) {
                                                editing = hit
                                            } else {
                                                draft = NoteDraft(
                                                    page = pageIndex,
                                                    anchor = PageRect.anchor(point.x, point.y)
                                                )
                                            }
                                        }
                                    )

                                    ReaderMode.HIGHLIGHT -> detectDragGestures(
                                        onDragStart = { start ->
                                            dragFrom = start
                                            dragTo = start
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            dragTo = change.position
                                        },
                                        onDragEnd = {
                                            val start = dragFrom
                                            val end = dragTo
                                            dragFrom = null
                                            dragTo = null
                                            if (start != null && end != null) {
                                                val first = toPage(start)
                                                val second = toPage(end)
                                                val rect = PageRect.fromDrag(
                                                    first.x,
                                                    first.y,
                                                    second.x,
                                                    second.y
                                                )
                                                if (rect.isBigEnough) {
                                                    onAddHighlight(
                                                        pageIndex,
                                                        activeColor,
                                                        listOf(rect)
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            dragFrom = null
                                            dragTo = null
                                        }
                                    )
                                }
                            }
                            .graphicsLayer(
                                scaleX = zoom,
                                scaleY = zoom,
                                translationX = pan.x,
                                translationY = pan.y
                            )
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        PageAnnotationsLayer(
                            annotations = pageAnnotations,
                            zoom = zoom,
                            pan = pan,
                            draftColor = activeColor,
                            dragFrom = dragFrom,
                            dragTo = dragTo,
                            canvasSize = canvasSize,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (mode != ReaderMode.READ) {
                        Text(
                            if (mode == ReaderMode.HIGHLIGHT) {
                                "Drag over the page to highlight"
                            } else {
                                "Tap the page to add a note"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .background(
                                    color = Color(0xCC000000),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                isLoading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> Text(
                    "Page unavailable",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        }

        if (pageCount > 0) {
            LinearProgressIndicator(
                progress = { (pageIndex + 1).toFloat() / pageCount },
                modifier = Modifier.fillMaxWidth()
            )
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
                        if (target != null) pageIndex = target
                        showJump = false
                    }
                ) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showJump = false }) { Text("Cancel") }
            }
        )
    }

    if (showAnnotations) {
        AlertDialog(
            onDismissRequest = { showAnnotations = false },
            title = { Text("Annotations") },
            text = {
                if (annotations.isEmpty()) {
                    Text("No annotations yet. Pick Highlight or Note above the page.")
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        items(annotations, key = { it.id }) { annotation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pageIndex = annotation.page
                                        showAnnotations = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 4.dp, height = 34.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(annotation.color))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Page ${annotation.page + 1} · " +
                                            if (annotation.isHighlight) "Highlight" else "Note",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        annotation.note.ifBlank {
                                            if (annotation.isHighlight) {
                                                "Highlighted area"
                                            } else {
                                                "Empty note"
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteAnnotation(annotation) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete annotation")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnnotations = false }) { Text("Close") }
            }
        )
    }

    val pending = draft
    if (pending != null) {
        var text by remember(pending) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { draft = null },
            title = { Text("New note") },
            text = {
                Column {
                    Text(
                        "Page ${pending.page + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Note") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onAddNote(
                                pending.page,
                                activeColor,
                                pending.anchor,
                                text
                            )
                        }
                        draft = null
                    },
                    enabled = text.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { draft = null }) { Text("Cancel") }
            }
        )
    }

    val selected = editing
    if (selected != null) {
        var text by remember(selected.id) { mutableStateOf(selected.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (selected.isHighlight) "Highlight" else "Note") },
            text = {
                Column {
                    Text(
                        "Page ${selected.page + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Note") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (text != selected.note) onUpdateNote(selected, text)
                        editing = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDeleteAnnotation(selected)
                            editing = null
                        }
                    ) { Text("Delete") }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun PageAnnotationsLayer(
    annotations: List<Annotation>,
    zoom: Float,
    pan: Offset,
    draftColor: Int,
    dragFrom: Offset?,
    dragTo: Offset?,
    canvasSize: Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val size = Size(this.size.width.coerceAtLeast(1f), this.size.height.coerceAtLeast(1f))

        annotations.forEach { annotation ->
            val color = Color(annotation.color)
            if (annotation.isHighlight) {
                annotation.rects.forEach { rect ->
                    drawRect(
                        color = color,
                        topLeft = Offset(rect.left * size.width, rect.top * size.height),
                        size = Size(rect.width * size.width, rect.height * size.height),
                        alpha = 0.6f,
                        blendMode = BlendMode.Multiply
                    )
                }
            } else {
                annotation.rects.firstOrNull()?.let { rect ->
                    val center = Offset(
                        rect.centerX() * size.width,
                        rect.centerY() * size.height
                    )
                    val radius = 9.dp.toPx()
                    drawCircle(color = color, radius = radius, center = center)
                    drawCircle(
                        color = Color.White,
                        radius = radius * 0.42f,
                        center = center
                    )
                }
            }
        }

        val start = dragFrom
        val end = dragTo
        if (start != null && end != null) {
            val first = start.toPagePosition(canvasSize, zoom, pan)
            val second = end.toPagePosition(canvasSize, zoom, pan)
            val rect = PageRect.fromDrag(first.x, first.y, second.x, second.y)
            drawRect(
                color = Color(draftColor),
                topLeft = Offset(rect.left * size.width, rect.top * size.height),
                size = Size(rect.width * size.width, rect.height * size.height),
                alpha = 0.7f
            )
        }
    }
}

/**
 * Turns a position in the page canvas (before the zoom/pan graphics layer) into
 * normalised page coordinates, i.e. the point under the finger on screen.
 */
private fun Offset.toPagePosition(size: Size, zoom: Float, pan: Offset): Offset {
    if (zoom == 1f && pan == Offset.Zero) {
        return Offset(x / size.width, y / size.height)
    }
    val center = Offset(size.width / 2f, size.height / 2f)
    val drawn = center + (this - center) * zoom + pan
    return Offset(drawn.x / size.width, drawn.y / size.height)
}

/** Topmost annotation on this page containing the normalised point. */
private fun List<Annotation>.hitTest(x: Float, y: Float): Annotation? =
    lastOrNull { annotation ->
        annotation.rects.any { rect ->
            x >= rect.left - HIT_SLOP &&
                x <= rect.right + HIT_SLOP &&
                y >= rect.top - HIT_SLOP &&
                y <= rect.bottom + HIT_SLOP
        }
    }

private const val HIT_SLOP = 0.012f

/**
 * Detail pane for a reference that exists in Mendeley but has no attached file.
 * The metadata is still fully usable; only the page view is unavailable.
 */
@Composable
private fun ContinuousReader(
    context: android.content.Context,
    uri: Uri,
    initialPageCount: Int,
    title: String,
    pagesPerView: Int,
    onBack: (() -> Unit)?
) {
    var pageCount by remember { mutableStateOf(initialPageCount) }
    LaunchedEffect(uri) {
        if (pageCount == 0) {
            val p = PdfPageLoader.render(context, uri, 0)
            if (p.pageCount > 0) pageCount = p.pageCount
            p.bitmap?.recycle()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pageCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (pagesPerView == 1) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(pageCount) { index ->
                    ContinuousPage(context = context, uri = uri, pageIndex = index, pageCount = pageCount)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items((pageCount + 1) / 2) { row ->
                    val left = row * 2
                    val right = left + 1
                    Row(Modifier.fillMaxWidth()) {
                        ContinuousPage(context = context, uri = uri, pageIndex = left, pageCount = pageCount, modifier = Modifier.weight(1f))
                        if (right < pageCount) {
                            ContinuousPage(context = context, uri = uri, pageIndex = right, pageCount = pageCount, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinuousPage(context: android.content.Context, uri: Uri, pageIndex: Int, pageCount: Int, modifier: Modifier = Modifier) {
    var bitmap by remember(uri, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, pageIndex) {
        val p = withContext(Dispatchers.IO) {
            runCatching { PdfPageLoader.render(context, uri, pageIndex) }.getOrNull()
        }
        bitmap = p?.bitmap
    }
    androidx.compose.runtime.DisposableEffect(uri, pageIndex) {
        onDispose {
            bitmap?.recycle()
            bitmap = null
        }
    }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(32.dp))
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Text(
            "Page ${pageIndex + 1} of $pageCount",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFBDBDBD),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NoPdfPane(document: Document, onBack: (() -> Unit)?) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (document.authors.isNotBlank()) append(document.authors)
                        if (document.year != null) {
                            if (isNotEmpty()) append(" · ")
                            append(document.year)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("No PDF attached", style = MaterialTheme.typography.titleLarge)
                Text(
                    "This reference was pulled from Mendeley without a file. " +
                        "The metadata is available here; attach a PDF to read and annotate it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                val details = buildList {
                    if (document.journal.isNotBlank()) add("Journal" to document.journal)
                    if (document.doi.isNotBlank()) add("DOI" to document.doi)
                    if (document.authors.isNotBlank()) add("Authors" to document.authors)
                }
                if (details.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        details.forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    label,
                                    modifier = Modifier.width(90.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
