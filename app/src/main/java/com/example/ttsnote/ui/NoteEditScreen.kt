package com.example.ttsnote.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import com.example.ttsnote.LocaleHelper
import com.example.ttsnote.R
import com.example.ttsnote.data.Note
import com.example.ttsnote.tts.TtsService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    viewModel: NoteViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var contentValue by remember { mutableStateOf(TextFieldValue("")) }
    
    var ttsService by remember { mutableStateOf<TtsService?>(null) }
    val currentTtsPosition by ttsService?.currentPosition?.collectAsState(initial = 0) ?: remember { mutableStateOf(0) }
    val isTtsPlaying by ttsService?.isPlaying?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
    val currentSentenceRange by ttsService?.currentSentenceRange?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }
    val currentSentence by ttsService?.currentSentence?.collectAsState(initial = "") ?: remember { mutableStateOf("") }
    val activeBlockIndex by ttsService?.activeBlockIndex?.collectAsState(initial = -1) ?: remember { mutableStateOf(-1) }

    val scrollState = rememberScrollState()
    val lazyListState = rememberLazyListState()
    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    var isFullScreen by remember { mutableStateOf(false) }
    var isPreviewMode by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var isSearchingContent by remember { mutableStateOf(false) }
    var innerSearchQuery by remember { mutableStateOf("") }

    var playbackMode by remember { mutableStateOf<PlaybackMode>(PlaybackMode.Full) }
    val speechRates = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var currentRateIndex by remember { mutableStateOf(1) }
    
    val globalFontSize by viewModel.globalFontSize.collectAsState()
    
    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    val headerColor = MaterialTheme.colorScheme.primary
    val wysiwygTransformation = remember(currentSentenceRange, isPreviewMode, globalFontSize, highlightColor, headerColor, innerSearchQuery) {
        MarkdownWysiwygTransformation(
            highlightRange = currentSentenceRange,
            highlightColor = highlightColor,
            headerColor = headerColor,
            hideSymbols = isPreviewMode,
            baseFontSize = globalFontSize,
            searchQuery = innerSearchQuery
        )
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                ttsService = (service as TtsService.TtsBinder).getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                ttsService = null
            }
        }
    }

    DisposableEffect(context) {
        context.bindService(Intent(context, TtsService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        onDispose { 
            // Final save of scroll position when leaving
            note?.let {
                val finalScrollIndex = if (isPreviewMode) lazyListState.firstVisibleItemIndex else scrollState.value
                viewModel.updateNote(it.copy(lastScrollIndex = finalScrollIndex))
            }
            context.unbindService(serviceConnection) 
        }
    }

    LaunchedEffect(noteId) {
        if (noteId != -1L) {
            viewModel.getNoteById(noteId)?.let {
                note = it
                title = it.title
                contentValue = TextFieldValue(it.content)
                if (it.lastPlayedParagraphIndex != -1) playbackMode = PlaybackMode.Cursor
                
                // Restore scroll position
                if (isPreviewMode && it.lastScrollIndex > 0) {
                    delay(500)
                    lazyListState.scrollToItem(it.lastScrollIndex)
                }

                val savedRateIndex = speechRates.indexOf(it.speechRate)
                if (savedRateIndex != -1) {
                    currentRateIndex = savedRateIndex
                    ttsService?.setSpeed(it.speechRate)
                }
            }
        }
    }

    LaunchedEffect(title, contentValue.text) {
        if (title.isBlank() && contentValue.text.isBlank()) return@LaunchedEffect
        delay(3000)
        if (noteId == -1L) {
            val newNote = Note(title = title, content = contentValue.text)
            viewModel.insertNote(newNote)
            viewModel.handleAutoTitle(newNote, contentValue.text)
        } else {
            note?.let {
                val updated = it.copy(
                    title = title, 
                    content = contentValue.text, 
                    updatedAt = System.currentTimeMillis(),
                    lastScrollIndex = if (isPreviewMode) lazyListState.firstVisibleItemIndex else scrollState.value,
                    lastPlayedParagraphIndex = ttsService?.currentBlockIndex ?: it.lastPlayedParagraphIndex,
                    lastPlayedCharOffset = ttsService?.currentSentenceInBlockStart ?: it.lastPlayedCharOffset
                )
                viewModel.updateNote(updated)
                viewModel.handleAutoTitle(updated, contentValue.text)
                note = updated
            }
        }
    }

    // Auto-scroll
    LaunchedEffect(activeBlockIndex) {
        if (isPreviewMode && isTtsPlaying && activeBlockIndex != -1) {
            lazyListState.animateScrollToItem(activeBlockIndex)
        }
    }

    LaunchedEffect(currentTtsPosition) {
        if (!isPreviewMode && isTtsPlaying && currentTtsPosition > 0) {
            textLayoutResult?.let { layout ->
                val line = layout.getLineForOffset(currentTtsPosition)
                scrollState.animateScrollTo(layout.getLineTop(line).toInt())
            }
        }
    }

    // Dynamic TOC parsing
    val semanticBlocks = remember(contentValue.text) {
        com.example.ttsnote.tts.SemanticParser.parse(contentValue.text)
    }
    val tocItems = remember(semanticBlocks) {
        semanticBlocks.filter { it.type == com.example.ttsnote.tts.BlockType.Header }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState
        ) {
            SettingsSheetContent(
                viewModel = viewModel,
                ttsService = ttsService,
                currentNote = note,
                onVoiceClick = { showVoiceDialog = true },
                onClose = { showSettingsSheet = false }
            )
        }
    }

    if (showVoiceDialog) {
        val voices = remember { ttsService?.getVoices() ?: emptyList() }
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text(stringResource(R.string.select_hq_voice)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(voices) { voice ->
                        val isHighQuality = voice.quality >= android.speech.tts.Voice.QUALITY_HIGH
                        val isNetwork = voice.isNetworkConnectionRequired
                        
                        ListItem(
                            headlineContent = { Text(voice.name) },
                            supportingContent = { 
                                Row {
                                    if (isHighQuality) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(stringResource(R.string.high_quality), fontSize = 10.sp) },
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                    if (isNetwork) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(stringResource(R.string.requires_network), fontSize = 10.sp) }
                                        )
                                    } else {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(stringResource(R.string.offline_available), fontSize = 10.sp) }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                ttsService?.setVoice(voice)
                                showVoiceDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.smart_toc),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
                    itemsIndexed(tocItems) { _, header ->
                        NavigationDrawerItem(
                            label = { 
                                Text(
                                    stripMarkdown(header.content), 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (header.level == 1) FontWeight.Bold else FontWeight.Normal
                                    )
                                ) 
                            },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    if (isPreviewMode) {
                                        val index = semanticBlocks.indexOf(header)
                                        if (index != -1) {
                                            lazyListState.animateScrollToItem(index)
                                        }
                                    }
                                    // Sync TTS
                                    val bIndex = ttsService?.getBlockIndexForOffset(header.startIndex) ?: 0
                                    ttsService?.startPlaying(contentValue.text, bIndex, 0)
                                }
                            },
                            modifier = Modifier.padding(
                                start = ((header.level - 1) * 16).dp,
                                top = 4.dp,
                                bottom = 4.dp
                            )
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(visible = !isFullScreen, enter = fadeIn(), exit = fadeOut()) {
                    TopAppBar(
                        title = { 
                            if (isSearchingContent) {
                                TextField(
                                    value = innerSearchQuery,
                                    onValueChange = { innerSearchQuery = it },
                                    placeholder = { Text(stringResource(R.string.search_content)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )
                            } else {
                                TextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    placeholder = { Text(stringResource(R.string.title_hint), fontWeight = FontWeight.Bold) },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        navigationIcon = {
                            Row {
                                if (isSearchingContent) {
                                    IconButton(onClick = { 
                                        isSearchingContent = false
                                        innerSearchQuery = ""
                                    }) {
                                        Icon(Icons.Default.Close, stringResource(R.string.close_search))
                                    }
                                } else {
                                    IconButton(onClick = onBackClick) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                                    }
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, stringResource(R.string.toc))
                                    }
                                }
                            }
                        },
                        actions = {
                            if (!isSearchingContent) {
                                IconButton(onClick = { isSearchingContent = true }) {
                                    Icon(Icons.Default.Search, stringResource(R.string.search))
                                }
                                IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                                    Icon(if (isPreviewMode) Icons.Default.EditNote else Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.toggle_preview))
                                }
                                IconButton(onClick = { showSettingsSheet = true }) {
                                    Icon(Icons.Default.Tune, stringResource(R.string.settings))
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isPreviewMode) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { /* offset -> */ },
                                    onLongPress = { isFullScreen = !isFullScreen }
                                )
                            }
                            .padding(horizontal = 16.dp)
                    ) {
                        items(semanticBlocks) { block ->
                            when (block.type) {
                                com.example.ttsnote.tts.BlockType.Header -> {
                                    val headerContent = parseInlineMarkdown(block.content)
                                    Text(
                                        text = headerContent,
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = (globalFontSize + 12 - (block.level * 2)).sp
                                        ),
                                        modifier = Modifier
                                            .padding(vertical = 8.dp)
                                            .clickable {
                                                ttsService?.startPlaying(contentValue.text, semanticBlocks.indexOf(block), 0)
                                            }
                                    )
                                }
                                com.example.ttsnote.tts.BlockType.Table -> {
                                    MarkdownTable(rows = block.rows, baseFontSize = globalFontSize)
                                }
                                com.example.ttsnote.tts.BlockType.Quote -> {
                                    MarkdownQuote(content = block.content, baseFontSize = globalFontSize)
                                }
                                com.example.ttsnote.tts.BlockType.Callout -> {
                                    MarkdownCallout(content = block.content, baseFontSize = globalFontSize)
                                }
                                com.example.ttsnote.tts.BlockType.Divider -> {
                                    MarkdownDivider()
                                }
                                else -> {
                                    val blockStart = block.startIndex
                                    val highlightStyle = SpanStyle(background = highlightColor)

                                    val blockAnnotatedString = remember(block.content, currentSentenceRange) {
                                        val inlineRendered = parseInlineMarkdown(block.content)
                                        buildAnnotatedString {
                                            append(inlineRendered)
                                            currentSentenceRange?.let { range ->
                                                val s = (range.first - blockStart).coerceIn(0, block.content.length)
                                                val e = (range.second - blockStart).coerceIn(0, block.content.length)
                                                if (s < e) {
                                                    addStyle(highlightStyle, 0, length) 
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = blockAnnotatedString,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = (globalFontSize + 4).sp,
                                            lineHeight = (globalFontSize + 16).sp
                                        ),
                                        modifier = Modifier
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                ttsService?.startPlaying(contentValue.text, semanticBlocks.indexOf(block), 0)
                                            }
                                    )
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(200.dp)) }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        textLayoutResult?.let { layout ->
                                            val transformedOffset = layout.getOffsetForPosition(offset)
                                            val originalOffset = wysiwygTransformation.filter(androidx.compose.ui.text.AnnotatedString(contentValue.text))
                                                .offsetMapping.transformedToOriginal(transformedOffset)
                                            
                                            val text = contentValue.text
                                            val sentenceStart = findSentenceStart(text, originalOffset)
                                            
                                            val blockIndex = ttsService?.getBlockIndexForOffset(sentenceStart) ?: 0
                                            val internalOffset = ttsService?.getCharOffsetInBlock(sentenceStart, blockIndex) ?: 0
                                            ttsService?.startPlaying(text, blockIndex, internalOffset)
                                        }
                                    },
                                    onLongPress = { isFullScreen = !isFullScreen }
                                )
                            }
                            .padding(16.dp)
                    ) {
                        BasicTextField(
                            value = contentValue,
                            onValueChange = { contentValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = globalFontSize.sp,
                                lineHeight = (globalFontSize + 10).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = wysiwygTransformation,
                            onTextLayout = { textLayoutResult = it },
                            decorationBox = { innerTextField ->
                                if (contentValue.text.isEmpty()) {
                                    Text(stringResource(R.string.start_typing), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                innerTextField()
                            }
                        )
                        Spacer(modifier = Modifier.height(200.dp))
                    }
                }

                // Floating Control Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Current Sentence Display
                    AnimatedVisibility(
                        visible = isTtsPlaying && currentSentence.isNotBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .fillMaxWidth(0.9f),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 4.dp,
                            shadowElevation = 8.dp
                        ) {
                            Text(
                                text = currentSentence,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(40.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(40.dp)
                            )
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            TextButton(onClick = {
                                playbackMode = when (playbackMode) {
                                    PlaybackMode.Full -> PlaybackMode.Selection
                                    PlaybackMode.Selection -> PlaybackMode.Cursor
                                    PlaybackMode.Cursor -> PlaybackMode.Full
                                }
                            }) {
                                val modeIcon = when (playbackMode) {
                                    PlaybackMode.Full -> "📖"
                                    PlaybackMode.Selection -> "✂️"
                                    PlaybackMode.Cursor -> "📍"
                                }
                                Text(modeIcon, fontSize = 20.sp)
                            }

                            IconButton(onClick = { ttsService?.previousParagraph() }) {
                                Icon(Icons.Default.SkipPrevious, null)
                            }

                            LargeFloatingActionButton(
                                onClick = {
                                    if (isTtsPlaying) ttsService?.pausePlayback()
                                    else {
                                        if (currentSentenceRange != null) {
                                            ttsService?.resumePlayback()
                                        } else {
                                            if (playbackMode == PlaybackMode.Full) {
                                                note?.let {
                                                    if (it.lastPlayedParagraphIndex != -1) {
                                                        ttsService?.startPlaying(it.content, it.lastPlayedParagraphIndex, it.lastPlayedCharOffset)
                                                    } else {
                                                        ttsService?.startPlaying(contentValue.text)
                                                    }
                                                } ?: ttsService?.startPlaying(contentValue.text)
                                            } else if (playbackMode == PlaybackMode.Cursor) {
                                                val offset = contentValue.selection.start
                                                val sStart = findSentenceStart(contentValue.text, offset)
                                                val bIndex = ttsService?.getBlockIndexForOffset(sStart) ?: 0
                                                val iOffset = ttsService?.getCharOffsetInBlock(sStart, bIndex) ?: 0
                                                ttsService?.startPlaying(contentValue.text, bIndex, iOffset)
                                            } else {
                                                val selected = contentValue.text.substring(contentValue.selection.start, contentValue.selection.end)
                                                if (selected.isNotBlank()) ttsService?.startPlaying(selected)
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = Color.White
                                )
                            }

                            IconButton(onClick = { ttsService?.nextParagraph() }) {
                                Icon(Icons.Default.SkipNext, null)
                            }

                            TextButton(onClick = {
                                currentRateIndex = (currentRateIndex + 1) % speechRates.size
                                val newRate = speechRates[currentRateIndex]
                                ttsService?.setSpeed(newRate)
                                note?.let {
                                    viewModel.updateNote(it.copy(speechRate = newRate))
                                }
                            }) {
                                Text("${speechRates[currentRateIndex]}x", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSheetContent(
    viewModel: NoteViewModel, 
    ttsService: TtsService?, 
    currentNote: Note?,
    onVoiceClick: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val globalFontSize by viewModel.globalFontSize.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val sections by viewModel.allSections.collectAsState()
    var showSectionPicker by remember { mutableStateOf(false) }
    
    val language by viewModel.language.collectAsState()
    val activity = context as? ComponentActivity

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.playback_visual_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.language)) },
            supportingContent = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = language == LocaleHelper.LANG_EN,
                        onClick = {
                            if (language != LocaleHelper.LANG_EN) {
                                LocaleHelper.setLanguage(context, LocaleHelper.LANG_EN)
                                viewModel.updateLanguage(LocaleHelper.LANG_EN)
                                activity?.recreate()
                            }
                        },
                        label = { Text(stringResource(R.string.language_english)) }
                    )
                    FilterChip(
                        selected = language == LocaleHelper.LANG_ZH,
                        onClick = {
                            if (language != LocaleHelper.LANG_ZH) {
                                LocaleHelper.setLanguage(context, LocaleHelper.LANG_ZH)
                                viewModel.updateLanguage(LocaleHelper.LANG_ZH)
                                activity?.recreate()
                            }
                        },
                        label = { Text(stringResource(R.string.language_chinese)) }
                    )
                }
            },
            leadingContent = { Icon(Icons.Default.Language, null) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.theme_mode)) },
            supportingContent = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ThemeOption(
                        icon = Icons.Default.BrightnessAuto,
                        label = stringResource(R.string.theme_auto),
                        isSelected = themeMode == ThemeMode.Auto,
                        onClick = { viewModel.updateThemeMode(ThemeMode.Auto) }
                    )
                    ThemeOption(
                        icon = Icons.Default.LightMode,
                        label = stringResource(R.string.theme_light),
                        isSelected = themeMode == ThemeMode.Light,
                        onClick = { viewModel.updateThemeMode(ThemeMode.Light) }
                    )
                    ThemeOption(
                        icon = Icons.Default.DarkMode,
                        label = stringResource(R.string.theme_dark),
                        isSelected = themeMode == ThemeMode.Dark,
                        onClick = { viewModel.updateThemeMode(ThemeMode.Dark) }
                    )
                }
            },
            leadingContent = { Icon(Icons.Default.Palette, null) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        ListItem(
            headlineContent = { Text(stringResource(R.string.font_size, globalFontSize)) },
            supportingContent = {
                Slider(
                    value = globalFontSize.toFloat(),
                    onValueChange = { viewModel.updateFontSize(it.toInt()) },
                    valueRange = 12f..40f,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            },
            leadingContent = { Icon(Icons.Default.TextFormat, null) }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.section_belonging)) },
            supportingContent = { 
                val sectionName = sections.find { it.id == currentNote?.sectionId }?.name
                    ?: stringResource(R.string.uncategorized)
                Text(sectionName) 
            },
            leadingContent = { Icon(Icons.Default.Folder, null) },
            modifier = Modifier.clickable { showSectionPicker = true }
        )

        if (showSectionPicker) {
            AlertDialog(
                onDismissRequest = { showSectionPicker = false },
                title = { Text(stringResource(R.string.select_section)) },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.uncategorized)) },
                                modifier = Modifier.clickable {
                                    currentNote?.let { viewModel.updateNote(it.copy(sectionId = 0)) }
                                    showSectionPicker = false
                                }
                            )
                        }
                        items(sections) { section ->
                            ListItem(
                                headlineContent = { Text(section.name) },
                                modifier = Modifier.clickable {
                                    currentNote?.let { viewModel.updateNote(it.copy(sectionId = section.id)) }
                                    showSectionPicker = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSectionPicker = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.switch_hq_voice)) },
            supportingContent = { Text(stringResource(R.string.switch_hq_voice_desc)) },
            leadingContent = { Icon(Icons.Default.RecordVoiceOver, null) },
            modifier = Modifier.clickable { onVoiceClick() }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.sleep_timer)) },
            trailingContent = {
                var timerText by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = timerText,
                    onValueChange = { 
                        timerText = it.filter { c -> c.isDigit() }
                        ttsService?.setSleepTimer(it.toIntOrNull() ?: 0)
                    },
                    modifier = Modifier.width(80.dp),
                    placeholder = { Text(stringResource(R.string.minutes_short)) },
                    singleLine = true
                )
            },
            leadingContent = { Icon(Icons.Default.Timer, null) }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.pitch_adjust)) },
            supportingContent = {
                var pitch by remember { mutableStateOf(1.0f) }
                Slider(
                    value = pitch,
                    onValueChange = { 
                        pitch = it
                        ttsService?.setPitch(it)
                    },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            },
            leadingContent = { Icon(Icons.Default.GraphicEq, null) }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.hq_voice_settings)) },
            supportingContent = { Text(stringResource(R.string.hq_voice_settings_desc)) },
            leadingContent = { Icon(Icons.Default.Settings, null) },
            modifier = Modifier.clickable { 
                val intent = Intent().apply {
                    action = "com.android.settings.TTS_SETTINGS"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                onClose()
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.done))
        }
    }
}

fun findSentenceStart(text: String, offset: Int): Int {
    if (text.isEmpty()) return 0
    val delimiters = charArrayOf('。', '！', '？', '.', '!', '?', '\n')
    val start = text.substring(0, offset.coerceIn(0, text.length)).lastIndexOfAny(delimiters)
    return if (start == -1) 0 else (start + 1).coerceAtMost(text.length)
}

@Composable
fun ThemeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
