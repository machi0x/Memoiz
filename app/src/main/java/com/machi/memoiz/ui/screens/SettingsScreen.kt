@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.machi.memoiz.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.machi.memoiz.domain.model.Category
import com.machi.memoiz.util.UsageStatsHelper
import com.machi.memoiz.ui.theme.MemoizTheme

/**
 * Settings screen for managing custom categories and favorites.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val allCategories by viewModel.allCategories.collectAsState()
    val customCategories by viewModel.customCategories.collectAsState()
    val canAddCustomCategory by viewModel.canAddCustomCategory.collectAsState()
    val customCategoryCount by viewModel.customCategoryCount.collectAsState()
    
    val context = LocalContext.current
    val hasUsageStatsPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsHelper(context).hasUsageStatsPermission()
        } else {
            false
        }
    }
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (canAddCustomCategory) {
                FloatingActionButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, "Add Custom Category")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Usage Stats Permission Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (hasUsageStatsPermission) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (hasUsageStatsPermission) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Source App Detection",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (hasUsageStatsPermission)
                                        "Enabled - AI can see which app you copied from"
                                    else
                                        "Enable to detect source app for better categorization",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        if (!hasUsageStatsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Usage Access Settings")
                            }
                        }
                    }
                }
            }
            
            // Custom Categories Section
            item {
                Text(
                    text = "My Categories ($customCategoryCount/${SettingsViewModel.MAX_CUSTOM_CATEGORIES})",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Create up to 20 custom categories. AI will try to use these when categorizing your memos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (customCategories.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No custom categories yet.\nTap + to add one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(customCategories) { category ->
                    CategoryListItem(
                        category = category,
                        onToggleFavorite = { viewModel.toggleFavorite(category) },
                        onDelete = { viewModel.deleteCategory(category) },
                        showDelete = true
                    )
                }
            }
            
            // All Categories Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "All Categories",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Mark categories as favorites. AI will prioritize these when merging.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            val nonCustomCategories = allCategories.filter { !it.isCustom }
            items(nonCustomCategories) { category ->
                CategoryListItem(
                    category = category,
                    onToggleFavorite = { viewModel.toggleFavorite(category) },
                    onDelete = null,
                    showDelete = false
                )
            }
        }
    }
    
    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addCustomCategory(name)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun CategoryListItem(
    category: Category,
    onToggleFavorite: () -> Unit,
    onDelete: (() -> Unit)?,
    showDelete: Boolean
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    Icons.Default.Category,
                    contentDescription = null
                )
                Column {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (category.isCustom) {
                        Text(
                            text = "Custom Category",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (category.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (category.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                if (showDelete && onDelete != null) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
    }
    
    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Category") },
            text = { Text("Are you sure? All memos in this category will also be deleted.") },
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
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Category") },
        text = {
            Column {
                Text("Enter a name for your custom category:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it
                        error = null
                    },
                    label = { Text("Category Name") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        categoryName.isBlank() -> error = "Name cannot be empty"
                        categoryName.length > 50 -> error = "Name too long (max 50 characters)"
                        else -> onConfirm(categoryName.trim())
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Preview for Settings screen
@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    val sampleAll = listOf(
        Category(id = 1, name = "Work", isFavorite = true),
        Category(id = 2, name = "Personal"),
        Category(id = 3, name = "Ideas")
    )
    val sampleCustom = listOf(
        Category(id = 100, name = "Invoices", isCustom = true),
        Category(id = 101, name = "Recipes", isCustom = true, isFavorite = true)
    )

    MemoizTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Usage stats card preview
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(text = "Source App Detection", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Enabled - AI can see which app you copied from", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "My Categories (2/${SettingsViewModel.MAX_CUSTOM_CATEGORIES})", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            // Custom category items
            sampleCustom.forEach { category ->
                CategoryListItem(category = category, onToggleFavorite = {}, onDelete = {}, showDelete = true)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "All Categories", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            sampleAll.forEach { category ->
                CategoryListItem(category = category, onToggleFavorite = {}, onDelete = null, showDelete = false)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
