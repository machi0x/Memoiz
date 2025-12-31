package com.machi.memoiz.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.machi.memoiz.R
import com.machi.memoiz.data.entity.MemoType
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.ui.theme.MemoizTheme
import com.machi.memoiz.util.FailureCategoryHelper
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showSortDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Any?>(null) }
    var deleteTargetIsCustomCategory by remember { mutableStateOf(false) }
    var pendingReanalyzeMemo by remember { mutableStateOf<Memo?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        listState = lazyListState,
        onMove = { from, to ->
            viewModel.onCategoryMoved(from.index, to.index)
        }
    )

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
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                modifier = Modifier.fillMaxWidth(),
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
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                                        }
                                    }
                                }
                            )

                            val typeLabel = when (memoTypeFilter) {
                                MemoType.TEXT -> stringResource(R.string.memo_type_text)
                                MemoType.WEB_SITE -> stringResource(R.string.memo_type_web_site)
                                MemoType.IMAGE -> stringResource(R.string.memo_type_image)
                                else -> null
                            }
                            val filterNote = when {
                                typeLabel != null && categoryFilter != null -> stringResource(
                                    R.string.filter_note_type_and_category,
                                    typeLabel,
                                    categoryFilter
                                )
                                typeLabel != null -> stringResource(R.string.filter_note_type_only, typeLabel)
                                categoryFilter != null -> stringResource(R.string.filter_note_category_only, categoryFilter)
                                else -> null
                            }
                            if (filterNote != null) {
                                Text(
                                    text = filterNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
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
                                when (sortMode) {
                                    SortMode.CREATED_DESC -> Icons.Default.DateRange
                                    SortMode.CATEGORY_NAME -> Icons.Default.SortByAlpha
                                },
                                stringResource(R.string.cd_sort)
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(R.string.fab_paste_clipboard)) },
                    icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    onClick = {
                        val enqueued = ContentProcessingLauncher.enqueueFromClipboard(context)
                        if (!enqueued) {
                            Toast.makeText(context, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        ) { padding ->
            if (memoGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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
                        .padding(padding)
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
                                onReanalyzeMemo = { memo ->
                                    pendingReanalyzeMemo = memo
                                },
                                onReanalyzeCategory = {
                                    pendingReanalyzeMemo = null
                                    viewModel.reanalyzeFailureBatch(context)
                                },
                                dragHandle = Modifier.detectReorder(reorderState)
                            )
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
            onDismiss = { showSortDialog = false }
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
    onReanalyzeMemo: (Memo) -> Unit,
    onReanalyzeCategory: () -> Unit,
    dragHandle: Modifier
) {
    val reanalyzeFailuresString = stringResource(R.string.action_reanalyze_failures)
    val deleteCategoryString = stringResource(R.string.action_delete_category)
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Category header (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
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
    onReanalyze: () -> Unit
) {
    val context = LocalContext.current

    // Hoist string resources to the composable context
    val reanalyzeString = stringResource(R.string.action_reanalyze)
    val openString = stringResource(R.string.action_open)
    val shareString = stringResource(R.string.action_share)
    val deleteString = stringResource(R.string.action_delete)
    val errorOpenImageString = stringResource(R.string.error_open_image)
    val errorOpenUrlString = stringResource(R.string.error_open_url)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Memo Type Badge and SubCategory Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Memo type badge
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            when (memo.memoType) {
                                MemoType.TEXT -> stringResource(R.string.memo_type_text)
                                MemoType.WEB_SITE -> stringResource(R.string.memo_type_web_site)
                                MemoType.IMAGE -> stringResource(R.string.memo_type_image)
                                else -> stringResource(R.string.memo_type_text)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            when (memo.memoType) {
                                MemoType.TEXT -> Icons.Default.Notes
                                MemoType.WEB_SITE -> Icons.Default.Language
                                MemoType.IMAGE -> Icons.Default.Image
                                else -> Icons.Default.Notes
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )

                // SubCategory chip if exists
                if (memo.subCategory != null) {
                    AssistChip(
                        onClick = { },
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onReanalyze) {
                    Icon(Icons.Default.Refresh, reanalyzeString)
                }
                // Action buttons based on memo type
                when (memo.memoType) {
                    MemoType.IMAGE -> {
                        // Image memo - open image
                        if (!memo.imageUri.isNullOrBlank()) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(memo.imageUri), "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, errorOpenImageString, Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.OpenInNew, stringResource(R.string.action_open))
                            }
                        }
                    }
                    MemoType.WEB_SITE -> {
                        // URL memo - open URL
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(memo.content))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, errorOpenUrlString, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, openString)
                        }
                    }
                    else -> {
                        // Text memo - share
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, memo.content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, shareString))
                        }) {
                            Icon(Icons.Default.Share, shareString)
                        }
                    }
                }

                IconButton(onClick = { onDelete() }) {
                    Icon(Icons.Default.Delete, deleteString)
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
                AsyncImage(
                    model = Uri.parse(memo.imageUri),
                    contentDescription = stringResource(R.string.cd_memo_image),
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Content column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (memo.content.isNotBlank()) {
                    Text(
                        text = memo.content,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (memo.summary != null && (memo.memoType != MemoType.IMAGE || memo.summary != memo.content)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = memo.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (memo.sourceApp != null) {
                Text(
                    text = stringResource(R.string.label_source_app, memo.sourceApp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    onDismiss: () -> Unit
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
                            .clickable { onModeSelected(mode) }
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

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleMemos = listOf(
        Memo(id = 1, content = "Buy groceries", memoType = MemoType.TEXT, category = "Personal", subCategory = "To-Do", createdAt = System.currentTimeMillis()),
        Memo(id = 2, content = "Prepare slides for meeting", memoType = MemoType.TEXT, category = "Work", subCategory = "Presentation", summary = "Finish the slides for the Q3 financial review.", createdAt = System.currentTimeMillis()),
        Memo(id = 3, content = "App idea: smart notebook", memoType = MemoType.TEXT, category = "Ideas", subCategory = "Mobile App", createdAt = System.currentTimeMillis())
    )
    val sampleMemoGroups = sampleMemos.groupBy { it.category }.map { (category, memos) -> MemoGroup(category, memos) }

    MemoizTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sampleMemoGroups) { group ->
                Column {
                    Text(
                        text = group.category,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    group.memos.forEach { memo ->
                        MemoCard(
                            memo = memo,
                            onDelete = { },
                            onReanalyze = { }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
