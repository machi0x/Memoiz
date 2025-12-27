package com.machi.memoiz.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.machi.memoiz.R
import com.machi.memoiz.domain.model.Category
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.ui.theme.MemoizTheme
import com.machi.memoiz.worker.ReanalyzeFailedMemosWorker
import com.machi.memoiz.worker.ReanalyzeSingleMemoWorker
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main screen showing memos organized by categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val filteredMemos by viewModel.filteredMemos.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val context = LocalContext.current

    // Find Failure category id if exists (check canonical or localized labels)
    val failureLabel = context.getString(R.string.failure_category)
    val failureCategory = remember(categories, failureLabel) {
        categories.find { it.name == "FAILURE" || it.name == failureLabel || it.nameEn == failureLabel || it.nameJa == failureLabel }
    }
    val failureCategoryId = failureCategory?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memoiz") },
                actions = {
                    // If Failure category selected, show batch reanalyze button
                    if (selectedCategoryId != null && failureCategoryId != null && selectedCategoryId == failureCategoryId) {
                        IconButton(onClick = {
                            // Enqueue ReanalyzeFailedMemosWorker
                            val workRequest = OneTimeWorkRequestBuilder<ReanalyzeFailedMemosWorker>().build()
                            WorkManager.getInstance(context).enqueue(workRequest)
                        }) {
                            Icon(Icons.Default.Autorenew, "Reanalyze All")
                        }
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
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
                FloatingActionButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_to_memoiz)))
                    }
                ) {
                    Icon(Icons.Default.Share, "Share to Memoiz")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category filter chips
            CategoryFilterRow(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = { viewModel.selectCategory(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )

            HorizontalDivider()

            // Memos list
            if (filteredMemos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.empty_state_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                MemosList(
                    memos = filteredMemos,
                    categories = categories,
                    failureCategoryId = failureCategoryId,
                    onDeleteMemo = { viewModel.deleteMemo(it) }
                )
            }
        }
    }
}

// Helper to pick localized label for a Category
private fun getCategoryDisplayName(category: Category, locale: Locale = Locale.getDefault()): String {
    return when (locale.language) {
        "ja" -> category.nameJa ?: category.nameEn ?: category.name
        else -> category.nameEn ?: category.nameJa ?: category.name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    onToggleFavorite: (Category) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        item {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") }
            )
        }

        // Category chips
        items(categories) { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onCategorySelected(category.id) },
                label = { Text(getCategoryDisplayName(category)) },
                leadingIcon = {
                    val interactionSource = remember { MutableInteractionSource() }
                    Icon(
                        imageVector = if (category.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Favorite",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current
                            ) {
                                onToggleFavorite(category)
                            }
                    )
                }
            )
        }
    }
}

@Composable
private fun MemosList(
    memos: List<Memo>,
    categories: List<Category>,
    failureCategoryId: Long?,
    onDeleteMemo: (Memo) -> Unit
) {
    val categoryMap = remember(categories) {
        categories.associateBy { it.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(memos, key = { it.id }) { memo ->
            MemoCard(
                memo = memo,
                categoryName = categoryMap[memo.categoryId]?.let { getCategoryDisplayName(it) } ?: "Unknown",
                failureCategoryId = failureCategoryId,
                onDelete = { onDeleteMemo(memo) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoCard(
    memo: Memo,
    categoryName: String,
    failureCategoryId: Long?,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
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
                AssistChip(
                    onClick = { },
                    label = { Text(categoryName) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                Row {
                    // If in Failure category, show per-memo reanalyze button (compare by categoryId)
                    if (failureCategoryId != null && memo.categoryId == failureCategoryId) {
                        IconButton(onClick = {
                            // Enqueue single memo reanalyze worker
                            val inputData = Data.Builder().putLong("memo_id", memo.id).build()
                            val workReq = OneTimeWorkRequestBuilder<ReanalyzeSingleMemoWorker>()
                                .setInputData(inputData)
                                .build()
                            WorkManager.getInstance(context).enqueue(workReq)
                        }) {
                            Icon(Icons.Default.Refresh, "Reanalyze")
                        }
                    }

                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Display sub-category and source app if available
            if (memo.subCategory != null || memo.sourceApp != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    memo.subCategory?.let { subCat ->
                        AssistChip(
                            onClick = { },
                            label = { Text(subCat) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    memo.sourceApp?.let { app ->
                        AssistChip(
                            onClick = { },
                            label = { Text("From: $app") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = memo.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (memo.originalCategory != null && memo.originalCategory != categoryName) {
                    Text(
                        text = "Originally: ${memo.originalCategory}",
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
        }
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

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleCategories = listOf(
        Category(id = 1, name = "Work", nameEn = "Work", nameJa = "仕事", isFavorite = true),
        Category(id = 2, name = "Personal", nameEn = "Personal", nameJa = "個人"),
        Category(id = 3, name = "Ideas", nameEn = "Ideas", nameJa = "アイデア")
    )
    val sampleMemos = listOf(
        Memo(id = 1, content = "Buy groceries", categoryId = 2, createdAt = System.currentTimeMillis()),
        Memo(id = 2, content = "Prepare slides for meeting", categoryId = 1, createdAt = System.currentTimeMillis()),
        Memo(id = 3, content = "App idea: smart notebook", categoryId = 3, createdAt = System.currentTimeMillis(), originalCategory = "Misc")
    )

    // Simple stateless preview: call the internal composables directly
    MemoizTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            CategoryFilterRow(
                categories = sampleCategories,
                selectedCategoryId = null,
                onCategorySelected = {},
                onToggleFavorite = {}
            )
            Divider()
            MemosList(
                memos = sampleMemos,
                categories = sampleCategories,
                failureCategoryId = null,
                onDeleteMemo = {}
            )
        }
    }
}
