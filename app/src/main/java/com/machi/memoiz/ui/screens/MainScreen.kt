package com.machi.memoiz.ui.screens

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Path
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.machi.memoiz.R
import com.machi.memoiz.data.entity.MemoType
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.ContentProcessingLauncher
import coil.compose.AsyncImage
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.machi.memoiz.analytics.AnalyticsManager
import com.machi.memoiz.ui.components.CampusNoteTextAligned
import com.machi.memoiz.ui.components.ChromeStyleUrlBar
import com.machi.memoiz.ui.dialog.CatCommentDialogActivity
import com.machi.memoiz.ui.dialog.GenAiStatusCheckDialogActivity
import com.machi.memoiz.ui.dialog.TutorialDialog
import com.machi.memoiz.ui.theme.Mplus1CodeReguar
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val memoGroups by viewModel.memoGroups.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val memoTypeFilter by viewModel.memoTypeFilter.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val customCategories by viewModel.customCategories.collectAsState()
    val expandedCategories by viewModel.expandedCategories.collectAsState()
    val categoryOrder by viewModel.categoryOrder.collectAsState()
    val shouldShowTutorial by viewModel.shouldShowTutorial.collectAsState()
    val preferencesLoaded by viewModel.preferencesLoaded.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val uiDisplayModeSetting by viewModel.uiDisplayMode.collectAsState()
    val newMemoIds by viewModel.newMemoIds.collectAsState()
    val categoriesWithNew by viewModel.categoriesWithNew.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTutorialDialog by rememberSaveable { mutableStateOf(false) }
    var showConsentDialogBeforeTutorial by rememberSaveable { mutableStateOf(false) }

    // Track whether we've already shown the GenAI dialog during this Activity lifecycle.
    var genAiDialogShown by remember { mutableStateOf(false) }
    var genAiTextAvailable by remember { mutableStateOf(false) }

    // Lightweight GenAI textAvailability check for enabling Cat Comment FAB
    LaunchedEffect(Unit) {
        try {
            val manager = com.machi.memoiz.service.GenAiStatusManager(context.applicationContext)
            val status = manager.checkAll()
            genAiTextAvailable = status.textGeneration == com.google.mlkit.genai.common.FeatureStatus.AVAILABLE
            manager.close()
        } catch (e: Exception) {
            // If check fails, default to false
            genAiTextAvailable = false
        }
    }

    LaunchedEffect(memoGroups) {
        viewModel.ensureCategoryOrder(memoGroups.map { it.category })
    }

    val toastMessage by viewModel.toastMessage.collectAsState()
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }


    LaunchedEffect(shouldShowTutorial, preferencesLoaded) {
        if (preferencesLoaded && shouldShowTutorial) {
            val alreadyAnswered = viewModel.isConsentDialogShownSync()
            val requestedFromSettings = viewModel.isShowTutorialOnNextLaunchSync()
            if (alreadyAnswered || requestedFromSettings) {
                // Skip consent dialog and show tutorial directly
                showTutorialDialog = true
            } else {
                showConsentDialogBeforeTutorial = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (genAiDialogShown) {
            Log.d("MainScreen", "Skipping GenAi check: genAiDialogShown=true")
            return@LaunchedEffect
        }
        if (preferencesLoaded && shouldShowTutorial) {
            Log.d("MainScreen", "Skipping GenAi check: preferencesLoaded and shouldShowTutorial are true => letting tutorial onFinished run the check")
            return@LaunchedEffect
        }
        val manager = com.machi.memoiz.service.GenAiStatusManager(context.applicationContext)
        try {
            Log.d("MainScreen", "Starting GenAi status check (genAiDialogShown=$genAiDialogShown, preferencesLoaded=$preferencesLoaded, shouldShowTutorial=$shouldShowTutorial)")
            val status = manager.checkAll()
            Log.d("MainScreen", "GenAi checkAll returned: image=${status.imageDescription} text=${status.textGeneration} sum=${status.summarization}")
            if (status.anyUnavailable() || status.anyDownloadable()) {
                Log.d("MainScreen", "GenAi status requires user attention; launching dialog")
                GenAiStatusCheckDialogActivity.start(context.applicationContext)
                genAiDialogShown = true
            } else {
                Log.d("MainScreen", "GenAi status OK: no dialog needed")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                Log.d("MainScreen", "GenAi check failed with exception; attempting to launch dialog as fallback: ${e.message}")
                GenAiStatusCheckDialogActivity.start(context.applicationContext)
                genAiDialogShown = true
            } catch (ignored: Exception) {
            }
        } finally {
            manager.close()
        }
    }


    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showSortDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Any?>(null) }
    var deleteTargetIsCustomCategory by remember { mutableStateOf(false) }
    var pendingReanalyzeMemo by remember { mutableStateOf<Memo?>(null) }
    var manualCategoryMemo by remember { mutableStateOf<Memo?>(null) }
    var manualCategoryInput by remember { mutableStateOf("") }
    var manualCategoryError by remember { mutableStateOf<String?>(null) }
    var showCreateMemoDialog by remember { mutableStateOf(false) }
    var createMemoText by remember { mutableStateOf("") }
    var createMemoError by remember { mutableStateOf<String?>(null) }
    val hasManualOrder = categoryOrder.isNotEmpty()

    // Capture resource strings used inside non-composable lambdas
    val errorCategoryNameEmpty = stringResource(R.string.error_category_name_empty)
    val createMemoErrorEmpty = stringResource(R.string.dialog_create_memo_error_empty)

    val clearManualCategoryState: () -> Unit = {
        manualCategoryMemo = null
        manualCategoryInput = ""
        manualCategoryError = null
    }

    val handleManualCategorySave: (String) -> Unit = save@{ rawInput ->
        val trimmedCategory = rawInput.trim()
        if (trimmedCategory.isEmpty()) {
            manualCategoryError = errorCategoryNameEmpty
            return@save
        }
        manualCategoryMemo?.let { memo ->
            val exists = availableCategories.any { it.equals(trimmedCategory, ignoreCase = true) }
            if (!exists) {
                viewModel.addCustomCategoryIfMissing(trimmedCategory)
            }
            viewModel.updateMemoCategory(memo, trimmedCategory, memo.subCategory)
        }
        clearManualCategoryState()
    }

    val displayedCategories by rememberUpdatedState(newValue = memoGroups.map { it.category })
    val autoHideFab = memoGroups.isNotEmpty()

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        listState = lazyListState,
        onMove = { from, to ->
            viewModel.onCategoryMoved(from.index, to.index, displayedCategories)
        }
    )
    var isFabExpanded by remember { mutableStateOf(false) }
    var isFabVisible by remember { mutableStateOf(true) }

    LaunchedEffect(autoHideFab, lazyListState) {
        if (!autoHideFab) {
            isFabVisible = true
            return@LaunchedEffect
        }
        var previousIndex = lazyListState.firstVisibleItemIndex
        var previousOffset = lazyListState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                lazyListState.isScrollInProgress,
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset
            )
        }.collect { (isScrolling, index, offset) ->
            if (!isScrolling) {
                isFabVisible = true
            } else {
                val scrollingDown = index > previousIndex ||
                        (index == previousIndex && offset > previousOffset)
                isFabVisible = !scrollingDown
            }
            previousIndex = index
            previousOffset = offset
        }
    }

    val appliedTypeLabel = when (memoTypeFilter) {
        MemoType.TEXT -> stringResource(R.string.memo_type_text)
        MemoType.WEB_SITE -> stringResource(R.string.memo_type_web_site)
        MemoType.IMAGE -> stringResource(R.string.memo_type_image)
        else -> null
    }
    val fabCreateMemoLabel = stringResource(R.string.fab_create_memo)
    val fabPickImageLabel = stringResource(R.string.fab_pick_image)
    val fabPasteLabel = stringResource(R.string.fab_paste_clipboard)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = ContentProcessingLauncher.enqueueWorkWithResult(context, null, uri, forceCopyImage = true, creationSource = "main_ui_image_picker")
            when (result) {
                ContentProcessingLauncher.EnqueueResult.Enqueued -> { /* no-op */ }
                ContentProcessingLauncher.EnqueueResult.NothingToCategorize ->
                    Toast.makeText(context, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
                ContentProcessingLauncher.EnqueueResult.DuplicateIgnored ->
                    Toast.makeText(context, R.string.toast_already_exists, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera capture: create a temp file URI via FileProvider and launch external camera app.
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoFile by remember { mutableStateOf<java.io.File?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            // Image captured successfully. Enqueue processing (force copy) and clear pending uri.
            val result = ContentProcessingLauncher.enqueueWorkWithResult(context, null, uri, forceCopyImage = true, creationSource = "main_ui_camera")
            when (result) {
                ContentProcessingLauncher.EnqueueResult.Enqueued -> { /* no-op */ }
                ContentProcessingLauncher.EnqueueResult.NothingToCategorize ->
                    Toast.makeText(context, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
                ContentProcessingLauncher.EnqueueResult.DuplicateIgnored ->
                    Toast.makeText(context, R.string.toast_already_exists, Toast.LENGTH_SHORT).show()
            }
            pendingPhotoUri = null
        } else {
            // Failed or cancelled: cleanup temp file if exists
            try {
                pendingPhotoFile?.let { file ->
                    if (file.exists()) file.delete()
                }
            } catch (_: Exception) { }
            pendingPhotoFile = null
            pendingPhotoUri = null
        }
    }

    val selectedCategoryFilter = categoryFilter
    val filterNote = when {
        appliedTypeLabel != null && selectedCategoryFilter != null -> stringResource(
            R.string.filter_note_type_and_category,
            appliedTypeLabel,
            selectedCategoryFilter
        )
        appliedTypeLabel != null -> stringResource(R.string.filter_note_type_only, appliedTypeLabel)
        selectedCategoryFilter != null -> stringResource(R.string.filter_note_category_only, selectedCategoryFilter)
        else -> null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerContent(
                    currentFilter = categoryFilter,
                    memoTypeFilter = memoTypeFilter,
                    availableCategories = availableCategories,
                    customCategories = customCategories,
                    onFilterSelected = { filter ->
                        viewModel.setCategoryFilter(filter)
                        scope.launch { drawerState.close() }
                    },
                    onMemoTypeFilterSelected = { typeFilter ->
                        viewModel.setMemoTypeFilter(typeFilter)
                        scope.launch { drawerState.close() }
                    },
                    onAddCategoryClick = {
                        showAddCategoryDialog = true
                    },
                    onSettingsClick = {
                        onNavigateToSettings()
                        scope.launch { drawerState.close() }
                    },
                    onRemoveCustomCategory = { category ->
                        deleteTarget = category
                        deleteTargetIsCustomCategory = true
                        showDeleteConfirmationDialog = true
                    },
                    showMemoizStatus = genAiTextAvailable // Pass the flag here
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text(stringResource(R.string.search_hint)) },
                                    modifier = Modifier
                                        .weight(if (isProcessing) 0.65f else 1f)
                                        .defaultMinSize(minWidth = 0.dp)
                                        .padding(end = if (isProcessing) 4.dp else 0.dp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    ),
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = stringResource(R.string.cd_clear_search)
                                                )
                                            }
                                        }
                                    }
                                )
                                if (isProcessing) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    AnalyzingIndicator()
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, stringResource(R.string.cd_open_drawer))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                if (hasManualOrder) {
                                    Icons.Default.SwapVert
                                } else {
                                    when (sortMode) {
                                        SortMode.CREATED_DESC -> Icons.Default.DateRange
                                        SortMode.CATEGORY_NAME -> Icons.Default.SortByAlpha
                                        SortMode.MOST_USED -> Icons.Default.Star
                                    }
                                },
                                stringResource(R.string.cd_sort)
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(visible = isFabVisible) {
                    AnimatedContent(targetState = isFabExpanded, label = "fab") { expanded ->
                        if (expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ExtendedFloatingActionButton(
                                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    text = { Text(text = fabCreateMemoLabel) },
                                    onClick = {
                                        showCreateMemoDialog = true
                                        isFabExpanded = false
                                    }
                                )
                                ExtendedFloatingActionButton(
                                    text = { Text(text = fabPickImageLabel) },
                                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                                    onClick = {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                        isFabExpanded = false
                                    }
                                )
                                ExtendedFloatingActionButton(
                                    text = { Text(text = fabPasteLabel) },
                                    icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                                    onClick = {
                                        val result = ContentProcessingLauncher.enqueueFromClipboardWithResult(context)
                                        when (result) {
                                            ContentProcessingLauncher.EnqueueResult.Enqueued -> { /* no-op */ }
                                            ContentProcessingLauncher.EnqueueResult.NothingToCategorize ->
                                                Toast.makeText(context, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
                                            ContentProcessingLauncher.EnqueueResult.DuplicateIgnored ->
                                                Toast.makeText(context, R.string.toast_already_exists, Toast.LENGTH_SHORT).show()
                                        }
                                         isFabExpanded = false
                                    }
                                )
                                if (genAiTextAvailable) {
                                    ExtendedFloatingActionButton(
                                        text = { Text(text = stringResource(R.string.fab_cat_comment_label)) },
                                        icon = {
                                            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                                Text(text = "🐱", fontSize = 16.sp)
                                            }
                                        },
                                        onClick = {
                                            Log.d("MainScreen", "CatComment FAB clicked")
                                            isFabExpanded = false
                                            try {
                                                val act = context as? android.app.Activity
                                                if (act != null) {
                                                    CatCommentDialogActivity.start(act)
                                                } else {
                                                    CatCommentDialogActivity.start(context.applicationContext)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("MainScreen", "Failed to start CatCommentDialogActivity: ${e.message}", e)
                                            }
                                        }
                                    )
                                }
                                // Camera capture button (no runtime CAMERA permission requested)
                                ExtendedFloatingActionButton(
                                    text = { Text(text = stringResource(R.string.fab_take_photo)) },
                                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                    onClick = {
                                        // Create temp file in cache and launch camera
                                        try {
                                            val photoFile = java.io.File.createTempFile(
                                                "memoiz_camera_${System.currentTimeMillis()}",
                                                ".jpg",
                                                context.cacheDir
                                            ).apply { deleteOnExit() }
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                            pendingPhotoFile = photoFile
                                            pendingPhotoUri = uri
                                            takePictureLauncher.launch(uri)
                                            isFabExpanded = false
                                        } catch (e: Exception) {
                                            Log.e("MainScreen", "Failed to create temp file for camera: ${e.message}", e)
                                            Toast.makeText(context, R.string.error_open_image, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                SmallFloatingActionButton(onClick = { isFabExpanded = false }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fab_close_menu))
                                }
                            }
                        } else {
                            FloatingActionButton(onClick = { isFabExpanded = true }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_paste_clipboard))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                filterNote?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (memoGroups.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.no_memo),
                                contentDescription = stringResource(R.string.cd_no_memo_image),
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty() || categoryFilter != null) {
                                    stringResource(R.string.no_matching_memos_found)
                                } else {
                                    stringResource(R.string.empty_state_message)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .reorderable(reorderState),
                        state = lazyListState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(memoGroups, key = { it.category }) { group ->
                            ReorderableItem(state = reorderState, key = group.category) { _ ->
                                val isCustomCategory = customCategories.contains(group.category)
                                CategoryAccordion(
                                    group = group,
                                    isExpanded = group.category in expandedCategories,
                                    context = context,
                                    isCustomCategory = isCustomCategory,
                                    categoriesWithNew = categoriesWithNew,
                                    newMemoIds = newMemoIds,
                                    onHeaderClick = { viewModel.toggleCategoryExpanded(group.category) },
                                    onDeleteCategory = {
                                        deleteTarget = group.category
                                        deleteTargetIsCustomCategory = isCustomCategory
                                        showDeleteConfirmationDialog = true
                                    },
                                    onDeleteMemo = { memo ->
                                        deleteTarget = memo
                                        deleteTargetIsCustomCategory = false
                                        showDeleteConfirmationDialog = true
                                    },
                                    onEditCategory = { memo ->
                                        manualCategoryMemo = memo
                                        manualCategoryInput = memo.category
                                        manualCategoryError = null
                                        isFabExpanded = false
                                    },
                                    onReanalyzeMemo = { memo ->
                                        pendingReanalyzeMemo = memo
                                        isFabExpanded = false
                                    },
                                    onMemoUsed = { id -> viewModel.recordMemoUsed(id) },
                                    dragHandle = Modifier.detectReorder(reorderState),
                                    uiDisplayMode = uiDisplayModeSetting,
                                    genAiTextAvailable = genAiTextAvailable
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        SortModeDialog(
            currentMode = sortMode,
            onModeSelected = { mode ->
                viewModel.setSortMode(mode)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false },
            hasManualOrder = hasManualOrder
        )
    }

    if (showAddCategoryDialog) {
        AddCustomCategoryDialog(
            existingCategories = availableCategories,
            onConfirm = { categoryName ->
                viewModel.addCustomCategory(categoryName)
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    if (showDeleteConfirmationDialog) {
        val isCategory = deleteTarget is String
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            icon = {
                Icon(
                    imageVector = if (isCategory) Icons.Default.Warning else Icons.Default.Delete,
                    contentDescription = null,
                    tint = if (isCategory) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
            },
            title = {
                Text(
                    text = when {
                        isCategory && deleteTargetIsCustomCategory -> stringResource(R.string.dialog_delete_custom_category_title)
                        isCategory -> stringResource(R.string.dialog_delete_category_title)
                        else -> stringResource(R.string.dialog_delete_memo_title)
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isCategory) {
                            val messageRes = if (deleteTargetIsCustomCategory) {
                                R.string.dialog_delete_custom_category_message
                            } else {
                                R.string.dialog_delete_category_message
                            }
                            stringResource(messageRes)
                        } else {
                            stringResource(R.string.dialog_delete_memo_message)
                        }
                    )
                    if (isCategory && !deleteTargetIsCustomCategory) {
                        Text(
                            text = stringResource(R.string.dialog_delete_category_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (val target = deleteTarget) {
                            is String -> {
                                if (deleteTargetIsCustomCategory) {
                                    viewModel.removeCustomCategory(target)
                                } else {
                                    viewModel.deleteCategory(target)
                                }
                            }
                            is Memo -> viewModel.deleteMemo(target)
                        }
                        deleteTargetIsCustomCategory = false
                        showDeleteConfirmationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmationDialog = false
                    deleteTargetIsCustomCategory = false
                }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (pendingReanalyzeMemo != null) {
        AlertDialog(
            onDismissRequest = { pendingReanalyzeMemo = null },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_reanalyze_title)) },
            text = {
                Text(stringResource(R.string.dialog_reanalyze_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingReanalyzeMemo?.let { memo ->
                        viewModel.reanalyzeMemo(context, memo.id)
                    }
                    pendingReanalyzeMemo = null
                }) {
                    Text(stringResource(R.string.dialog_reanalyze_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReanalyzeMemo = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (manualCategoryMemo != null) {
        ManualCategoryDialog(
            availableCategories = availableCategories,
            categoryValue = manualCategoryInput,
            errorMessage = manualCategoryError,
            onCategoryChange = { updated ->
                manualCategoryInput = updated
                manualCategoryError = null
            },
            onDismiss = clearManualCategoryState,
            onSave = handleManualCategorySave
        )
    }

    if (showConsentDialogBeforeTutorial) {
        AlertDialog(
            onDismissRequest = {
                viewModel.setAnalyticsCollectionEnabled(false)
                com.machi.memoiz.analytics.AnalyticsManager.setCollectionEnabled(context, false)
                try {
                    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
                } catch (e: Exception) {
                    Log.w("MainScreen", "Failed to set Crashlytics collection: ${e.message}")
                }
                viewModel.setConsentDialogShownSync(true)
                showConsentDialogBeforeTutorial = false
                showTutorialDialog = true
            },
            title = { Text(stringResource(R.string.tutorial_step_consent_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.report),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.tutorial_step_consent_body))

                    Spacer(modifier = Modifier.height(12.dp))
                }
            },
            confirmButton = {
                 TextButton(onClick = {
                     viewModel.setAnalyticsCollectionEnabled(true)
                     com.machi.memoiz.analytics.AnalyticsManager.setCollectionEnabled(context, true)
                     try {
                         FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
                     } catch (e: Exception) {
                         Log.w("MainScreen", "Failed to enable Crashlytics: ${e.message}")
                     }
                     viewModel.setConsentDialogShownSync(true)
                     showConsentDialogBeforeTutorial = false
                     showTutorialDialog = true
                 }) {
                     Text(stringResource(R.string.ok))
                 }
             },
             dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val privacyUrl = stringResource(R.string.privacy_policy_url)
                    TextButton(onClick = {
                        try {
                            val uri = android.net.Uri.parse(privacyUrl)
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }) {
                        Text(stringResource(R.string.privacy_policy_init))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = {
                        viewModel.setAnalyticsCollectionEnabled(false)
                        AnalyticsManager.setCollectionEnabled(context, false)
                        try {
                            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
                        } catch (e: Exception) {
                            Log.w("MainScreen", "Failed to disable Crashlytics: ${e.message}")
                        }
                        viewModel.setConsentDialogShownSync(true)
                        showConsentDialogBeforeTutorial = false
                        showTutorialDialog = true
                    }) {
                        Text(stringResource(R.string.no))
                    }
                }
            }
        )
    }

    if (showTutorialDialog) {
        TutorialDialog(viewModel = viewModel,
            onFinished = {
                showTutorialDialog = false
                viewModel.markTutorialSeen()
                if (genAiDialogShown) return@TutorialDialog
                scope.launch {
                    val manager = com.machi.memoiz.service.GenAiStatusManager(context.applicationContext)
                    try {
                        val status = manager.checkAll()
                        if (status.anyUnavailable() || status.anyDownloadable()) {
                            GenAiStatusCheckDialogActivity.start(context.applicationContext)
                            genAiDialogShown = true
                        }
                     } catch (e: Exception) {
                         e.printStackTrace()
                         try {
                            GenAiStatusCheckDialogActivity.start(context.applicationContext)
                             genAiDialogShown = true
                         } catch (ignored: Exception) {
                         }
                     } finally {
                         manager.close()
                     }
                }
             },
            includeMemoizCommentStep = genAiTextAvailable
         )
     }

    if (showCreateMemoDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateMemoDialog = false
                createMemoText = ""
                createMemoError = null
            },
            title = { Text(stringResource(R.string.dialog_create_memo_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_create_memo_message))
                    OutlinedTextField(
                        value = createMemoText,
                        onValueChange = {
                            createMemoText = it
                            createMemoError = null
                        },
                        label = { Text(stringResource(R.string.dialog_create_memo_placeholder)) },
                        isError = createMemoError != null,
                        supportingText = createMemoError?.let { err -> { Text(err) } },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = createMemoText.trim()
                    if (trimmed.isEmpty()) {
                        createMemoError = createMemoErrorEmpty
                        return@TextButton
                    }
                    val result = ContentProcessingLauncher.enqueueManualMemoWithResult(context, trimmed)
                    when (result) {
                        ContentProcessingLauncher.EnqueueResult.Enqueued -> { /* no-op */ }
                        ContentProcessingLauncher.EnqueueResult.NothingToCategorize ->
                            Toast.makeText(context, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
                        ContentProcessingLauncher.EnqueueResult.DuplicateIgnored ->
                            Toast.makeText(context, R.string.toast_already_exists, Toast.LENGTH_SHORT).show()
                    }
                     showCreateMemoDialog = false
                     createMemoText = ""
                     createMemoError = null
                 }) {

                    Text(stringResource(R.string.dialog_add))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateMemoDialog = false
                    createMemoText = ""
                    createMemoError = null
                }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    Log.d("MainScreen", "MainScreen composed entry: preferencesLoaded=$preferencesLoaded shouldShowTutorial=$shouldShowTutorial genAiDialogShown=$genAiDialogShown")

    // Observe lifecycle to reliably detect when the Activity/Screen is left.
    // Mark the main screen as seen on ON_STOP so new memos are treated as read when user leaves.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                Log.d("MainScreen", "Lifecycle ON_STOP: marking main screen seen and clearing genAiDialogShown")
                viewModel.markMainScreenSeen()
                genAiDialogShown = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Log.d("MainScreen", "MainScreen disposed; removed lifecycle observer")
        }
    }
}

@Composable
private fun NavigationDrawerContent(
    currentFilter: String?,
    memoTypeFilter: String?,
    availableCategories: List<String>,
    customCategories: Set<String>,
    onFilterSelected: (String?) -> Unit,
    onMemoTypeFilterSelected: (String?) -> Unit,
    onAddCategoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRemoveCustomCategory: (String) -> Unit,
    showMemoizStatus: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(250.dp)
    ) {
        val drawerContext = LocalContext.current
        val bannerPainter = painterResource(id = R.drawable.top_banner)
        val bannerHeight = 120.dp
        Image(
            painter = bannerPainter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .padding(horizontal = 16.dp,vertical = 13.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )

        NavigationDrawerItem(
             label = { Text(stringResource(R.string.drawer_settings)) },
             selected = false,
             onClick = onSettingsClick,
             icon = { Icon(Icons.Default.Settings, contentDescription = null) },
             modifier = Modifier.height(45.dp)
         )

        if (showMemoizStatus) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_status)) },
                selected = false,
                onClick = { com.machi.memoiz.ui.dialog.CatStatusDialogActivity.start(drawerContext) },
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.height(45.dp)
            )
        }

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_add_category)) },
            selected = false,
            onClick = onAddCategoryClick,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            modifier = Modifier.height(45.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            item {
                Text(
                    text = stringResource(R.string.drawer_filter_by_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_all_categories)) },
                    selected = memoTypeFilter == null,
                    onClick = { onMemoTypeFilterSelected(null) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.memo_type_text)) },
                    selected = memoTypeFilter == MemoType.TEXT,
                    onClick = { onMemoTypeFilterSelected(MemoType.TEXT) },
                    icon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.memo_type_web_site)) },
                    selected = memoTypeFilter == MemoType.WEB_SITE,
                    onClick = { onMemoTypeFilterSelected(MemoType.WEB_SITE) },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.memo_type_image)) },
                    selected = memoTypeFilter == MemoType.IMAGE,
                    onClick = { onMemoTypeFilterSelected(MemoType.IMAGE) },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.drawer_filter_by_category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_all_categories)) },
                    selected = currentFilter == null,
                    onClick = { onFilterSelected(null) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            items(availableCategories, key = { it }) { category ->
                val isCustom = category in customCategories
                val leadingIcon = if (isCustom) Icons.Default.LabelImportant else Icons.Default.Category
                NavigationDrawerItem(
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isCustom) {
                                IconButton(onClick = { onRemoveCustomCategory(category) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.dialog_delete_custom_category_message)
                                    )
                                }
                            }
                        }
                    },
                    selected = currentFilter == category,
                    onClick = { onFilterSelected(category) },
                    icon = { Icon(leadingIcon, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        HorizontalDivider()
     }
 }

