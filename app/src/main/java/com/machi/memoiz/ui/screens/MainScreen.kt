package com.machi.memoiz.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.machi.memoiz.R
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.ui.theme.MemoizTheme
import com.machi.memoiz.util.FailureCategoryHelper
import kotlinx.coroutines.launch
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
    val availableCategories by viewModel.availableCategories.collectAsState()
    val customCategories by viewModel.customCategories.collectAsState()
    val expandedCategories by viewModel.expandedCategories.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var toastMessage by remember { mutableStateOf<String?>(null) }
    toastMessage?.let {
        LaunchedEffect(it) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scheduleDailyFailureReanalyze(context)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showSortDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerContent(
                    currentFilter = categoryFilter,
                    availableCategories = availableCategories,
                    customCategories = customCategories,
                    onFilterSelected = { filter ->
                        viewModel.setCategoryFilter(filter)
                        scope.launch { drawerState.close() }
                    },
                    onAddCategoryClick = {
                        showAddCategoryDialog = true
                    },
                    onSettingsClick = {
                        onNavigateToSettings()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
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
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Open drawer")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                when (sortMode) {
                                    SortMode.CREATED_DESC -> Icons.Default.DateRange
                                    SortMode.CATEGORY_NAME -> Icons.Default.SortByAlpha
                                },
                                "Sort"
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = context.getString(R.string.fab_paste_clipboard)) },
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
                    Text(
                        text = if (searchQuery.isNotEmpty() || categoryFilter != null) {
                            stringResource(R.string.no_matching_memos_found)
                        } else {
                            context.getString(R.string.empty_state_message)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(memoGroups) { group ->
                        CategoryAccordion(
                            group = group,
                            isExpanded = group.category in expandedCategories,
                            onHeaderClick = { viewModel.toggleCategoryExpanded(group.category) },
                            onDeleteCategory = { viewModel.deleteCategory(group.category) },
                            onDeleteMemo = { memo -> viewModel.deleteMemo(memo) },
                            onReanalyzeMemo = { memo ->
                                viewModel.reanalyzeMemo(context, memo.id)
                                toastMessage = context.getString(R.string.toast_reanalyze_enqueued)
                            },
                            onReanalyzeCategory = {
                                viewModel.reanalyzeFailureBatch(context)
                                toastMessage = context.getString(R.string.toast_reanalyze_enqueued)
                            }
                        )
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
            onDismiss = { showAddCategoryDialog = false }
        )
    }
}

@Composable
private fun NavigationDrawerContent(
    currentFilter: String?,
    availableCategories: List<String>,
    customCategories: Set<String>,
    onFilterSelected: (String?) -> Unit,
    onAddCategoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
    ) {
        // App name
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Banner image
        Image(
            painter = painterResource(id = R.drawable.top_banner),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentScale = ContentScale.Crop
        )

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
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

            // Categories
            items(availableCategories) { category ->
                NavigationDrawerItem(
                    label = { 
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(category)
                            if (category in customCategories) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Custom",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    selected = currentFilter == category,
                    onClick = { onFilterSelected(category) },
                    icon = { Icon(Icons.Default.Category, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Add custom category button
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

        // Settings at bottom
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
    onHeaderClick: () -> Unit,
    onDeleteCategory: () -> Unit,
    onDeleteMemo: (Memo) -> Unit,
    onReanalyzeMemo: (Memo) -> Unit,
    onReanalyzeCategory: () -> Unit
) {
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
                        text = "(${group.memos.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (FailureCategoryHelper.isFailureLabel(context, group.category)) {
                        IconButton(onClick = onReanalyzeCategory) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.action_reanalyze_failures))
                        }
                    }
                    IconButton(onClick = onDeleteCategory) {
                        Icon(Icons.Default.Delete, "Delete Category")
                    }
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            } else {
                Spacer(modifier = Modifier.height(28.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onReanalyze) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.action_reanalyze))
                }
                // Action buttons based on content type
                when {
                    // Image memo - open image
                    !memo.imageUri.isNullOrBlank() -> {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(memo.imageUri), "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open image", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, stringResource(R.string.action_open))
                        }
                    }
                    // URL memo - open URL
                    memo.content.startsWith("http://", ignoreCase = true) ||
                    memo.content.startsWith("https://", ignoreCase = true) -> {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(memo.content))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, stringResource(R.string.action_open))
                        }
                    }
                    // Text memo - share
                    else -> {
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, memo.content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
                        }) {
                            Icon(Icons.Default.Share, stringResource(R.string.action_share))
                        }
                    }
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Delete")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = memo.content,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )

        if (memo.summary != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = memo.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (memo.sourceApp != null) {
                Text(
                    text = "From: ${memo.sourceApp}",
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Memo") },
            text = { Text("Are you sure you want to delete this memo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
    onDismiss: () -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

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
            TextButton(
                onClick = {
                    when {
                        categoryName.isBlank() -> 
                            error = context.getString(R.string.error_category_name_empty)
                        categoryName.length > 50 -> 
                            error = context.getString(R.string.error_category_name_too_long)
                        categoryName.trim() in existingCategories ->
                            error = context.getString(R.string.error_category_already_exists)
                        else -> onConfirm(categoryName.trim())
                    }
                }
            ) {
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

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleMemos = listOf(
        Memo(id = 1, content = "Buy groceries", category = "Personal", subCategory = "To-Do", createdAt = System.currentTimeMillis()),
        Memo(id = 2, content = "Prepare slides for meeting", category = "Work", subCategory = "Presentation", summary = "Finish the slides for the Q3 financial review.", createdAt = System.currentTimeMillis()),
        Memo(id = 3, content = "App idea: smart notebook", category = "Ideas", subCategory = "Mobile App", createdAt = System.currentTimeMillis())
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
                        MemoCard(memo = memo, onDelete = { })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
