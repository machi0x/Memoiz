# Memoiz - Architecture and Implementation Details

## Overview

Memoiz is an Android app that automatically categorizes clipboard content using AI and saves them as organized memos. The app implements a 2-stage AI categorization process while respecting Android 10+ clipboard restrictions.

## Key Requirements Implementation

### 1. AI-Powered 2-Stage Categorization

#### Stage 1: Free Categorization (`AiCategorizationService`)
The AI analyzes clipboard content and assigns a category based on content analysis. Currently implemented with rule-based logic as a placeholder for Gemini Nano integration:

```kotlin
// Location: app/src/main/java/com/machika/memoiz/service/AiCategorizationService.kt

private suspend fun performFirstStageCategorization(content: String): String {
    // Rule-based categorization (placeholder for Gemini Nano)
    // Examples: URLs → "Links", Tasks → "Tasks", etc.
}
```

#### Stage 2: Smart Merge with User Preferences
After initial categorization, the AI attempts to merge the result with:
1. **User's custom categories** (max 20, priority 1)
2. **User's favorite categories** (priority 2)

```kotlin
private suspend fun performSecondStageMerge(
    firstStageCategory: String,
    customCategories: List<Category>,
    favoriteCategories: List<Category>
): String {
    // Check similarity with custom categories first
    // Then check favorite categories
    // Use semantic matching (placeholder for AI-based matching)
}
```

### 2. Custom Categories Management

Users can create up to 20 custom "My Categories" (`SettingsViewModel`):

```kotlin
companion object {
    const val MAX_CUSTOM_CATEGORIES = 20
}
```

The database tracks custom categories with the `isCustom` flag:

```kotlin
@Entity(tableName = "categories")
data class CategoryEntity(
    val id: Long = 0,
    val name: String,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,  // Marks user's custom categories
    val createdAt: Long = System.currentTimeMillis()
)
```

### 3. Favorite Categories

Any category (custom or auto-generated) can be marked as favorite. The AI respects these in the 2nd stage merge:

```kotlin
// Repository method to toggle favorite status
suspend fun toggleFavorite(category: Category) {
    val updated = category.copy(isFavorite = !category.isFavorite)
    categoryDao.updateCategory(updated.toEntity())
}
```

### 4. Android 10+ Clipboard Restrictions

Android 10 introduced restrictions on background clipboard access. Memoiz handles this through **explicit user-triggered entry points instead of a long-running foreground service**:

#### Process Text + Share Entry (`ProcessTextActivity`)
- Exposes the "Categorize with Memoiz" option when users select text (ACTION_PROCESS_TEXT)
- Also registered for ACTION_SEND so anything shared via the system share sheet (text/image) flows into Memoiz
- Activity extracts the shared payload and enqueues `ClipboardProcessingWorker`

#### In-App Clipboard Button
- The main screen surface includes a "Paste from clipboard" FAB that pulls current clipboard content only when the user taps it
- Clipboard access is therefore always tied to an explicit user gesture inside the app

#### Share Sheet Shortcut
- Users can invoke Memoiz from the Android share sheet (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`)
- Supports both text and images so no foreground service polling is required

### 5. Background Processing with WorkManager

Heavy AI processing happens in background via `ClipboardProcessingWorker`:

```kotlin
class ClipboardProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        // 1. Get clipboard content from input data
        // 2. Fetch custom and favorite categories
        // 3. Perform 2-stage AI categorization
        // 4. Save memo to database
    }
}
```

## Database Schema

### Tables and Relationships

```
┌────────────────────┐
│   CategoryEntity   │
├────────────────────┤
│ id (PK)           │
│ name              │
│ isFavorite        │◄─────┐
│ isCustom          │      │
│ createdAt         │      │ Foreign Key
└────────────────────┘      │ Relationship
                            │
                            │
┌────────────────────┐      │
│    MemoEntity      │      │
├────────────────────┤      │
│ id (PK)           │      │
│ content           │      │
│ imageUri          │      │
│ categoryId (FK)   │──────┘
│ originalCategory  │  (stores 1st stage result)
│ createdAt         │
└────────────────────┘
```

### Key Fields

**CategoryEntity:**
- `isCustom`: Identifies user's "My Categories" (max 20)
- `isFavorite`: Marks categories for AI prioritization

**MemoEntity:**
- `originalCategory`: Preserves AI's first-stage categorization
- `categoryId`: Final category after 2-stage merge
- Allows users to see how AI's decision evolved

## Data Flow

### Capture Triggers to Memo Storage

```
1. User selects, shares, or pastes content explicitly
   ↓
