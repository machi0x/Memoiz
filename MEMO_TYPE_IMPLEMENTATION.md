# Memo Type Implementation Summary

## Overview
This implementation adds memo type classification to the Memoiz app, allowing users to distinguish between Text, Web site, and Image memos. The changes also improve image categorization by using AI-generated descriptions instead of hardcoded categories.

## Changes Implemented

### 1. Database Schema Changes

#### Added MemoType Constants (`MemoEntity.kt`)
```kotlin
object MemoType {
    const val TEXT = "TEXT"
    const val WEB_SITE = "WEB_SITE"
    const val IMAGE = "IMAGE"
}
```

#### Updated MemoEntity
- Added `memoType: String` field with default value `MemoType.TEXT`
- Updated documentation to include memoType parameter

#### Database Migration (v4 → v5)
- Added `MIGRATION_4_5` to handle schema upgrade
- Adds `memoType` column with default value "TEXT"
- Auto-migrates existing records:
  - URLs (starting with http:// or https://) → `WEB_SITE`
  - Records with imageUri → `IMAGE`
  - All others → `TEXT`

### 2. Domain Model Updates

#### Memo Model (`Memo.kt`)
- Added `memoType: String = MemoType.TEXT` field
- Updated repository converters to handle memoType

### 3. AI Categorization Improvements

#### MlKitCategorizer (`MlKitCategorizer.kt`)
**Previous Behavior**: Images were always categorized as "Image" (hardcoded)

**New Behavior**: 
- Get image description from ML Kit Image Description API
- Use description to generate category via LLM (same as text content)
- Generate sub-category based on description
- Falls back to "Image" category only if description generation fails

```kotlin
suspend fun categorizeImage(bitmap: Bitmap, sourceApp: String?): Triple<String?, String?, String?>? {
    // Get image description
    val description = describeImage(bitmap)
    
    // Categorize based on description (not hardcoded)
    if (!description.isNullOrBlank()) {
        val category = generateText(buildCategorizationPrompt(description, sourceApp))
        val subCategory = generateText(buildSubCategoryPrompt(description, category ?: "", sourceApp))
        return Triple(category, subCategory, description)
    }
    // Fallback only
    return Triple(imageCategoryLabel(), null, null)
}
```

#### AiCategorizationService (`AiCategorizationService.kt`)
- `processText`: Determines memo type based on content (TEXT or WEB_SITE)
- `processImage`: Sets memoType to IMAGE and stores description in content field
- Both methods now set appropriate memoType during processing

### 4. UI Changes

#### MainScreen (`MainScreen.kt`)

**Memo Type Badge**:
- Each memo card displays a badge showing its type (Text/Web site/Image)
- Badge includes icon and label for visual clarity

**Image Thumbnails**:
- Added Coil library for efficient image loading
- Image memos display 80dp thumbnail alongside content
- Thumbnail is rounded with 8dp corner radius
- Scales using ContentScale.Crop for consistent appearance

**Memo Type Filter in Drawer**:
- Added "Filter by Type" section above "Filter by Category"
- Three filter options:
  - Text (with Notes icon)
  - Web site (with Language icon)  
  - Image (with Image icon)
- Filters work independently from category filters
- Shows "All" option to clear type filter

**Action Buttons**:
- Now based on memoType instead of content inspection
- IMAGE: Opens image viewer
- WEB_SITE: Opens URL in browser
- TEXT: Shares text content

#### MainViewModel (`MainViewModel.kt`)
- Added `_memoTypeFilter: MutableStateFlow<String?>`
- Updated `memoGroups` to filter by both category AND memo type
- Added `setMemoTypeFilter(memoType: String?)` method

### 5. Worker Updates

All workers updated to handle memoType field:
- `ClipboardProcessingWorker`: Passes memoType when creating memos
- `ReanalyzeSingleMemoWorker`: Updates memoType when re-analyzing
- `ReanalyzeFailedMemosWorker`: Updates memoType during batch re-analysis

### 6. Resources

#### Strings (`strings.xml`)
```xml
<!-- Memo type labels -->
<string name="memo_type_text">Text</string>
<string name="memo_type_web_site">Web site</string>
<string name="memo_type_image">Image</string>

<!-- Filter sections -->
<string name="drawer_filter_by_type">Filter by Type</string>
<string name="drawer_filter_by_category">Filter by Category</string>
```

#### Dependencies (`app/build.gradle.kts`)
- Added Coil Compose: `implementation("io.coil-kt:coil-compose:2.5.0")`

## Benefits

### For Users
1. **Better Organization**: Filter memos by type to quickly find images, web links, or text notes
2. **Visual Clarity**: Memo type badges make it instantly clear what kind of content each memo contains
3. **Improved Image Experience**: Thumbnails provide visual preview without opening the full image
4. **Smarter Categorization**: Images are now categorized based on their content description, not just labeled as "Image"

### For Developers
1. **Type Safety**: Explicit memo type field prevents relying on heuristics
2. **Extensibility**: Easy to add new memo types in the future
3. **Database Integrity**: Proper migration ensures existing data is correctly typed
4. **Consistent Logic**: Single source of truth for memo type determination

## Migration Path

### Existing Users
When users upgrade to this version:
1. Database automatically migrates from v4 to v5
2. Existing memos are analyzed:
   - URLs get `WEB_SITE` type
   - Memos with images get `IMAGE` type
   - Others get `TEXT` type
3. No data loss or manual intervention required

### New Users
- All new memos are properly typed during initial save
- Type is determined in pre-processing phase before AI categorization

## Technical Notes

### Image Memo Content
- Image memos now store the AI-generated description in the `content` field
- This allows the description to be searchable and used for categorization
- The `imageUri` field still stores the reference to the actual image file

### Summary Display Logic
- For image memos: Don't show summary separately if it's the same as content
- For text/web memos: Show summary if it differs from content
- Prevents duplicate text display for images

### Performance Considerations
- Coil library provides efficient image caching and loading
- Thumbnails are displayed at fixed 80dp size to prevent memory issues
- Async loading prevents UI blocking

## Future Enhancements

Possible future improvements:
1. Add more memo types (Audio, Video, Document, etc.)
2. Support multiple images per memo
3. Bulk type editing
4. Type-specific search filters
5. Export filtered by type

## Testing Recommendations

1. **Migration Testing**:
   - Test upgrade from v4 database with various memo types
   - Verify all existing memos get correct types

2. **Image Categorization**:
   - Test with various image types (photos, screenshots, diagrams)
   - Verify descriptions are meaningful
   - Check category assignment is appropriate

3. **UI Testing**:
   - Test memo type filter combinations
   - Verify thumbnails load correctly
   - Check action buttons work for each type
   - Test on different screen sizes

4. **Edge Cases**:
   - Empty content with image
   - Very long descriptions
   - Filter with no matching memos
   - Re-analyzing memos changes type correctly
