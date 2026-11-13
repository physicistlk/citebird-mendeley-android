package com.refmanager.tablet.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.refmanager.tablet.BuildConfig
import com.refmanager.tablet.data.Collection
import com.refmanager.tablet.data.Document
import com.refmanager.tablet.data.LibraryFilter
import com.refmanager.tablet.data.MendeleyAuth
import com.refmanager.tablet.data.PdfFilter
import com.refmanager.tablet.data.SidebarSection
import com.refmanager.tablet.data.SortOrder
import com.refmanager.tablet.ui.theme.CiteColors

@Composable
fun AppRoot(viewModel: MainViewModel = viewModel()) {
    val allDocuments by viewModel.allDocuments.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val openTabs by viewModel.openTabs.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val annotations by viewModel.annotations.collectAsState()
    val filter by viewModel.filterState.collectAsState()
    val importing by viewModel.isImporting.collectAsState()
    val message by viewModel.message.collectAsState()
    val mendeley by viewModel.mendeley.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val labelCollections by viewModel.labelCollections.collectAsState()
    val folderMemberships by viewModel.folderMemberships.collectAsState()
    val collectionCounts by viewModel.collectionCounts.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var expandedDocId by remember { mutableStateOf<Long?>(null) }
    var newCollectionType by remember { mutableStateOf<String?>(null) }
    var newCollectionTitle by remember { mutableStateOf("") }
    var newCollectionColor by remember { mutableIntStateOf(CiteColors.LabelPalette.first()) }
    var editing by remember { mutableStateOf<Document?>(null) }
    var renaming by remember { mutableStateOf<Document?>(null) }
    var notesEditing by remember { mutableStateOf<Document?>(null) }
    var deleting by remember { mutableStateOf<Document?>(null) }
    var assigningCollections by remember { mutableStateOf<Document?>(null) }
    var saveToDiskDoc by remember { mutableStateOf<Document?>(null) }
    var leftSidebarVisible by remember { mutableStateOf(true) }
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var rightPanelVisible by remember(isLandscape) { mutableStateOf(isLandscape) }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPdf)
    }
    val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val doc = saveToDiskDoc
        saveToDiskDoc = null
        if (uri != null && doc != null) {
            viewModel.savePdfToUri(doc, uri)
        }
    }
    val mendeleyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.refreshMendeleyState()
        if (result.resultCode == Activity.RESULT_OK) viewModel.signInSucceeded()
    }

    val pullOrSignIn = {
        if (viewModel.hasValidMendeleyToken()) viewModel.pullMendeley()
        else mendeleyLauncher.launch(Intent(context, MendeleyAuthActivity::class.java))
    }

    BackHandler(enabled = selected != null) {
        viewModel.showLibrary()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = CiteColors.CanvasBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val openDoc = selected
        if (openDoc != null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (mendeley.busy) {
                    BackgroundSyncStrip(state = mendeley, onCancel = viewModel::cancelMendeley)
                }
                ReaderScreen(
                    document = openDoc,
                    annotations = annotations,
                    onBack = viewModel::showLibrary,
                    tabsBar = if (openTabs.isNotEmpty()) {
                        {
                            OpenTabsBar(
                                tabs = openTabs,
                                activeId = openDoc.id,
                                onSelectLibrary = viewModel::showLibrary,
                                onSelectTab = { id -> viewModel.select(id) },
                                onCloseTab = viewModel::closeTab,
                                showDivider = false,
                                transparentBg = true,
                                showLibraryTab = false
                            )
                        }
                    } else null,
                    onProgress = { page -> viewModel.updateProgress(openDoc.id, page) },
                    onAddHighlight = viewModel::addHighlight,
                    onAddUnderline = viewModel::addUnderline,
                    onAddInk = viewModel::addInk,
                    onAddNote = viewModel::addNote,
                    onUpdateNote = viewModel::updateNote,
                    onDeleteAnnotation = viewModel::deleteAnnotation
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (openTabs.isNotEmpty()) {
                    OpenTabsBar(
                        tabs = openTabs,
                        activeId = null,
                        onSelectLibrary = viewModel::showLibrary,
                        onSelectTab = { id -> viewModel.select(id) },
                        onCloseTab = viewModel::closeTab
                    )
                }
                CiteTopBar(
                    onToggleSidebar = { leftSidebarVisible = !leftSidebarVisible },
                    query = filter.query,
                    onQueryChange = viewModel::setQuery,
                    selectedCount = selectedIds.size,
                    importing = importing,
                    mendeley = mendeley,
                    onImportPdf = { picker.launch(arrayOf("application/pdf")) },
                    onLoadSample = viewModel::importSample,
                    onMendeleySignIn = { mendeleyLauncher.launch(Intent(context, MendeleyAuthActivity::class.java)) },
                    onMendeleyPull = pullOrSignIn,
                    onMendeleyCancel = viewModel::cancelMendeley,
                    onMendeleySignOut = viewModel::signOutMendeley,
                    onSelectAll = viewModel::selectAll,
                    onClearSelection = viewModel::clearSelection,
                    onBatchStar = { viewModel.batchStar(true) },
                    onBatchTrash = { viewModel.batchTrash(!filter.trashedOnly) }
                )

                HorizontalDivider(thickness = 1.dp, color = CiteColors.Border)

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sidebarWidth = (maxWidth * 0.21f).coerceIn(200.dp, 250.dp)
                    val inspectorWidth = (maxWidth * 0.20f).coerceIn(190.dp, 240.dp)

                    Row(Modifier.fillMaxSize()) {
                        AnimatedVisibility(visible = leftSidebarVisible) {
                            Row(Modifier.fillMaxHeight()) {
                                LeftSidebar(
                                    allDocuments = allDocuments,
                                    collections = collections,
                                    collectionCounts = collectionCounts,
                                    folderMemberships = folderMemberships,
                                    filter = filter,
                                    onSelectSection = { sec -> viewModel.setSection(sec) },
                                    onSelectCollection = { viewModel.setCollection(it) },
                                    onNewFolder = {
                                        newCollectionType = "folder"
                                        newCollectionTitle = ""
                                    },
                                    onNewLabel = {
                                        newCollectionType = "label"
                                        newCollectionTitle = ""
                                        newCollectionColor = CiteColors.LabelPalette.first()
                                    },
                                    onCleanupLabels = viewModel::cleanupUnusedLabels,
                                    onSharedPlaceholder = { viewModel.showMessage("Shared folders: place holder") },
                                    modifier = Modifier.width(sidebarWidth).fillMaxHeight()
                                )

                                VerticalDivider(thickness = 1.dp, color = CiteColors.Border)
                            }
                        }

                        CenterPaperList(
                            documents = documents,
                            filter = filter,
                            selectedIds = selectedIds,
                            expandedDocId = expandedDocId,
                            labelCollections = labelCollections,
                            folderMemberships = folderMemberships,
                            onToggleExpand = { id ->
                                expandedDocId = if (expandedDocId == id) null else id
                            },
                            onToggleSelect = viewModel::toggleSelection,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onOpenPdf = { doc -> viewModel.select(doc.id) },
                            onEdit = { editing = it },
                            onRename = { renaming = it },
                            onNotes = { notesEditing = it },
                            onAssignCollections = { assigningCollections = it },
                            onSaveToDisk = { doc ->
                                saveToDiskDoc = doc
                                val suggested = "${doc.title.take(40).replace(Regex("[^A-Za-z0-9._-]"), "_")}.pdf"
                                savePdfLauncher.launch(suggested)
                            },
                            onCopyBibtex = { doc ->
                                val key = (doc.authors.substringBefore(',').substringBefore(' ').ifBlank { "ref" }) + (doc.year?.toString() ?: "")
                                val bib = buildString {
                                    append("@article{$key,\n")
                                    append("  title = {${doc.title}},\n")
                                    if (doc.authors.isNotBlank()) append("  author = {${doc.authors}},\n")
                                    if (doc.journal.isNotBlank()) append("  journal = {${doc.journal}},\n")
                                    if (doc.year != null) append("  year = {${doc.year}},\n")
                                    if (doc.doi.isNotBlank()) append("  doi = {${doc.doi}},\n")
                                    append("}")
                                }
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("BibTeX", bib))
                                viewModel.showMessage("BibTeX copied")
                            },
                            onCopyDoi = { doc ->
                                if (doc.doi.isBlank()) {
                                    viewModel.showMessage("No DOI available")
                                } else {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("DOI", doc.doi))
                                    viewModel.showMessage("DOI copied: ${doc.doi}")
                                }
                            },
                            onBrowseJournal = { doc ->
                                if (doc.doi.isBlank()) {
                                    viewModel.showMessage("No DOI available for journal link")
                                } else {
                                    val url = if (doc.doi.startsWith("http")) doc.doi else "https://doi.org/${doc.doi}"
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        .onFailure { viewModel.showMessage("Cannot open browser") }
                                }
                            },
                            onPlaceholder = { label -> viewModel.showMessage("$label: place holder") },
                            onTrashOrDelete = { doc ->
                                if (doc.trashed) deleting = doc else viewModel.moveToTrash(doc)
                            },
                            onRestore = viewModel::restoreFromTrash,
                            onImportPdf = { picker.launch(arrayOf("application/pdf")) },
                            onMendeleyPull = pullOrSignIn,
                            onClearFilters = {
                                viewModel.setQuery("")
                                viewModel.showAllPapers()
                                viewModel.setDocTypeFilter(null)
                                if (filter.pdfFilter != PdfFilter.ALL) viewModel.setPdfFilter(filter.pdfFilter)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        AnimatedVisibility(visible = rightPanelVisible) {
                            Row(Modifier.fillMaxHeight()) {
                                VerticalDivider(thickness = 1.dp, color = CiteColors.Border)

                                RightFilterPanel(
                                    allDocuments = allDocuments,
                                    filteredDocuments = documents,
                                    folderMemberships = folderMemberships,
                                    selectedCount = selectedIds.size,
                                    filter = filter,
                                    onSort = viewModel::setSort,
                                    onPdfFilter = viewModel::setPdfFilter,
                                    onDocType = viewModel::setDocTypeFilter,
                                    modifier = Modifier.width(inspectorWidth).fillMaxHeight()
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { rightPanelVisible = !rightPanelVisible },
                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                        color = CiteColors.SidebarBg,
                        border = BorderStroke(1.dp, CiteColors.Border),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp)
                            .size(width = 22.dp, height = 38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (rightPanelVisible) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
                                contentDescription = if (rightPanelVisible) "Collapse panel" else "Expand panel",
                                tint = CiteColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { document ->
        MetadataDialog(
            document = document,
            onDismiss = { editing = null },
            onSave = { updated ->
                viewModel.saveMetadata(updated)
                editing = null
            }
        )
    }

    renaming?.let { document ->
        var newTitle by remember(document.id) { mutableStateOf(document.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename paper") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title / PDF label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newTitle.trim()
                    if (trimmed.isNotEmpty()) viewModel.saveMetadata(document.copy(title = trimmed))
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    notesEditing?.let { document ->
        var notesText by remember(document.id) { mutableStateOf(document.abstractText) }
        AlertDialog(
            onDismissRequest = { notesEditing = null },
            title = { Text("Notes & Abstract", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes / Abstract") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveMetadata(document.copy(abstractText = notesText.trim()))
                    notesEditing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { notesEditing = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { document ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete permanently?") },
            text = { Text("\"${document.title}\" will be permanently removed and its PDF file deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(document); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }

    assigningCollections?.let { document ->
        val docFolders = folderMemberships[document.id].orEmpty().map { it.id }.toSet()
        val docLabels = labelCollections[document.id].orEmpty().map { it.id }.toSet()
        AssignCollectionsDialog(
            document = document,
            collections = collections,
            assignedIds = docFolders + docLabels,
            onToggle = { colId, isAssigned ->
                viewModel.toggleDocumentCollection(document.id, colId, isAssigned)
            },
            onDismiss = { assigningCollections = null }
        )
    }

    if (newCollectionType != null) {
        val isLabel = newCollectionType == "label"
        AlertDialog(
            onDismissRequest = { newCollectionType = null; newCollectionTitle = "" },
            title = { Text(if (isLabel) "New Label" else "New Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newCollectionTitle,
                        onValueChange = { newCollectionTitle = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isLabel) {
                        Text("Color", style = MaterialTheme.typography.bodySmall, color = CiteColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CiteColors.LabelPalette.forEach { argb ->
                                val chosen = newCollectionColor == argb
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (chosen) 2.5.dp else 0.dp,
                                            color = if (chosen) CiteColors.TextPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { newCollectionColor = argb }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = newCollectionTitle.trim()
                    if (t.isNotEmpty()) {
                        viewModel.createCollection(
                            title = t,
                            type = newCollectionType!!,
                            color = if (isLabel) newCollectionColor else null
                        )
                    }
                    newCollectionType = null
                    newCollectionTitle = ""
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { newCollectionType = null; newCollectionTitle = "" }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CiteTopBar(
    onToggleSidebar: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCount: Int,
    importing: Boolean,
    mendeley: MendeleyState,
    onImportPdf: () -> Unit,
    onLoadSample: () -> Unit,
    onMendeleySignIn: () -> Unit,
    onMendeleyPull: () -> Unit,
    onMendeleyCancel: () -> Unit,
    onMendeleySignOut: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBatchStar: () -> Unit,
    onBatchTrash: () -> Unit
) {
    var addMenuOpen by remember { mutableStateOf(false) }
    var selectMenuOpen by remember { mutableStateOf(false) }
    var syncMenuOpen by remember { mutableStateOf(false) }

    Surface(
        color = CiteColors.TopBarBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hamburger + App brand + Add Papers dropdown button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "Toggle sidebar",
                    tint = CiteColors.TextSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onToggleSidebar)
                )
                Text(
                    text = "CiteBird",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = CiteColors.TitleNavy
                )

                Box {
                    Surface(
                        onClick = { addMenuOpen = true },
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, CiteColors.Border),
                        shadowElevation = 0.5.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(CiteColors.AddGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                "Add Papers",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = CiteColors.TextPrimary
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = CiteColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Import PDF from tablet…") },
                            leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                            onClick = { addMenuOpen = false; onImportPdf() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (mendeley.signedIn) "Pull from Mendeley" else "Connect Mendeley…") },
                            leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                            onClick = {
                                addMenuOpen = false
                                if (mendeley.signedIn) onMendeleyPull() else onMendeleySignIn()
                            }
                        )
                        if (BuildConfig.DEBUG) {
                            DropdownMenuItem(
                                text = { Text("Load sample PDF (debug)") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = { addMenuOpen = false; onLoadSample() }
                            )
                        }
                    }
                }
            }

            // Crisp white Search bar
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White,
                border = BorderStroke(1.dp, CiteColors.Border),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = CiteColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Search your papers",
                                fontSize = 13.sp,
                                color = CiteColors.TextMuted
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = CiteColors.TextPrimary),
                            cursorBrush = SolidColor(CiteColors.SidebarSelected),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = CiteColors.TextMuted,
                            modifier = Modifier.size(16.dp).clickable { onQueryChange("") }
                        )
                    }
                }
            }

            // Select dropdown
            Box {
                Surface(
                    onClick = { selectMenuOpen = true },
                    shape = RoundedCornerShape(4.dp),
                    color = if (selectedCount > 0) CiteColors.SidebarSelected else Color.White,
                    border = BorderStroke(1.dp, if (selectedCount > 0) CiteColors.SidebarSelected else CiteColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            if (selectedCount > 0) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (selectedCount > 0) Color.White else CiteColors.TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (selectedCount > 0) "Selected ($selectedCount)" else "Select",
                            fontSize = 13.sp,
                            color = if (selectedCount > 0) Color.White else CiteColors.TextPrimary
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = if (selectedCount > 0) Color.White else CiteColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(expanded = selectMenuOpen, onDismissRequest = { selectMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all visible") },
                        onClick = { selectMenuOpen = false; onSelectAll() }
                    )
                    if (selectedCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Clear selection") },
                            onClick = { selectMenuOpen = false; onClearSelection() }
                        )
                        DropdownMenuItem(
                            text = { Text("Star selected") },
                            onClick = { selectMenuOpen = false; onBatchStar() }
                        )
                        DropdownMenuItem(
                            text = { Text("Move selected to Trash") },
                            onClick = { selectMenuOpen = false; onBatchTrash() }
                        )
                    }
                }
            }

            // Non-blocking Background Mendeley Sync Pill
            if (importing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CiteColors.SidebarSelected)
            }

            Box {
                Surface(
                    onClick = { syncMenuOpen = true },
                    shape = RoundedCornerShape(4.dp),
                    color = if (mendeley.busy) Color(0xFFE3EFF7) else Color.White,
                    border = BorderStroke(1.dp, if (mendeley.busy) CiteColors.SidebarSelected else CiteColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (mendeley.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = CiteColors.SidebarSelected
                            )
                            Text(
                                text = if (mendeley.total > 0) "Syncing ${mendeley.done}/${mendeley.total}" else "Syncing…",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = CiteColors.TitleNavy
                            )
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Stop sync",
                                tint = CiteColors.TitleNavy,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onMendeleyCancel() }
                            )
                        } else {
                            if (mendeley.signedIn) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(CiteColors.AddGreen)
                                )
                            }
                            Icon(
                                Icons.Filled.Sync,
                                contentDescription = "Mendeley sync",
                                tint = CiteColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Mendeley",
                                fontSize = 12.5.sp,
                                color = CiteColors.TextPrimary
                            )
                        }
                    }
                }

                // Non-blocking popover for Mendeley status & actions
                DropdownMenu(
                    expanded = syncMenuOpen,
                    onDismissRequest = { syncMenuOpen = false },
                    modifier = Modifier.width(270.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when {
                                mendeley.busy -> "Background Mendeley Sync"
                                mendeley.signedIn -> "Mendeley Connected"
                                else -> "Mendeley Disconnected"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = CiteColors.TitleNavy
                        )
                        if (mendeley.busy) {
                            if (mendeley.total > 0) {
                                LinearProgressIndicator(
                                    progress = { (mendeley.done.toFloat() / mendeley.total).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = CiteColors.SidebarSelected
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CiteColors.SidebarSelected)
                            }
                            Text(
                                text = "${mendeley.done}/${mendeley.total} processed · ${mendeley.imported} imported · ${mendeley.skipped} skipped",
                                fontSize = 11.5.sp,
                                color = CiteColors.TextSecondary
                            )
                            Text(
                                text = "You can browse and read papers while syncing continues.",
                                fontSize = 11.sp,
                                color = CiteColors.TextMuted
                            )
                        } else if (mendeley.finished) {
                            Text(
                                text = mendeley.status ?: "Last sync finished",
                                fontSize = 11.5.sp,
                                color = CiteColors.TextSecondary
                            )
                        }
                        mendeley.error?.let {
                            Text(it, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                    if (mendeley.busy) {
                        DropdownMenuItem(
                            text = { Text("Stop background sync") },
                            onClick = { syncMenuOpen = false; onMendeleyCancel() }
                        )
                    } else if (mendeley.signedIn) {
                        DropdownMenuItem(
                            text = { Text("Pull library in background") },
                            leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                            onClick = { syncMenuOpen = false; onMendeleyPull() }
                        )
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = { syncMenuOpen = false; onMendeleySignOut() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Sign in to Mendeley…") },
                            onClick = { syncMenuOpen = false; onMendeleySignIn() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundSyncStrip(state: MendeleyState, onCancel: () -> Unit) {
    Surface(
        color = Color(0xFFE3EFF7),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = CiteColors.SidebarSelected)
            Text(
                text = if (state.total > 0) "Mendeley syncing in background: ${state.done}/${state.total} (${state.imported} imported)"
                else "Fetching Mendeley library in background…",
                fontSize = 12.sp,
                color = CiteColors.TitleNavy,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Stop",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = CiteColors.SidebarSelected,
                modifier = Modifier.clickable(onClick = onCancel)
            )
        }
    }
}

@Composable
private fun LeftSidebar(
    allDocuments: List<Document>,
    collections: List<Collection>,
    collectionCounts: Map<Long, Int>,
    folderMemberships: Map<Long, List<Collection>>,
    filter: LibraryFilter,
    onSelectSection: (SidebarSection) -> Unit,
    onSelectCollection: (Long) -> Unit,
    onNewFolder: () -> Unit,
    onNewLabel: () -> Unit,
    onCleanupLabels: () -> Unit,
    onSharedPlaceholder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDocs = remember(allDocuments) { allDocuments.filter { !it.trashed } }
    val recentlyReadCount = remember(activeDocs) { activeDocs.count { it.lastOpenedAt != null } }
    val starredCount = remember(activeDocs) { activeDocs.count { it.favorite } }
    val myPubsCount = remember(activeDocs) { activeDocs.count { it.authored } }
    val unsortedCount = remember(activeDocs, folderMemberships) {
        activeDocs.count { folderMemberships[it.id].isNullOrEmpty() }
    }
    val duplicatesCount = remember(activeDocs) {
        val titleCounts = activeDocs.groupingBy { it.title.trim().lowercase() }.eachCount()
        val doiCounts = activeDocs.filter { it.doi.isNotBlank() }.groupingBy { it.doi.trim().lowercase() }.eachCount()
        activeDocs.count {
            (titleCounts[it.title.trim().lowercase()] ?: 0) > 1 ||
                (it.doi.isNotBlank() && (doiCounts[it.doi.trim().lowercase()] ?: 0) > 1)
        }
    }
    val trashedCount = remember(allDocuments) { allDocuments.count { it.trashed } }
    val folders = remember(collections) { collections.filter { it.type == "folder" } }
    val labels = remember(collections) { collections.filter { it.type == "label" } }
    var labelSearchOpen by remember { mutableStateOf(false) }
    var labelQuery by remember { mutableStateOf("") }
    val visibleLabels = remember(labels, labelQuery) {
        if (labelQuery.isBlank()) labels
        else labels.filter { it.title.contains(labelQuery.trim(), ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .background(CiteColors.SidebarBg)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SidebarNavRow(
            label = "All References",
            count = activeDocs.size,
            selected = filter.section == SidebarSection.ALL_REFERENCES,
            icon = { tint -> Icon(Icons.AutoMirrored.Filled.Article, null, tint = tint, modifier = Modifier.size(16.dp)) },
            onClick = { onSelectSection(SidebarSection.ALL_REFERENCES) }
        )
        SidebarNavRow(
            label = "Recently Added",
            count = null,
            selected = filter.section == SidebarSection.RECENTLY_ADDED,
            icon = { tint -> Icon(Icons.Filled.Schedule, null, tint = tint, modifier = Modifier.size(16.dp)) },
            onClick = { onSelectSection(SidebarSection.RECENTLY_ADDED) }
        )
        SidebarNavRow(
            label = "Recently Read",
            count = recentlyReadCount,
            selected = filter.section == SidebarSection.RECENTLY_READ,
            icon = { tint -> Icon(Icons.Filled.BookmarkBorder, null, tint = tint, modifier = Modifier.size(16.dp)) },
            onClick = { onSelectSection(SidebarSection.RECENTLY_READ) }
        )
        SidebarNavRow(
            label = "Favorites",
            count = starredCount,
            selected = filter.section == SidebarSection.FAVORITES,
            icon = { tint -> Icon(Icons.Filled.Star, null, tint = tint, modifier = Modifier.size(16.dp)) },
            onClick = { onSelectSection(SidebarSection.FAVORITES) }
        )
        SidebarNavRow(
            label = "My Publications",
            count = myPubsCount,
            selected = filter.section == SidebarSection.MY_PUBLICATIONS,
            icon = { tint -> Icon(Icons.Filled.MenuBook, null, tint = tint, modifier = Modifier.size(16.dp)) },
            onClick = { onSelectSection(SidebarSection.MY_PUBLICATIONS) }
        )
        SidebarNavRow(
            label = "Unsorted",
            count = unsortedCount,
            selected = filter.section == SidebarSection.UNSORTED,
            icon = { tint -> Icon(Icons.Filled.Inbox, null, tint = tint, modifier = Modifier.size(16.dp)) },
            onClick = { onSelectSection(SidebarSection.UNSORTED) }
        )
        SidebarNavRow(
            label = "Duplicates",
            count = duplicatesCount,
            selected = filter.section == SidebarSection.DUPLICATES,
            icon = { tint -> Icon(Icons.Filled.ContentCopy, null, tint = tint, modifier = Modifier.size(16.dp)) },
            onClick = { onSelectSection(SidebarSection.DUPLICATES) }
        )
        SidebarNavRow(
            label = "Trash",
            count = trashedCount,
            selected = filter.section == SidebarSection.TRASH,
            icon = { tint -> Icon(Icons.Filled.DeleteOutline, null, tint = tint, modifier = Modifier.size(16.dp)) },
            onClick = { onSelectSection(SidebarSection.TRASH) }
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = CiteColors.BorderSubtle)
        Spacer(Modifier.height(8.dp))

        SidebarSectionHeader(title = "COLLECTIONS", onAdd = onNewFolder)
        if (folders.isEmpty()) {
            Text(
                "No folders yet",
                fontSize = 11.5.sp,
                color = CiteColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        } else {
            folders.forEach { folder ->
                val selected = filter.collectionId == folder.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) CiteColors.SidebarSelected else Color.Transparent)
                        .clickable { onSelectCollection(folder.id) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = if (selected) Color.White else CiteColors.FolderYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = folder.title,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Color.White else CiteColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "(${collectionCounts[folder.id] ?: 0})",
                        fontSize = 11.5.sp,
                        color = if (selected) Color.White.copy(alpha = 0.85f) else CiteColors.TextMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "LABELS",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CiteColors.TextSecondary,
                    letterSpacing = 0.4.sp
                )
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Filter labels",
                    tint = if (labelSearchOpen) CiteColors.SidebarSelected else CiteColors.TextMuted,
                    modifier = Modifier.size(13.dp).clickable {
                        labelSearchOpen = !labelSearchOpen
                        if (!labelSearchOpen) labelQuery = ""
                    }
                )
            }
            Surface(
                onClick = onNewLabel,
                shape = CircleShape,
                color = Color(0xFFE0E3E7),
                modifier = Modifier.size(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "Add label", tint = CiteColors.TextSecondary, modifier = Modifier.size(13.dp))
                }
            }
        }
        if (labelSearchOpen) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = Color.White,
                border = BorderStroke(1.dp, CiteColors.Border),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).height(26.dp)
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                    if (labelQuery.isEmpty()) Text("Filter labels…", fontSize = 11.sp, color = CiteColors.TextMuted)
                    BasicTextField(
                        value = labelQuery,
                        onValueChange = { labelQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 11.5.sp, color = CiteColors.TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (visibleLabels.isEmpty()) {
            Text(
                if (labels.isEmpty()) "No labels yet" else "No matching labels",
                fontSize = 11.5.sp,
                color = CiteColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        } else {
            visibleLabels.forEach { label ->
                val selected = filter.collectionId == label.id
                val badgeColor = Color(label.color ?: CiteColors.LabelPalette[1])
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) CiteColors.SidebarSelected.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelectCollection(label.id) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = label.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "(${collectionCounts[label.id] ?: 0})",
                        fontSize = 11.5.sp,
                        color = CiteColors.TextMuted
                    )
                }
            }
            Text(
                text = "clean up",
                fontSize = 11.sp,
                color = CiteColors.TextMuted,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .clickable(onClick = onCleanupLabels)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Shared Folders (placeholder per Paperpile UI)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSharedPlaceholder)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Filled.People, contentDescription = null, tint = CiteColors.TextMuted, modifier = Modifier.size(14.dp))
                Text(
                    text = "SHARED FOLDERS",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CiteColors.TextSecondary,
                    letterSpacing = 0.4.sp
                )
            }
            Surface(
                onClick = onSharedPlaceholder,
                shape = CircleShape,
                color = Color(0xFFE0E3E7),
                modifier = Modifier.size(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "Shared folder", tint = CiteColors.TextSecondary, modifier = Modifier.size(13.dp))
                }
            }
        }
        Text(
            text = "place holder",
            fontSize = 9.5.sp,
            color = CiteColors.TextMuted,
            modifier = Modifier.padding(start = 33.dp, bottom = 4.dp)
        )

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SidebarNavRow(
    label: String,
    count: Int?,
    selected: Boolean,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit
) {
    val fg = if (selected) Color.White else CiteColors.TextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) CiteColors.SidebarSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon(fg)
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (count != null) {
            Text(
                text = "($count)",
                fontSize = 11.5.sp,
                color = if (selected) Color.White.copy(alpha = 0.85f) else CiteColors.TextMuted
            )
        }
    }
}

@Composable
private fun SidebarSectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = CiteColors.TextSecondary,
            letterSpacing = 0.4.sp
        )
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = Color(0xFFE0E3E7),
            modifier = Modifier.size(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "Add $title", tint = CiteColors.TextSecondary, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun CenterPaperList(
    documents: List<Document>,
    filter: LibraryFilter,
    selectedIds: Set<Long>,
    expandedDocId: Long?,
    labelCollections: Map<Long, List<Collection>>,
    folderMemberships: Map<Long, List<Collection>>,
    onToggleExpand: (Long) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onToggleFavorite: (Document) -> Unit,
    onOpenPdf: (Document) -> Unit,
    onEdit: (Document) -> Unit,
    onRename: (Document) -> Unit,
    onNotes: (Document) -> Unit,
    onAssignCollections: (Document) -> Unit,
    onSaveToDisk: (Document) -> Unit,
    onCopyBibtex: (Document) -> Unit,
    onCopyDoi: (Document) -> Unit,
    onBrowseJournal: (Document) -> Unit,
    onPlaceholder: (String) -> Unit,
    onTrashOrDelete: (Document) -> Unit,
    onRestore: (Document) -> Unit,
    onImportPdf: () -> Unit,
    onMendeleyPull: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(CiteColors.CanvasBg)) {
        val hasActiveFilter = filter.query.isNotBlank() ||
            filter.pdfFilter != PdfFilter.ALL ||
            filter.docType != null
        if (hasActiveFilter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE9EDF2))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = buildString {
                        append("Filtered")
                        if (filter.pdfFilter != PdfFilter.ALL) append(" · ${filter.pdfFilter.name.replace('_', ' ').lowercase()}")
                        if (filter.docType != null) append(" · ${filter.docType}")
                        if (filter.query.isNotBlank()) append(" · \"${filter.query}\"")
                    },
                    fontSize = 12.sp,
                    color = CiteColors.TextSecondary
                )
                Text(
                    text = "Reset filters",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CiteColors.SidebarSelected,
                    modifier = Modifier.clickable(onClick = onClearFilters)
                )
            }
        }

        if (documents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (hasActiveFilter || filter.favoritesOnly || filter.trashedOnly || filter.collectionId != null)
                            "No papers match this view"
                        else
                            "Your CiteBird library is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = CiteColors.TextPrimary
                    )
                    Text(
                        text = if (hasActiveFilter) "Try clearing your search or filters"
                        else "Import a PDF from your tablet or pull your library from Mendeley",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CiteColors.TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onImportPdf) { Text("Import PDF") }
                        OutlinedButton(onClick = onMendeleyPull) { Text("Pull from Mendeley") }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    PaperCard(
                        document = doc,
                        labels = labelCollections[doc.id].orEmpty(),
                        folders = folderMemberships[doc.id].orEmpty(),
                        checked = doc.id in selectedIds,
                        expanded = expandedDocId == doc.id,
                        onToggleCheck = { onToggleSelect(doc.id) },
                        onToggleExpand = { onToggleExpand(doc.id) },
                        onToggleFavorite = { onToggleFavorite(doc) },
                        onOpenPdf = { onOpenPdf(doc) },
                        onEdit = { onEdit(doc) },
                        onRename = { onRename(doc) },
                        onNotes = { onNotes(doc) },
                        onAssignCollections = { onAssignCollections(doc) },
                        onSaveToDisk = { onSaveToDisk(doc) },
                        onCopyBibtex = { onCopyBibtex(doc) },
                        onCopyDoi = { onCopyDoi(doc) },
                        onBrowseJournal = { onBrowseJournal(doc) },
                        onPlaceholder = onPlaceholder,
                        onTrashOrDelete = { onTrashOrDelete(doc) },
                        onRestore = { onRestore(doc) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaperCard(
    document: Document,
    labels: List<Collection>,
    folders: List<Collection>,
    checked: Boolean,
    expanded: Boolean,
    onToggleCheck: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenPdf: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onNotes: () -> Unit,
    onAssignCollections: () -> Unit,
    onSaveToDisk: () -> Unit,
    onCopyBibtex: () -> Unit,
    onCopyDoi: () -> Unit,
    onBrowseJournal: () -> Unit,
    onPlaceholder: (String) -> Unit,
    onTrashOrDelete: () -> Unit,
    onRestore: () -> Unit
) {
    val hasPdf = document.fileUri.isNotBlank()
    var moreMenuOpen by remember(document.id) { mutableStateOf(false) }

    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, CiteColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggleCheck() },
                    modifier = Modifier.size(24.dp).padding(top = 2.dp)
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Title + inline label pills + star
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = document.title,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CiteColors.TitleNavy,
                            lineHeight = 19.sp,
                            modifier = Modifier.clickable(onClick = if (hasPdf) onOpenPdf else onToggleExpand)
                        )
                        labels.forEach { label ->
                            Surface(
                                color = Color(label.color ?: CiteColors.LabelPalette[1]),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = label.title,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = if (document.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Star",
                            tint = if (document.favorite) Color(0xFFE2B007) else CiteColors.TextMuted,
                            modifier = Modifier
                                .size(17.dp)
                                .clickable(onClick = onToggleFavorite)
                        )
                    }

                    // Authors
                    if (document.authors.isNotBlank()) {
                        Text(
                            text = document.authors,
                            fontSize = 12.5.sp,
                            color = CiteColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Journal, Year — Type, filed in Folder
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        val pubLine = buildAnnotatedString {
                            if (document.journal.isNotBlank()) {
                                withStyle(SpanStyle(color = CiteColors.JournalGreen, fontStyle = FontStyle.Italic)) {
                                    append(document.journal)
                                }
                                if (document.year != null) append(", ")
                            }
                            if (document.year != null) {
                                withStyle(SpanStyle(color = CiteColors.TextPrimary)) {
                                    append(document.year.toString())
                                }
                            }
                            if (document.docType.isNotBlank()) {
                                if (length > 0) append(" — ")
                                withStyle(SpanStyle(color = CiteColors.TextSecondary)) {
                                    append(document.docType)
                                }
                            }
                        }
                        if (pubLine.isNotEmpty()) {
                            Text(text = pubLine, fontSize = 12.sp)
                        }
                        if (folders.isNotEmpty()) {
                            Text("filed in", fontSize = 11.5.sp, color = CiteColors.TextMuted)
                            folders.take(2).forEach { folder ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier
                                        .background(Color(0xFFECEFF1), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Icon(Icons.Filled.Folder, null, tint = CiteColors.TextMuted, modifier = Modifier.size(12.dp))
                                    Text(folder.title, fontSize = 11.sp, color = CiteColors.TextSecondary)
                                }
                            }
                        }

                        // Paperpile-style "..." pill button
                        Box {
                            Surface(
                                onClick = { moreMenuOpen = true },
                                color = Color(0xFFE8EBEE),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreHoriz,
                                    contentDescription = "More actions",
                                    tint = CiteColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp).size(14.dp)
                                )
                            }
                            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                                if (hasPdf) {
                                    DropdownMenuItem(
                                        text = { Text("View PDF") },
                                        onClick = { moreMenuOpen = false; onOpenPdf() }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Edit metadata") },
                                    onClick = { moreMenuOpen = false; onEdit() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Notes & Abstract") },
                                    onClick = { moreMenuOpen = false; onNotes() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Folders & Labels") },
                                    onClick = { moreMenuOpen = false; onAssignCollections() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy BibTeX") },
                                    onClick = { moreMenuOpen = false; onCopyBibtex() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy DOI") },
                                    onClick = { moreMenuOpen = false; onCopyDoi() }
                                )
                                if (document.trashed) {
                                    DropdownMenuItem(
                                        text = { Text("Restore from Trash") },
                                        onClick = { moreMenuOpen = false; onRestore() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete forever", color = MaterialTheme.colorScheme.error) },
                                        onClick = { moreMenuOpen = false; onTrashOrDelete() }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Move to Trash", color = MaterialTheme.colorScheme.error) },
                                        onClick = { moreMenuOpen = false; onTrashOrDelete() }
                                    )
                                }
                            }
                        }
                    }

                    // Action row: subtle icons on left + View PDF button on right
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Article,
                                contentDescription = "Notes & Abstract",
                                tint = if (document.abstractText.isNotBlank()) CiteColors.SidebarSelected else CiteColors.TextMuted,
                                modifier = Modifier.size(15.dp).clickable(onClick = onNotes)
                            )
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit metadata",
                                tint = CiteColors.TextMuted,
                                modifier = Modifier.size(15.dp).clickable(onClick = onEdit)
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.Label,
                                contentDescription = "Folders & Labels",
                                tint = CiteColors.TextMuted,
                                modifier = Modifier.size(15.dp).clickable(onClick = onAssignCollections)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.clickable(onClick = onToggleExpand)
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = "Attachments",
                                    tint = if (hasPdf) CiteColors.SidebarSelected else CiteColors.TextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                if (hasPdf) {
                                    Text("1", fontSize = 11.sp, color = CiteColors.TextSecondary)
                                }
                            }
                            if (document.lastPage > 0 && (document.pageCount ?: 0) > 0) {
                                Text(
                                    text = "p. ${document.lastPage + 1}/${document.pageCount}",
                                    fontSize = 11.sp,
                                    color = CiteColors.TextMuted
                                )
                            }
                        }

                        when {
                            hasPdf -> {
                                OutlinedButton(
                                    onClick = onOpenPdf,
                                    shape = RoundedCornerShape(3.dp),
                                    border = BorderStroke(1.dp, CiteColors.Border),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFFF8F9FA),
                                        contentColor = CiteColors.TextPrimary
                                    ),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("View PDF", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            document.pdfRestricted -> {
                                Text("PDF restricted.", fontSize = 11.5.sp, color = CiteColors.TextMuted)
                            }
                            else -> {
                                Text("No PDF", fontSize = 11.5.sp, color = CiteColors.TextMuted)
                            }
                        }
                    }
                }
            }

            // Expandable inline Paperpile-style attachment & details drawer
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CiteColors.CardDrawerBg)
                        .border(BorderStroke(0.5.dp, CiteColors.BorderSubtle))
                        .padding(start = 46.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasPdf) {
                        val fileName = document.fileUri.substringAfterLast('/').ifBlank { "${document.title}.pdf" }
                        Text(
                            text = fileName,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1A65C2),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(onClick = onOpenPdf)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val meta = buildList {
                                if (document.pdfSize.isNotBlank()) add(document.pdfSize)
                                document.pageCount?.let { add("$it pages") }
                            }.joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, fontSize = 11.5.sp, color = CiteColors.TextSecondary)
                                Text("|", fontSize = 11.5.sp, color = CiteColors.Border)
                            }
                            Text(
                                "Rename",
                                fontSize = 11.5.sp,
                                color = CiteColors.TextSecondary,
                                modifier = Modifier.clickable(onClick = onRename)
                            )
                            Text("|", fontSize = 11.5.sp, color = CiteColors.Border)
                            if (document.trashed) {
                                Text(
                                    "Restore",
                                    fontSize = 11.5.sp,
                                    color = CiteColors.JournalGreen,
                                    modifier = Modifier.clickable(onClick = onRestore)
                                )
                                Text("|", fontSize = 11.5.sp, color = CiteColors.Border)
                                Text(
                                    "Delete forever",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable(onClick = onTrashOrDelete)
                                )
                            } else {
                                Text(
                                    "Remove",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable(onClick = onTrashOrDelete)
                                )
                            }
                            Text("|", fontSize = 11.5.sp, color = CiteColors.Border)
                            Text(
                                "Save to disk",
                                fontSize = 11.5.sp,
                                color = CiteColors.SidebarSelected,
                                modifier = Modifier.clickable(onClick = onSaveToDisk)
                            )
                            Text("|", fontSize = 11.5.sp, color = CiteColors.Border)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = CiteColors.TextMuted, modifier = Modifier.size(13.dp))
                                Text("Is main PDF", fontSize = 11.5.sp, color = CiteColors.TextMuted)
                            }
                        }

                        HorizontalDivider(color = CiteColors.BorderSubtle, modifier = Modifier.padding(vertical = 2.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaceholder("Attach supplementary PDF") }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.AttachFile, null, tint = CiteColors.TextMuted, modifier = Modifier.size(14.dp))
                                Text(
                                    "Attach supplementary PDF or other files...",
                                    fontSize = 11.5.sp,
                                    color = CiteColors.TextSecondary
                                )
                            }
                            Text(
                                "place holder",
                                fontSize = 9.5.sp,
                                color = CiteColors.TextMuted,
                                modifier = Modifier.padding(start = 20.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable(onClick = onBrowseJournal)
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, null, tint = CiteColors.TextMuted, modifier = Modifier.size(14.dp))
                            Text(
                                "Browse journal website for supplementary files",
                                fontSize = 11.5.sp,
                                color = CiteColors.TextSecondary
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Edit Metadata",
                                fontSize = 11.5.sp,
                                color = CiteColors.SidebarSelected,
                                modifier = Modifier.clickable(onClick = onEdit)
                            )
                            Text("|", fontSize = 11.5.sp, color = CiteColors.Border)
                            Text(
                                "Folders & Labels",
                                fontSize = 11.5.sp,
                                color = CiteColors.TextSecondary,
                                modifier = Modifier.clickable(onClick = onAssignCollections)
                            )
                            Text("|", fontSize = 11.5.sp, color = CiteColors.Border)
                            if (document.trashed) {
                                Text(
                                    "Restore",
                                    fontSize = 11.5.sp,
                                    color = CiteColors.JournalGreen,
                                    modifier = Modifier.clickable(onClick = onRestore)
                                )
                            } else {
                                Text(
                                    "Move to Trash",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable(onClick = onTrashOrDelete)
                                )
                            }
                        }
                    }

                    if (document.doi.isNotBlank()) {
                        Text("DOI: ${document.doi}", fontSize = 11.sp, color = CiteColors.TextMuted)
                    }
                    if (document.abstractText.isNotBlank()) {
                        Text(
                            text = document.abstractText,
                            fontSize = 11.5.sp,
                            color = CiteColors.TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Icon(
                            Icons.Filled.ExpandLess,
                            contentDescription = "Collapse",
                            tint = CiteColors.TextMuted,
                            modifier = Modifier.size(18.dp).clickable(onClick = onToggleExpand)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RightFilterPanel(
    allDocuments: List<Document>,
    filteredDocuments: List<Document>,
    folderMemberships: Map<Long, List<Collection>>,
    selectedCount: Int,
    filter: LibraryFilter,
    onSort: (SortOrder) -> Unit,
    onPdfFilter: (PdfFilter) -> Unit,
    onDocType: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDocs = remember(allDocuments) { allDocuments.filter { !it.trashed } }
    val hasPdfCount = remember(activeDocs) { activeDocs.count { it.fileUri.isNotBlank() } }
    val noPdfCount = remember(activeDocs) { activeDocs.count { it.fileUri.isBlank() && !it.pdfRestricted } }
    val restrictedCount = remember(activeDocs) { activeDocs.count { it.pdfRestricted } }
    val incompleteCount = remember(activeDocs) {
        activeDocs.count { it.authors.isBlank() || it.year == null || it.journal.isBlank() }
    }
    val notesCount = remember(activeDocs) { activeDocs.count { it.abstractText.isNotBlank() } }
    val unsortedCount = remember(activeDocs, folderMemberships) {
        activeDocs.count { folderMemberships[it.id].isNullOrEmpty() }
    }
    val typeCounts = remember(activeDocs) {
        activeDocs.groupingBy { it.docType.ifBlank { "Journal Article" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
    }
    var showAllTypes by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(CiteColors.SidebarBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Big counter block matching Paperpile screenshot
        Column {
            Text(
                text = filteredDocuments.size.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = CiteColors.TextSecondary
            )
            Text(
                text = "Papers found",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = CiteColors.TextSecondary
            )
            Text(
                text = "${filteredDocuments.size} shown, $selectedCount selected",
                fontSize = 11.5.sp,
                color = CiteColors.TextMuted
            )
        }

        // Sort section
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sort", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CiteColors.TextSecondary)
            SortOrder.entries.forEach { order ->
                val active = filter.sort == order
                val label = when (order) {
                    SortOrder.DATE_ADDED -> "Date Added"
                    SortOrder.PUBLICATION_DATE -> "Publication Date"
                    SortOrder.TITLE -> "Title"
                    SortOrder.JOURNAL -> "Journal"
                    SortOrder.AUTHOR -> "Author"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSort(order) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) CiteColors.TitleNavy else CiteColors.TextSecondary
                    )
                    if (active) {
                        Icon(
                            imageVector = if (filter.sortAscending) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = CiteColors.TitleNavy,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Filter section
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Filter", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CiteColors.TextSecondary)
            FacetFilterLink("Has no PDF", noPdfCount, filter.pdfFilter == PdfFilter.NO_PDF) {
                onPdfFilter(PdfFilter.NO_PDF)
            }
            FacetFilterLink("Unsorted", unsortedCount, filter.pdfFilter == PdfFilter.UNSORTED) {
                onPdfFilter(PdfFilter.UNSORTED)
            }
            FacetFilterLink("Has PDF", hasPdfCount, filter.pdfFilter == PdfFilter.HAS_PDF) {
                onPdfFilter(PdfFilter.HAS_PDF)
            }
            FacetFilterLink("PDF Restricted", restrictedCount, filter.pdfFilter == PdfFilter.RESTRICTED) {
                onPdfFilter(PdfFilter.RESTRICTED)
            }
            FacetFilterLink("Incomplete", incompleteCount, filter.pdfFilter == PdfFilter.INCOMPLETE) {
                onPdfFilter(PdfFilter.INCOMPLETE)
            }
            FacetFilterLink("Notes", notesCount, filter.pdfFilter == PdfFilter.HAS_NOTES) {
                onPdfFilter(PdfFilter.HAS_NOTES)
            }
        }

        // Type section
        if (typeCounts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Type", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CiteColors.TextSecondary)
                val visibleTypes = if (showAllTypes) typeCounts else typeCounts.take(4)
                visibleTypes.forEach { (typeName, count) ->
                    FacetFilterLink(typeName, count, filter.docType == typeName) {
                        onDocType(typeName)
                    }
                }
                if (typeCounts.size > 4) {
                    Text(
                        text = if (showAllTypes) "Less..." else "More...",
                        fontSize = 12.sp,
                        color = CiteColors.TextMuted,
                        modifier = Modifier.clickable { showAllTypes = !showAllTypes }.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenTabsBar(
    tabs: List<Document>,
    activeId: Long?,
    onSelectLibrary: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    showDivider: Boolean = true,
    transparentBg: Boolean = false,
    showLibraryTab: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (transparentBg) Color.Transparent else CiteColors.SidebarBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLibraryTab) {
                val libraryActive = activeId == null
                Surface(
                    onClick = onSelectLibrary,
                    shape = RoundedCornerShape(6.dp),
                    color = if (libraryActive) CiteColors.TitleNavy else Color.White,
                    border = BorderStroke(1.dp, if (libraryActive) CiteColors.TitleNavy else CiteColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = if (libraryActive) Color.White else CiteColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Library",
                            fontSize = 12.sp,
                            fontWeight = if (libraryActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (libraryActive) Color.White else CiteColors.TextSecondary
                        )
                    }
                }
            }

            tabs.forEach { doc ->
                val isActive = activeId == doc.id
                Surface(
                    onClick = { onSelectTab(doc.id) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isActive) CiteColors.TitleNavy else Color.White,
                    border = BorderStroke(1.dp, if (isActive) CiteColors.TitleNavy else CiteColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            tint = if (isActive) Color.White else CiteColors.TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = doc.title.ifBlank { "Untitled PDF" },
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) Color.White else CiteColors.TextSecondary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close tab",
                            tint = if (isActive) Color.White.copy(alpha = 0.85f) else CiteColors.TextMuted,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onCloseTab(doc.id) }
                        )
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = CiteColors.Border)
        }
    }
}

@Composable
private fun FacetFilterLink(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Text(
        text = "$label ($count)",
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) CiteColors.TitleNavy else CiteColors.TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    )
}

@Composable
private fun AssignCollectionsDialog(
    document: Document,
    collections: List<Collection>,
    assignedIds: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folders & Labels", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            if (collections.isEmpty()) {
                Text("Create a folder or label in the left sidebar first.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val folders = collections.filter { it.type == "folder" }
                    val labels = collections.filter { it.type == "label" }
                    if (folders.isNotEmpty()) {
                        Text("Folders", style = MaterialTheme.typography.labelLarge, color = CiteColors.TextSecondary)
                        folders.forEach { col ->
                            val checked = col.id in assignedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(col.id, checked) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { onToggle(col.id, checked) })
                                Icon(Icons.Filled.FolderOpen, null, tint = CiteColors.FolderYellow, modifier = Modifier.size(18.dp))
                                Text(col.title)
                            }
                        }
                    }
                    if (labels.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Labels", style = MaterialTheme.typography.labelLarge, color = CiteColors.TextSecondary)
                        labels.forEach { col ->
                            val checked = col.id in assignedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(col.id, checked) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { onToggle(col.id, checked) })
                                Surface(
                                    color = Color(col.color ?: CiteColors.LabelPalette[1]),
                                    shape = RoundedCornerShape(3.dp)
                                ) {
                                    Text(
                                        col.title,
                                        fontSize = 11.5.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun MetadataDialog(document: Document, onDismiss: () -> Unit, onSave: (Document) -> Unit) {
    var title by remember(document.id) { mutableStateOf(document.title) }
    var authors by remember(document.id) { mutableStateOf(document.authors) }
    var year by remember(document.id) { mutableStateOf(document.year?.toString() ?: "") }
    var journal by remember(document.id) { mutableStateOf(document.journal) }
    var doi by remember(document.id) { mutableStateOf(document.doi) }
    var docType by remember(document.id) { mutableStateOf(document.docType) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit metadata") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = authors, onValueChange = { authors = it }, label = { Text("Authors") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) year = it },
                        label = { Text("Year") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(value = journal, onValueChange = { journal = it }, label = { Text("Journal") }, singleLine = true, modifier = Modifier.weight(2f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = docType, onValueChange = { docType = it }, label = { Text("Type") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = doi, onValueChange = { doi = it }, label = { Text("DOI") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    document.copy(
                        title = title.trim().ifBlank { document.title },
                        authors = authors.trim(),
                        year = year.toIntOrNull(),
                        journal = journal.trim(),
                        doi = doi.trim(),
                        docType = docType.trim().ifBlank { "Journal Article" }
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
