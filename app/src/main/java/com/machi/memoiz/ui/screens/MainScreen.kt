package com.machi.memoiz.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import kotlin.random.Random
import com.machi.memoiz.R
import com.machi.memoiz.data.entity.MemoType
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.ui.theme.MemoizTheme
import com.machi.memoiz.util.FailureCategoryHelper
import com.machi.memoiz.util.UsageStatsHelper
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

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
    val isProcessing by viewModel.isProcessing.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTutorialDialog by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        viewModel.scheduleDailyFailureReanalyze(context)
    }

    LaunchedEffect(shouldShowTutorial) {
        if (shouldShowTutorial) {
            showTutorialDialog = true
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

    val clearManualCategoryState: () -> Unit = {
        manualCategoryMemo = null
        manualCategoryInput = ""
        manualCategoryError = null
    }

    val handleManualCategorySave: (String) -> Unit = save@{ rawInput ->
        val trimmedCategory = rawInput.trim()
        if (trimmedCategory.isEmpty()) {
            manualCategoryError = context.getString(R.string.error_category_name_empty)
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
            val enqueued = ContentProcessingLauncher.enqueueWork(context, null, uri, forceCopyImage = true)
            if (!enqueued) {
                Toast.makeText(context, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
            }
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
                    }
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
                                        .weight(if (isProcessing) 0.85f else 1f)
                                        .padding(end = if (isProcessing) 8.dp else 0.dp),
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
                                        val enqueued = ContentProcessingLauncher.enqueueFromClipboard(context)
                                        if (!enqueued) {
                                            Toast.makeText(context, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
                                        }
                                        isFabExpanded = false
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
                                textAlign = TextAlign.Center
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
                                    onReanalyzeCategory = {
                                        pendingReanalyzeMemo = null
                                        viewModel.reanalyzeFailureBatch(context)
                                        isFabExpanded = false
                                    },
                                    dragHandle = Modifier.detectReorder(reorderState)
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
            onConfirmWithReanalyze = { categoryName ->
                viewModel.addCustomCategoryWithMerge(context, categoryName)
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
                    text = if (isCategory) {
                        stringResource(R.string.dialog_delete_category_title)
                    } else {
                        stringResource(R.string.dialog_delete_memo_title)
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
                    if (isCategory) {
                        val warningRes = if (deleteTargetIsCustomCategory) {
                            R.string.dialog_delete_custom_category_warning
                        } else {
                            R.string.dialog_delete_category_warning
                        }
                        Text(
                            text = stringResource(warningRes),
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
                                    viewModel.removeCustomCategoryAndReanalyze(context, target)
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

    if (showTutorialDialog) {
        TutorialDialog(
            onFinished = {
                showTutorialDialog = false
                viewModel.markTutorialSeen()
            }
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
                        createMemoError = context.getString(R.string.dialog_create_memo_error_empty)
                        return@TextButton
                    }
                    val enqueued = ContentProcessingLauncher.enqueueManualMemo(context, trimmed)
                    if (!enqueued) {
                        Toast.makeText(context, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
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
    onRemoveCustomCategory: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
    ) {
        val bannerPainter = painterResource(id = R.drawable.top_banner)
        val bannerHeight = 120.dp
        Image(
            painter = bannerPainter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Filter by Type section header
            item {
                Text(
                    text = stringResource(R.string.drawer_filter_by_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // All types option
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_all_categories)) },
                    selected = memoTypeFilter == null,
                    onClick = { onMemoTypeFilterSelected(null) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Memo type filters
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Filter by Category section header
            item {
                Text(
                    text = stringResource(R.string.drawer_filter_by_category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // All categories option
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

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_add_category)) },
                    selected = false,
                    onClick = onAddCategoryClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        HorizontalDivider()

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_settings)) },
            selected = false,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun CategoryAccordion(
    group: MemoGroup,
    isExpanded: Boolean,
    context: Context,
    isCustomCategory: Boolean,
    onHeaderClick: () -> Unit,
    onDeleteCategory: () -> Unit,
    onDeleteMemo: (Memo) -> Unit,
    onEditCategory: (Memo) -> Unit,
    onReanalyzeMemo: (Memo) -> Unit,
    onReanalyzeCategory: () -> Unit,
    dragHandle: Modifier
) {
    val reanalyzeFailuresString = stringResource(R.string.action_reanalyze_failures)
    val deleteCategoryString = stringResource(R.string.action_delete_category)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.memo_card_background))
    ) {
        Column {
            // Category header (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current
                    ) { onHeaderClick() }
                    .padding(16.dp),
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
                    Text(
                        text = stringResource(R.string.category_memo_count, group.memos.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    if (FailureCategoryHelper.isFailureLabel(context, group.category)) {
                        IconButton(onClick = onReanalyzeCategory) {
                            Icon(Icons.Default.Refresh, reanalyzeFailuresString)
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.cd_category_drag_handle),
                        modifier = dragHandle
                    )
                }
            }

            // Memos (expandable)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider()
                    group.memos.forEach { memo ->
                        MemoCard(
                            memo = memo,
                            onDelete = { onDeleteMemo(memo) },
                            onEditCategory = { onEditCategory(memo) },
                            onReanalyze = { onReanalyzeMemo(memo) }
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
    readOnly: Boolean = false
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
            .padding(16.dp)
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
                MemoTypeIcon(memo.memoType)
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
                        val textToCopy = when {
                            memo.memoType == MemoType.IMAGE && !memo.imageUri.isNullOrBlank() -> memo.imageUri
                            memo.content.isNotBlank() -> memo.content
                            !memo.summary.isNullOrBlank() -> memo.summary
                            else -> null
                        }
                        textToCopy?.let {
                            clipboardManager.setText(AnnotatedString(it))
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !readOnly
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = copyContentDescription)
                }

                if (primaryAction.enabled) {
                    IconButton(onClick = {
                        primaryAction.onInvoke()
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

        // Image thumbnail and content
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Show image thumbnail if available
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

            // Content column
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
                         if (memo.content.length > displayText.length) {
                             Spacer(modifier = Modifier.height(4.dp))
                             Text(
                                 text = stringResource(R.string.action_share),
                                 style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                         }
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
                            modifier = Modifier.fillMaxWidth()
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

                if (!memo.summary.isNullOrBlank() && memo.memoType != MemoType.IMAGE) {
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
                                interactionSource = remember { MutableInteractionSource() },
                                indication = LocalIndication.current
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
    onConfirmWithReanalyze: (String) -> Unit,
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { handleSubmit(onConfirm) }) {
                    Text(stringResource(R.string.dialog_add))
                }
                TextButton(onClick = { handleSubmit(onConfirmWithReanalyze) }) {
                    Text(stringResource(R.string.dialog_add_and_remerge))
                }
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

private data class TutorialStep(
    @DrawableRes val imageRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
)

@Composable
private fun TutorialDialog(
    onFinished: () -> Unit,
    initialStep: Int = 0
) {
    val context = LocalContext.current
    val steps = listOf(
        TutorialStep(R.drawable.top_banner, R.string.tutorial_step_overview_title, R.string.tutorial_step_overview_body),
        TutorialStep(R.drawable.share_from_browser, R.string.tutorial_step_share_browser_title, R.string.tutorial_step_share_browser_body),
        TutorialStep(R.drawable.share_from_select, R.string.tutorial_step_share_select_title, R.string.tutorial_step_share_select_body),
        TutorialStep(R.drawable.floating_buttons, R.string.tutorial_step_fab_title, R.string.tutorial_step_fab_body),
        TutorialStep(R.drawable.my_category, R.string.tutorial_step_my_category_title, R.string.tutorial_step_my_category_body),
        TutorialStep(R.drawable.app_usages, R.string.tutorial_step_usage_permission_title, R.string.tutorial_step_usage_permission_body),
        TutorialStep(R.drawable.main_ui, R.string.tutorial_step_main_ui_title, R.string.tutorial_step_main_ui_body),
        TutorialStep(R.drawable.side_panel, R.string.tutorial_step_side_panel_title, R.string.tutorial_step_side_panel_body)
    )

    var currentStep by rememberSaveable { mutableStateOf(initialStep.coerceIn(0, steps.lastIndex)) }
    val isLastStep = currentStep == steps.lastIndex
    val step = steps[currentStep]
    val stepTitle = stringResource(step.titleRes)
    val stepDescription = stringResource(step.descriptionRes)
    val tutorialCoroutineScope = rememberCoroutineScope()
    var usagePermissionJob by remember { mutableStateOf<Job?>(null) }
    val usagePermissionStepIndex = steps.indexOfFirst { it.imageRes == R.drawable.app_usages }
    val isUsagePermissionStep = currentStep == usagePermissionStepIndex && usagePermissionStepIndex >= 0
    var usagePermissionGranted by rememberSaveable { mutableStateOf(UsageStatsHelper(context).hasUsageStatsPermission()) }
    LaunchedEffect(isUsagePermissionStep) {
        if (isUsagePermissionStep) {
            usagePermissionGranted = UsageStatsHelper(context).hasUsageStatsPermission()
        }
    }

    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.98f)
            .widthIn(min = 600.dp, max = 760.dp)
            .height(720.dp),
        onDismissRequest = onFinished,
        title = { Text(stringResource(R.string.tutorial_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val imageScrollState = rememberScrollState()
                var viewportHeightPx by remember { mutableStateOf(1f) }
                val isLargeShot = step.imageRes == R.drawable.main_ui || step.imageRes == R.drawable.side_panel || step.imageRes == R.drawable.app_usages
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isLargeShot) 0.82f else 0.45f, fill = true)
                        .heightIn(max = if (isLargeShot) 500.dp else 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(imageScrollState)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = step.imageRes),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { base -> if (isLargeShot) base else base.heightIn(max = 220.dp) }
                                .wrapContentHeight(),
                            contentScale = if (isLargeShot) ContentScale.FillWidth else ContentScale.Fit
                        )
                    }
                    if (imageScrollState.maxValue > 0) {
                        val contentHeightPx = viewportHeightPx + imageScrollState.maxValue.toFloat()
                        val thumbHeightFraction = (viewportHeightPx / contentHeightPx).coerceIn(0.1f, 1f)
                        val thumbOffsetFraction = (imageScrollState.value.toFloat() / imageScrollState.maxValue.toFloat()).coerceIn(0f, 1f)
                        val thumbHeight = thumbHeightFraction * viewportHeightPx
                        val thumbOffset = thumbOffsetFraction * (viewportHeightPx - thumbHeight)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                                .align(Alignment.CenterEnd)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(with(LocalDensity.current) { thumbHeight.toDp() })
                                    .offset(y = with(LocalDensity.current) { thumbOffset.toDp() })
                                    .align(Alignment.TopEnd)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            )
                        }
                    }
                }
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                val usageDescriptionForGranted = when {
                    isUsagePermissionStep && usagePermissionGranted -> {
                        // Only keep header part when permission is already granted
                        stringResource(R.string.tutorial_step_usage_permission_body_header)
                    }
                    isUsagePermissionStep -> {
                        // Show full text (header + hint) when not granted
                        stringResource(R.string.tutorial_step_usage_permission_body)
                    }
                    else -> stepDescription
                }
                val descriptionToShow = if (usageDescriptionForGranted.isNotBlank()) usageDescriptionForGranted else null
                descriptionToShow?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                 val activeIndicatorSize = 10.dp
                 val inactiveIndicatorSize = 8.dp
                 Row(
                     modifier = Modifier.fillMaxWidth(),
                     horizontalArrangement = Arrangement.Center
                 ) {
                     steps.forEachIndexed { index, _ ->
                         val color = if (index == currentStep) {
                             MaterialTheme.colorScheme.primary
                         } else {
                             MaterialTheme.colorScheme.outline
                         }
                         Box(
                             modifier = Modifier
                                 .padding(horizontal = 4.dp)
                                 .size(if (index == currentStep) activeIndicatorSize else inactiveIndicatorSize)
                                 .clip(CircleShape)
                                 .background(color)
                         )
                     }
                 }
                if (isUsagePermissionStep && !usagePermissionGranted) {
                     TextButton(onClick = {
                         usagePermissionJob?.cancel()
                         launchUsageAccessSettings(context)
                         usagePermissionJob = tutorialCoroutineScope.launch {
                             val helper = UsageStatsHelper(context)
                             val deadline = System.currentTimeMillis() + 60_000
                             while (System.currentTimeMillis() < deadline && !helper.hasUsageStatsPermission()) {
                                 delay(2_000)
                             }
                             if (helper.hasUsageStatsPermission()) {
                                 usagePermissionGranted = true
                                 currentStep = (currentStep + 1).coerceAtMost(steps.lastIndex)
                             }
                         }
                     }) {
                         Text(stringResource(R.string.tutorial_usage_permission_button))
                     }
                 }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isLastStep) {
                    onFinished()
                } else {
                    currentStep++
                }
            }) {
                Text(
                    if (isLastStep) stringResource(R.string.tutorial_done) else stringResource(R.string.tutorial_next)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (currentStep > 0) {
                    currentStep--
                } else {
                    onFinished()
                }
            }) {
                Text(
                    if (currentStep > 0) stringResource(R.string.tutorial_back) else stringResource(R.string.tutorial_skip)
                )
            }
        }
    )
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
        Box(
            modifier = Modifier
                .size(48.dp)
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
                color = MaterialTheme.colorScheme.primary
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

private fun formatTimestamp(timestamp: Long): String {
    val locale = Locale.getDefault()
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    return formatter.format(Date(timestamp))
}

private fun launchUsageAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun cleanSummary(raw: String?): String {
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

private const val AI_ROBOT_PREFIX = "🤖"

@Composable
private fun ChromeStyleUrlBar(
    url: String,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        val hasTitle = !title.isNullOrBlank()
        if (hasTitle) {
            // ...existing two-column layout with title on top, URL below...
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "URL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title!!,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF202124),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF202124),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp),
                    tint = Color(0xFFFFD700)
                )
            }
        } else {
            // Fallback: single-line layout with label and URL in one row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "URL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF202124),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp),
                    tint = Color(0xFFFFD700)
                )
            }
        }
    }
}

@Composable
private fun ImageThumbnailFrame(
    imageUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val outerShape = RoundedCornerShape(10.dp)
    val innerShape = RoundedCornerShape(8.dp)
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
                .background(colorResource(id = R.color.memo_card_background), shape = outerShape)
                .padding(18.dp) // wider outer frame for more clearance
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
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        PinThumbtack(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 4.dp) // move pin down further
        )
    }
}

@Composable
private fun PinThumbtack(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(width = 11.dp, height = 17.dp) // slightly smaller pin
            .graphicsLayer(rotationX = 26f, rotationZ = -18f)
    ) {
        val pinColor = Color(0xFF2E7D32)
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

@Preview(name = "Text Frame", showBackground = true)
@Composable
private fun PreviewCampusNoteFrame() {
    MemoizTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            CampusNoteTextAligned(
                text = "買い忘れ防止メモ\n・牛乳\n・卵\n・朝食用のパン",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "Web URL Frame", showBackground = true)
@Composable
private fun PreviewChromeStyleUrlBar() {
    MemoizTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            ChromeStyleUrlBar(
                url = "https://news.example.com/articles/awesome-updates",
                title = "Awesome Updates - Example News",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "Image Frame", showBackground = true)
@Composable
private fun PreviewPinnedPhotoFrame() {
    MemoizTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            ImageThumbnailFrame(
                imageUri = "https://picsum.photos/seed/memoizPreview/600",
                contentDescription = null,
                modifier = Modifier.size(160.dp)
            )
        }
    }
}

@Preview(name = "Tutorial Main UI - Pixel 9", widthDp = 411, heightDp = 915, showBackground = true)
@Composable
private fun PreviewTutorialMainUiPixel9() {
    MemoizTheme {
        Surface {
            val start = listOf(
                R.drawable.main_ui,
                R.drawable.side_panel
            )
            TutorialDialog(
                onFinished = {},
                initialStep = stepsIndexForPreview(start, R.drawable.main_ui)
            )
        }
    }
}

@Preview(name = "Tutorial Main UI - Nexus 5", device = Devices.NEXUS_5, showBackground = true)
@Composable
private fun PreviewTutorialMainUiNexus5() {
    MemoizTheme {
        Surface {
            TutorialDialog(
                onFinished = {},
                initialStep = stepsIndexForPreview(emptyList(), R.drawable.main_ui)
            )
        }
    }
}

@Composable
private fun stepsIndexForPreview(priority: List<Int>, target: Int): Int {
    val steps = listOf(
        R.drawable.top_banner,
        R.drawable.share_from_browser,
        R.drawable.share_from_select,
        R.drawable.floating_buttons,
        R.drawable.my_category,
        R.drawable.app_usages,
        R.drawable.main_ui,
        R.drawable.side_panel
    )
    priority.forEach { preferred ->
        val idx = steps.indexOf(preferred)
        if (idx >= 0) return idx
    }
    return steps.indexOf(target).coerceAtLeast(0)
}

@Composable
private fun CampusNoteTextAligned(
    text: String,
    modifier: Modifier = Modifier,
    lineHeight: androidx.compose.ui.unit.TextUnit = 20.sp // smaller line spacing
) {
    val density = LocalDensity.current
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = lineHeight),
        color = Color(0xFF111111),
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .drawBehind {
                drawRect(Color(0xFFF9FAFB))
                val marginX = 28.dp.toPx()
                drawLine(
                    color = Color(0xFFFF9AA2),
                    start = Offset(marginX, 0f),
                    end = Offset(marginX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                val lineOffset = 2.dp.toPx() // nudge lines upward for better baseline alignment
                var y = lineHeightPx - lineOffset
                while (y < size.height) {
                    drawLine(
                        color = Color(0xFFB7D7FF),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += lineHeightPx
                }
            }
            .padding(start = 44.dp, top = 2.dp, end = 16.dp, bottom = 16.dp))
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
