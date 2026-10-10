package com.example.ttsnote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ttsnote.R
import com.example.ttsnote.data.Note
import com.example.ttsnote.data.Section

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    onNoteClick: (Long) -> Unit,
    onAddNoteClick: () -> Unit
) {
    val context = LocalContext.current
    val notes by viewModel.filteredNotes.collectAsState(initial = emptyList())
    val sections by viewModel.allSections.collectAsState()
    val selectedSectionId by viewModel.selectedSectionId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    var showAddSectionDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                reader.readText()
            }
            if (!content.isNullOrBlank()) {
                val fileName = it.path?.substringAfterLast("/")
                    ?: context.getString(R.string.imported_note)
                viewModel.insertNote(Note(title = fileName, content = content, sectionId = selectedSectionId ?: 0))
            }
        }
    }

    if (showAddSectionDialog) {
        var sectionName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSectionDialog = false },
            title = { Text(stringResource(R.string.add_section)) },
            text = {
                OutlinedTextField(
                    value = sectionName,
                    onValueChange = { sectionName = it },
                    label = { Text(stringResource(R.string.section_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (sectionName.isNotBlank()) {
                        viewModel.insertSection(sectionName)
                        showAddSectionDialog = false
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSectionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        if (isSearching) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text(stringResource(R.string.search_ellipsis)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        } else {
                            Text(stringResource(R.string.app_display_name), fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        if (isSearching) {
                            IconButton(onClick = { 
                                isSearching = false
                                viewModel.updateSearchQuery("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
                            }
                        }
                    },
                    actions = {
                        if (!isSearching) {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                            }
                            IconButton(onClick = { filePickerLauncher.launch("text/plain") }) {
                                Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.import_text_file))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                
                // Section Tabs
                if (!isSearching) {
                    ScrollableTabRow(
                        selectedTabIndex = if (selectedSectionId == null) 0 else sections.indexOfFirst { it.id == selectedSectionId } + 1,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                        divider = {},
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[if (selectedSectionId == null) 0 else (sections.indexOfFirst { it.id == selectedSectionId } + 1).coerceAtLeast(0)])
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedSectionId == null,
                            onClick = { viewModel.selectSection(null) },
                            text = { Text(stringResource(R.string.all_sections)) }
                        )
                        sections.forEach { section ->
                            Tab(
                                selected = selectedSectionId == section.id,
                                onClick = { viewModel.selectSection(section.id) },
                                text = { Text(section.name) }
                            )
                        }
                        IconButton(onClick = { showAddSectionDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_section))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = onAddNoteClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_note), modifier = Modifier.size(36.dp))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (notes.isEmpty() && !isSearching) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        var isVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { isVisible = true }

                        AnimatedVisibility(
                            visible = isVisible,
                            enter = slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = tween(durationMillis = 500)
                            ) + fadeIn()
                        ) {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteNote(note)
                                        true
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        else -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                                enableDismissFromStartToEnd = false
                            ) {
                                NoteItem(
                                    note = note, 
                                    onClick = { onNoteClick(note.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteItem(note: Note, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (note.title.isBlank()) stringResource(R.string.untitled_note) else note.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.pin),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule, 
                    contentDescription = null, 
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Notes,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.empty_notes_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            stringResource(R.string.empty_notes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
