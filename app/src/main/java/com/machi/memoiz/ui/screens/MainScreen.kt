@file:OptIn(ExperimentalMaterial3Api::class)

package com.machi.memoiz.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
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
import com.machi.memoiz.domain.model.Category
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.ClipboardMonitorService
import com.machi.memoiz.ui.theme.MemoizTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main screen showing memos organized by categories.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val filteredMemos by viewModel.filteredMemos.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memoiz") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val intent = Intent(context, ClipboardMonitorService::class.java)
                    context.startForegroundService(intent)
                }
            ) {
                Icon(Icons.Default.Add, "Capture Clipboard")
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
            
            Divider()
            
            // Memos list
            if (filteredMemos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No memos yet.\nTap + to capture clipboard content.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                MemosList(
                    memos = filteredMemos,
                    categories = categories,
                    onDeleteMemo = { viewModel.deleteMemo(it) }
                )
            }
        }
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
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (category.isFavorite) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onToggleFavorite(category) }
                            )
                        } else {
                            Icon(
                                Icons.Default.StarBorder,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onToggleFavorite(category) }
                            )
                        }
                        Text(category.name)
                    }
                },
                trailingIcon = if (selectedCategoryId == category.id) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun MemosList(
    memos: List<Memo>,
    categories: List<Category>,
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
                categoryName = categoryMap[memo.categoryId]?.name ?: "Unknown",
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
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
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
                
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Delete")
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
        Category(id = 1, name = "Work", isFavorite = true),
        Category(id = 2, name = "Personal"),
        Category(id = 3, name = "Ideas")
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
                onDeleteMemo = {}
            )
        }
    }
}