2. Entry point (ProcessTextActivity, Share sheet, clipboard button) collects payload
   ↓
3. Entry point enqueues WorkManager job via ContentProcessingLauncher
   ↓
4. Worker fetches custom/favorite categories
   ↓
5. Worker calls AiCategorizationService
   ├─→ Stage 1: Free categorization
   └─→ Stage 2: Merge with user preferences
   ↓
6. Worker finds/creates category in DB
   ↓
7. Worker saves memo with:
   - Final category ID
   - Original category name (for transparency)
```

## UI Architecture

### Screens

**MainScreen** (`app/src/main/java/com/machika/memoiz/ui/screens/MainScreen.kt`)
- Shows memos grouped by category
- Filter chips for category selection
- Displays original vs final category in memo cards
- FAB to trigger clipboard monitoring
- Floating actions: "Paste from clipboard" button plus shortcut to share sheet

**SettingsScreen** (`app/src/main/java/com/machika/memoiz/ui/screens/SettingsScreen.kt`)
- Manage custom categories (max 20)
- Toggle favorite status on any category
- Delete custom categories (cascades to memos)

### ViewModels

**MainViewModel**
```kotlin
- categories: StateFlow<List<Category>>  // All categories
- memos: StateFlow<List<Memo>>          // All memos
- filteredMemos: StateFlow<List<Memo>>  // By selected category
- selectCategory(categoryId)            // Filter memos
- toggleFavorite(category)              // Mark as favorite
```

**SettingsViewModel**
```kotlin
- customCategories: StateFlow<List<Category>>
- customCategoryCount: StateFlow<Int>         // Enforce max 20
- canAddCustomCategory: StateFlow<Boolean>    // Check limit
- addCustomCategory(name)                     // Add if < 20
```

## Privacy & Security

### On-Device Processing
- All AI categorization happens locally
- No network requests for categorization
- Gemini Nano (when integrated) runs entirely on-device

### Explicit Consent
- Clipboard accessed only on explicit user action
- Notification tap or tile tap = consent
- Complies with Android 10+ restrictions

### Data Storage
- Room database stored locally
- No cloud sync (by design)
- Can be backed up via Android backup service

## Future Enhancements

### Gemini Nano Integration
Currently, `AiCategorizationService` uses rule-based logic. When Gemini Nano becomes available:

1. Replace `performFirstStageCategorization()` with Gemini Nano API
2. Use AI for semantic category matching in `performSecondStageMerge()`
3. Enable image content analysis

### Suggested Implementation:
```kotlin
// Future Gemini Nano integration
private suspend fun performFirstStageCategorization(content: String): String {
    val prompt = """
        Analyze this clipboard content and suggest a category:
        Content: $content
        
        Suggest a short category name (1-3 words).
    """.trimIndent()
    
    return geminiNanoClient.generateText(prompt)
}
```

## Testing Considerations

### Unit Tests
- `AiCategorizationService`: Test 2-stage logic
- `CategoryRepository`: Test custom category limit (20)
- `MemoRepository`: Test CRUD operations

### Integration Tests
- WorkManager: Test clipboard processing flow
- Room Database: Test foreign key cascades
- ViewModels: Test state flows and actions

### UI Tests
- Settings: Verify max 20 custom categories enforced
- Main Screen: Verify filtering by category works
- Dialogs: Verify delete confirmations

## Performance Considerations

### Database Optimization
- Foreign key indices on `MemoEntity.categoryId`
- Query optimization with Room's Flow for reactive UI
- Cascade deletes for category removal

### Background Work
- WorkManager handles retry on failure
- Debouncing not needed (explicit user trigger)
- No battery drain from polling

### Memory Management
- ViewModels with `viewModelScope` for proper lifecycle
- StateFlow with `WhileSubscribed(5000)` for UI-aware collection
- Room flows automatically cleaned up

## Configuration

### Customizable Parameters

In `SettingsViewModel`:
```kotlin
const val MAX_CUSTOM_CATEGORIES = 20  // User preference limit
```

In `AiCategorizationService`:
```kotlin
// Similarity threshold for category merging
private fun isSimilarCategory(category1: String, category2: String): Boolean
```

## Manifest Configuration

### Required Permissions
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- Usage stats for source app lookup -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />
```

### Components
- `ProcessTextActivity`: handles ACTION_PROCESS_TEXT and ACTION_SEND intents

## Summary

Memoiz implements all requirements from the problem statement:

✅ **AI-powered categorization** with 2-stage process  
✅ **On-device processing** (Gemini Nano placeholder)  
✅ **Android 10+ clipboard handling** (Process Text, share sheet, clipboard button)  
✅ **WorkManager** for background processing  