@Composable
private fun CategoryAccordion(
    group: MemoGroup,
    isExpanded: Boolean,
    context: Context,
    isCustomCategory: Boolean,
    categoriesWithNew: Set<String>,
    newMemoIds: Set<Long>,
    onHeaderClick: () -> Unit,
    onDeleteCategory: () -> Unit,
    onDeleteMemo: (Memo) -> Unit,
    onEditCategory: (Memo) -> Unit,
    onReanalyzeMemo: (Memo) -> Unit,
    onMemoUsed: (Long) -> Unit = {},
    dragHandle: Modifier,
    uiDisplayMode: com.machi.memoiz.data.datastore.UiDisplayMode? = null,
    genAiTextAvailable: Boolean = false
) {
    val deleteCategoryString = stringResource(R.string.action_delete_category)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onHeaderClick() }
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                    Text(
                        text = group.category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (group.category in categoriesWithNew) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(color = Color(0xFFFFF59D), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "New!", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val deleteIcon = Icons.Default.Delete
                    val deleteContentDescription = if (isCustomCategory) {
                        stringResource(R.string.dialog_delete_custom_category_message)
                    } else {
                        deleteCategoryString
                    }
                    IconButton(onClick = onDeleteCategory) {
                        Icon(deleteIcon, deleteContentDescription)
                    }
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.cd_category_drag_handle),
                        modifier = dragHandle
                    )
                }
            }

            // Avoid using AnimatedVisibility here because LazyList prefetch/subcomposition can attempt to
            // apply changes to layout nodes that have been deactivated, causing crashes like:
            // "Apply is called on deactivated node ... deactivated: true". To prevent this, keep the
            // composable node active and animate its size instead of removing/adding child nodes.
            Column(modifier = Modifier.animateContentSize()) {
                if (isExpanded) {
                    HorizontalDivider()
                    group.memos.forEach { memo ->
                        MemoCard(
                            memo = memo,
                            onDelete = { onDeleteMemo(memo) },
                            onEditCategory = { onEditCategory(memo) },
                            onReanalyze = { onReanalyzeMemo(memo) },
                            onUsed = { id -> onMemoUsed(id) },
                            newMemoIds = newMemoIds,
                            appUiDisplayMode = uiDisplayMode,
                            genAiTextAvailable = genAiTextAvailable
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoCard(
    memo: Memo,
    onDelete: () -> Unit,
    onEditCategory: () -> Unit,
    onReanalyze: () -> Unit,
    readOnly: Boolean = false,
    onUsed: (Long) -> Unit = {},
    newMemoIds: Set<Long> = emptySet(),
    appUiDisplayMode: com.machi.memoiz.data.datastore.UiDisplayMode? = null,
    genAiTextAvailable: Boolean = false
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val reanalyzeString = stringResource(R.string.action_reanalyze)
    val openString = stringResource(R.string.action_open)
    val shareString = stringResource(R.string.action_share)
    val errorOpenImageString = stringResource(R.string.error_open_image)
    val errorOpenUrlString = stringResource(R.string.error_open_url)
    val copiedToast = stringResource(R.string.toast_copy_to_clipboard)
    val copyContentDescription = stringResource(R.string.cd_copy_memo)
    var menuExpanded by remember { mutableStateOf(false) }

    fun openImage() {
        val uri = memo.imageUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, errorOpenImageString, Toast.LENGTH_SHORT).show()
        }
    }

    fun openWebsite() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(memo.content))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, errorOpenUrlString, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, memo.content)
        }
        context.startActivity(Intent.createChooser(shareIntent, shareString))
    }

    val primaryAction = when (memo.memoType) {
        MemoType.IMAGE -> PrimaryAction(
            label = openString,
            icon = Icons.Default.OpenInNew,
            enabled = !readOnly && !memo.imageUri.isNullOrBlank(),
            onInvoke = { openImage() }
        )
        MemoType.WEB_SITE -> PrimaryAction(
            label = openString,
            icon = Icons.Default.OpenInNew,
            enabled = !readOnly,
            onInvoke = { openWebsite() }
        )
        else -> PrimaryAction(
            label = shareString,
            icon = Icons.Default.Share,
            enabled = !readOnly,
            onInvoke = { shareText() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
    ) {
        var summaryOverride by remember { mutableStateOf<String?>(null) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Ensure icon keeps its size and the label sits clearly below it.
                    MemoTypeIcon(memo.memoType)
                    if (memo.id in newMemoIds) {
                        Box(
                            modifier = Modifier
                                .background(color = Color(0xFFFFF59D), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "New!", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                        }
                    }
                }
                if (memo.subCategory != null) {
                    AssistChip(
                        onClick = { },
                        enabled = !readOnly,
                        label = { Text(memo.subCategory) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        when {
                            memo.memoType == MemoType.IMAGE && !memo.imageUri.isNullOrBlank() -> {
                                val uri = runCatching { Uri.parse(memo.imageUri) }.getOrNull()
                                uri?.let {
                                    val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newUri(context.contentResolver, "image", it)
                                    sysClipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                                }
                            }
                            memo.content.isNotBlank() -> {
                                clipboardManager.setText(AnnotatedString(memo.content))
                                Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                            }
                            !memo.summary.isNullOrBlank() -> {
                                clipboardManager.setText(AnnotatedString(memo.summary))
                                Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !readOnly
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = copyContentDescription)
                }

                if (primaryAction.enabled) {
                    IconButton(onClick = {
                        primaryAction.onInvoke()
                        onUsed(memo.id)
                        menuExpanded = false
                    }, enabled = primaryAction.enabled) {
                        Icon(primaryAction.icon, contentDescription = primaryAction.label)
                    }
                }

                IconButton(onClick = { menuExpanded = true }, enabled = !readOnly) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_open_memo_menu))
                }
            }

            Box {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dialog_edit_category_title)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        enabled = !readOnly,
                        onClick = {
                            menuExpanded = false
                            if (!readOnly) onEditCategory()
                        }
                    )
                    if (genAiTextAvailable) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fab_cat_comment_label)) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "🐱", fontSize = 14.sp)
                                }
                            },
                            enabled = !readOnly,
                            onClick = {
                                menuExpanded = false
                                try {
                                    val act = context as? android.app.Activity
                                    if (act != null) {
                                        CatCommentDialogActivity.start(act, memo.id)
                                    } else {
                                        CatCommentDialogActivity.start(context.applicationContext, memo.id)
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainScreen", "Failed to start CatCommentDialogActivity from menu: ${e.message}", e)
                                }
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(reanalyzeString) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        enabled = !readOnly,
                        onClick = {
                            menuExpanded = false
                            if (!readOnly) onReanalyze()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.memo_menu_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        enabled = !readOnly,
                        onClick = {
                            menuExpanded = false
                            if (!readOnly) onDelete()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!memo.imageUri.isNullOrBlank()) {
                if (memo.memoType == MemoType.IMAGE) {
                    ImageThumbnailFrame(
                        imageUri = memo.imageUri,
                        contentDescription = stringResource(R.string.cd_memo_image),
                        modifier = Modifier.size(140.dp)
                    )
                } else {
                    val imageModifier = Modifier.size(96.dp)
                    AsyncImage(
                        model = Uri.parse(memo.imageUri),
                        contentDescription = stringResource(R.string.cd_memo_image),
                        modifier = imageModifier.clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                when (memo.memoType) {
                    MemoType.TEXT -> {
                        val displayText = remember(memo.content) {
                            val maxChars = 240
                            if (memo.content.length <= maxChars) memo.content else memo.content.take(maxChars) + "\u2026"
                        }
                        CampusNoteTextAligned(
                            text = displayText,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    MemoType.WEB_SITE -> {
                        val displayText = remember(memo.content) {
                            val maxChars = 240
                            if (memo.content.length <= maxChars) memo.content else memo.content.take(maxChars) + "\u2026"
                        }
                        val (webTitle, webSummary) = remember(memo.summary) {
                            val raw = memo.summary
                            if (raw.isNullOrBlank()) null to null else {
                                val first = raw.substringBefore("\n").trim().takeIf { it.isNotBlank() }
                                val rest = raw.substringAfter("\n", "").trim().takeIf { it.isNotEmpty() }
                                first to (rest ?: raw)
                            }
                        }
                        ChromeStyleUrlBar(
                            url = displayText,
                            title = webTitle,
                            modifier = Modifier.fillMaxWidth(),
                            appUiDisplayMode = appUiDisplayMode
                        )
                        if (memo.content.length > displayText.length) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.action_share),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        summaryOverride = webSummary
                    }
                    MemoType.IMAGE -> {
                        // For image memos we intentionally do NOT render an inline prefixed Surface here.
                        // The AI-generated description for images should be shown only in the speech bubble below
                        // (with the robot emoji). Keeping this branch empty avoids duplicate descriptions.
                    }
                    else -> {
                        if (memo.content.isNotBlank()) {
                            val imageDescription = remember(memo.content) { "${AI_ROBOT_PREFIX} ${memo.content}" }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = imageDescription,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }

                val bubbleText = remember(memo.memoType, memo.content, summaryOverride, memo.summary) {
                    when (memo.memoType) {
                        MemoType.IMAGE -> memo.content
                        MemoType.WEB_SITE -> summaryOverride ?: memo.summary ?: memo.content
                        else -> null
                    }
                }

                if (!bubbleText.isNullOrBlank() && (memo.memoType == MemoType.IMAGE || memo.memoType == MemoType.WEB_SITE)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        SpeechBubble(
                            text = bubbleText,
                            modifier = Modifier.fillMaxWidth(),
                            maxWidthDp = 420.dp,
                            appUiDisplayMode = appUiDisplayMode,
                            startPadding = 24.dp,
                            showRobotEmoji = true
                        )
                    }
                }

                if (!memo.summary.isNullOrBlank() && memo.memoType != MemoType.IMAGE && bubbleText.isNullOrBlank()) {
                    val cleaned = remember(memo.summary) { cleanSummary(summaryOverride ?: memo.summary) }
                    val prefixedSummary = remember(cleaned, memo.memoType) {
                        val needsPrefix = memo.memoType == MemoType.IMAGE || memo.memoType == MemoType.WEB_SITE
                        if (needsPrefix && !cleaned.isNullOrBlank()) "${AI_ROBOT_PREFIX} $cleaned" else cleaned.orEmpty()
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AiSummaryBlock(prefixedSummary)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                memo.sourceApp?.let {
                    Text(
                        text = stringResource(R.string.label_source_app, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (memo.isCategoryLocked) {
                    Text(
                        text = stringResource(R.string.manual_category_indicator),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatTimestamp(memo.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun SortModeDialog(
    currentMode: SortMode,
    onModeSelected: (SortMode) -> Unit,
    onDismiss: () -> Unit,
    hasManualOrder: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_mode_label)) },
        text = {
            Column {
                SortMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onModeSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                SortMode.CREATED_DESC -> stringResource(R.string.sort_by_created)
                                SortMode.CATEGORY_NAME -> stringResource(R.string.sort_by_category_name)
                                SortMode.MOST_USED -> stringResource(R.string.sort_by_most_used)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun AddCustomCategoryDialog(
    existingCategories: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val errorEmpty = stringResource(R.string.error_category_name_empty)
    val errorTooLong = stringResource(R.string.error_category_name_too_long)
    val errorExists = stringResource(R.string.error_category_already_exists)

    fun handleSubmit(onValid: (String) -> Unit) {
        val validationError = when {
            categoryName.isBlank() -> errorEmpty
            categoryName.length > 50 -> errorTooLong
            categoryName.trim().lowercase() in existingCategories.map { it.lowercase() } -> errorExists
            else -> null
        }
        if (validationError != null) {
            error = validationError
        } else {
            onValid(categoryName.trim())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_category_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_add_category_message))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.dialog_category_name_label)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { handleSubmit(onConfirm) }) {
                Text(stringResource(R.string.dialog_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualCategoryDialog(
    availableCategories: List<String>,
    categoryValue: String,
    errorMessage: String?,
    onCategoryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_category_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_edit_category_message))
                Text(
                    text = stringResource(R.string.dialog_edit_category_auto_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = categoryValue,
                    onValueChange = {
                        onCategoryChange(it)
                        menuExpanded = false
                    },
                    label = { Text(stringResource(R.string.dialog_category_name_label)) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { menuExpanded = true }) {
                    Text(stringResource(R.string.dialog_choose_existing_category))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    availableCategories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onCategoryChange(option)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(categoryValue) }) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

private data class PrimaryAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onInvoke: () -> Unit
)

@Composable
private fun MemoTypeIcon(memoType: String?) {
    val (icon, tint) = when (memoType) {
        MemoType.TEXT -> Icons.Default.Notes to MaterialTheme.colorScheme.primary
        MemoType.WEB_SITE -> Icons.Default.Language to MaterialTheme.colorScheme.secondary
        MemoType.IMAGE -> Icons.Default.Image to MaterialTheme.colorScheme.tertiary
        else -> Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    maxWidthDp: Dp = 320.dp,
    appUiDisplayMode: com.machi.memoiz.data.datastore.UiDisplayMode? = null,
    startPadding: Dp = 0.dp,
    showRobotEmoji: Boolean = true
) {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val effectiveConfig = remember(configuration, appUiDisplayMode) {
        if (appUiDisplayMode == null || appUiDisplayMode == com.machi.memoiz.data.datastore.UiDisplayMode.SYSTEM) {
            configuration
        } else {
            val copy = android.content.res.Configuration(configuration)
            copy.uiMode = copy.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()
            copy.uiMode = when (appUiDisplayMode) {
                com.machi.memoiz.data.datastore.UiDisplayMode.DARK -> copy.uiMode or android.content.res.Configuration.UI_MODE_NIGHT_YES
                com.machi.memoiz.data.datastore.UiDisplayMode.LIGHT -> copy.uiMode or android.content.res.Configuration.UI_MODE_NIGHT_NO
                else -> copy.uiMode
            }
            copy
        }
    }
    val themedCtx = remember(effectiveConfig) { ctx.createConfigurationContext(effectiveConfig) }
    val bubbleColor = Color(themedCtx.resources.getColor(R.color.speech_bubble_bg, themedCtx.theme))

    val robotXOffset = startPadding - 20.dp
    val robotYOffset = 30.dp
    val tailXOffset = startPadding - 4.dp
    val tailYOffset = 12.dp

    Box(modifier = modifier.padding(bottom = robotYOffset)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mplus1CodeReguar, fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Canvas(
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomStart)
                .offset(x = tailXOffset, y = tailYOffset)
        ) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(p, color = bubbleColor)
        }

        if (showRobotEmoji) {
            Text(
                text = "🤖",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = robotXOffset, y = robotYOffset)
            )
        }
    }
}

@Composable
private fun AnalyzingIndicator() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val infiniteTransition = rememberInfiniteTransition(label = "analyzing")
        val sweep by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "analyzing_rotation"
        )

        val hue by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "analyzing_hue"
        )

        val alphaPulse by infiniteTransition.animateFloat(
            initialValue = 0.78f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "analyzing_alpha"
        )

        val baseColorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.9f, 0.95f))
        val animatedColor = Color(baseColorInt).copy(alpha = alphaPulse)

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = sweep },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = animatedColor
            )
            Image(
                painter = painterResource(id = R.drawable.analyzing),
                contentDescription = stringResource(R.string.cd_analyzing_indicator),
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = stringResource(R.string.label_analyzing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

fun formatTimestamp(timestamp: Long): String {
    val locale = Locale.getDefault()
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    return formatter.format(Date(timestamp))
}

fun cleanSummary(raw: String?): String {
    raw ?: return ""
    val trimmed = raw.trimStart()
    val prefixes = listOf("* ", "- ", "• ")
    val matching = prefixes.firstOrNull { trimmed.startsWith(it) }
    return matching?.let { trimmed.removePrefix(it) } ?: raw
}

@Composable
private fun AiSummaryBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

const val AI_ROBOT_PREFIX = "\uD83E\uDD16"

@Composable
private fun ImageThumbnailFrame(
    imageUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val outerShape = RoundedCornerShape(10.dp)
    val innerShape = RoundedCornerShape(8.dp)
    val context = LocalContext.current

    val uri = runCatching { Uri.parse(imageUri) }.getOrNull()
    val authority = uri?.authority ?: ""
    val scheme = uri?.scheme ?: ""
    val appFileProviderAuthority = "${context.packageName}.fileprovider"
    val hasLocalCopy = when {
        scheme == ContentResolver.SCHEME_FILE -> true
        scheme == ContentResolver.SCHEME_CONTENT && authority == appFileProviderAuthority -> true
        else -> false
    }

    val pinColor = if (hasLocalCopy) MaterialTheme.colorScheme.error else Color(0xFF1976D2)

    Box(
        modifier = modifier
            .graphicsLayer(rotationZ = 2.5f)
            .shadow(
                elevation = 10.dp,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = Color.Black.copy(alpha = 0.18f),
                shape = outerShape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = outerShape)
                .padding(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7F7F7), shape = innerShape)
                    .padding(6.dp)
            ) {
                AsyncImage(
                    model = Uri.parse(imageUri),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        PinThumbtack(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 4.dp),
            pinColor = pinColor
        )
    }
}

@Composable
private fun PinThumbtack(modifier: Modifier = Modifier, pinColor: Color = Color(0xFF2E7D32)) {
    Canvas(
        modifier = modifier
            .size(width = 11.dp, height = 17.dp)
            .graphicsLayer(rotationX = 26f, rotationZ = -18f)
    ) {
        val shadowColor = Color.Black.copy(alpha = 0.26f)
        val centerX = size.width / 2f
        val headRadius = size.width * 0.34f
        val needleWidth = size.width * 0.18f
        val needleHeight = size.height * 0.62f
        val needleTopY = headRadius * 1.25f

        drawRoundRect(
            color = shadowColor,
            topLeft = Offset(centerX - needleWidth * 1.1f, needleTopY + 2.2f),
            size = Size(needleWidth * 2.2f, needleHeight + headRadius * 1.3f),
            cornerRadius = CornerRadius(needleWidth * 1.2f),
            alpha = 0.25f
        )

        drawRoundRect(
            color = shadowColor,
            topLeft = Offset(centerX - needleWidth / 2f + 1.0f, needleTopY + 1.6f),
            size = Size(needleWidth, needleHeight),
            cornerRadius = CornerRadius(needleWidth)
        )

        drawRoundRect(
            color = Color(0xFF7A5D48),
            topLeft = Offset(centerX - needleWidth / 2f, needleTopY),
            size = Size(needleWidth, needleHeight),
            cornerRadius = CornerRadius(needleWidth)
        )

        drawCircle(
            color = pinColor.copy(alpha = 0.25f),
            radius = headRadius * 1.08f,
            center = Offset(centerX - headRadius * 0.16f, headRadius * 0.9f)
        )

        drawCircle(
            color = pinColor,
            radius = headRadius,
            center = Offset(centerX, headRadius)
        )
    }
}
