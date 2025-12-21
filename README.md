# Memoiz

Android app that automatically categorizes clipboard content (text, images) using AI and saves them as organized memos.

## Features

### Core Functionality
- **AI-Powered Categorization**: Uses on-device AI (Gemini Nano / AICore placeholder) for privacy-focused, zero-cost categorization
- **2-Stage Categorization Process**:
  1. First stage: AI performs free categorization based on content analysis
  2. Second stage: AI merges with user's custom categories or favorite categories when possible
- **Clipboard Monitoring**: Respects Android 10+ clipboard restrictions by using:
  - Notification-based trigger (user taps notification after copying)
  - Quick Settings Tile for quick capture
- **Background Processing**: Heavy AI processing handled by WorkManager to avoid blocking UI

### User Features
- **Custom Categories**: Users can create up to 20 custom "My Categories" that AI prioritizes
- **Favorite Categories**: Any category can be marked as favorite, and AI respects these in the merge stage
- **Category Management**: Full CRUD operations on categories
- **Memo Organization**: View memos filtered by category
- **Clean Material 3 UI**: Modern Jetpack Compose interface

## Architecture

### Technology Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Database**: Room for persistent storage
- **Background Work**: WorkManager for async processing
- **AI**: Placeholder for Gemini Nano / AICore integration (on-device LLM)
- **Navigation**: Compose Navigation
- **Minimum SDK**: 29 (Android 10) - to properly handle clipboard restrictions

### Project Structure
```
app/src/main/java/com/machika/memoiz/
├── data/
│   ├── entity/          # Room entities (CategoryEntity, MemoEntity)
│   ├── dao/             # Data Access Objects
│   ├── repository/      # Repository pattern implementation
│   └── MemoizDatabase.kt
├── domain/
│   ├── model/           # Domain models
│   └── repository/      # Repository interfaces
├── service/
│   ├── AiCategorizationService.kt      # 2-stage AI categorization
│   ├── ClipboardMonitorService.kt      # Notification-based clipboard access
│   └── ClipboardTileService.kt         # Quick Settings Tile
├── worker/
│   └── ClipboardProcessingWorker.kt    # Background processing
└── ui/
    ├── screens/         # Compose screens (Main, Settings)
    ├── components/      # Reusable UI components
    └── theme/           # Material 3 theme
```

## Database Schema

### Category Table
- `id`: Primary key
- `name`: Category name
- `isFavorite`: User marked as favorite
- `isCustom`: User's custom category (max 20)
- `createdAt`: Timestamp

### Memo Table
- `id`: Primary key
- `content`: Text content from clipboard
- `imageUri`: Optional image URI
- `categoryId`: Foreign key to Category
- `originalCategory`: AI's first-stage categorization result
- `createdAt`: Timestamp

## AI Categorization Process

### Stage 1: Free Categorization
AI analyzes clipboard content and assigns a category based on content analysis:
- URLs → "Links"
- Tasks/TODOs → "Tasks"
- Code snippets → "Code"
- Shopping items → "Shopping"
- etc.

### Stage 2: Smart Merge
AI checks if the first-stage category can be merged with:
1. User's custom categories (priority)
2. User's favorite categories (secondary)
3. Uses semantic similarity to match categories

If no match is found, the first-stage category is used as-is.

## How to Use

1. **Install the app** on an Android device (API 29+)
2. **Grant permissions** for notifications and clipboard access
3. **Add custom categories** (optional) in Settings - up to 20
4. **Mark favorites** (optional) to help AI prioritize certain categories
5. **Copy content** to clipboard
6. **Trigger save** via:
   - Tap the notification that appears
   - Use the Quick Settings Tile
   - Tap the FAB in the app
7. **View organized memos** by category in the main screen

## Privacy

- **On-device processing**: All AI categorization happens on-device (when Gemini Nano is integrated)
- **No cloud uploads**: Your data never leaves your device
- **Explicit consent**: Clipboard is only accessed when you explicitly tap notification or tile
- **Respects Android restrictions**: Fully compliant with Android 10+ clipboard access restrictions

## Future Enhancements

- [ ] Full Gemini Nano / AICore integration (currently placeholder)
- [ ] Image content analysis and categorization
- [ ] Export memos to various formats
- [ ] Search functionality across memos
- [ ] Memo editing capabilities
- [ ] Category color coding
- [ ] Backup and restore functionality

## Requirements

- Android 10 (API 29) or higher
- Kotlin 1.9+
- Gradle 8.2+

## License

See LICENSE file for details.
