# Memoiz

Memoiz is a privacy-first Android clipboard companion that captures text, links, and images, then categorizes and summarizes them on-device for quick recall.

## Highlights
- **One-tap capture** from Process Text, the Android share sheet, or the in-app "Paste from clipboard" FAB.
- **Two-stage AI categorization** that merges smart suggestions with your custom categories while keeping all processing on-device.
- **Actionable memo cards** that surface summaries, sub-categories, memo type badges, and quick actions (open, share, delete, re-analyze).
- **Category-centric organization** with expandable groups, drag-to-reorder headers, filtering chips, and manual reassignment via an edit dialog.

## Feature Details
### Capture & Input
- **Process Text**: highlight any text system-wide and choose *Categorize with Memoiz*.
- **Share Sheet**: share text, URLs, or images to Memoiz from any app.
- **Clipboard FAB**: tap the floating button to enqueue the most recent clipboard contents.

### Categorization & Analysis
- **Stage 1**: `MlKitCategorizer` generates main/sub categories + summaries using ML Kit GenAI (Prompt, Summarization, Image Description APIs).
- **Stage 2**: `CategoryMergeService` reconciles AI output with existing and custom categories to reduce duplication.
- **Localization**: summaries and category hints adapt to the system language (English or Japanese). Image captions default to English, then are rewritten when the locale is Japanese.
- **Memo Integrity**: re-analysis never changes memo type; failed analyses can be batch re-run or individually re-queued.

### AI model management
- The app performs on-device AI model management when needed: the main UI can trigger model downloads and shows progress or error states to the user.
- If a model download fails, the UI surfaces a clear error state and offers a retry option; the app falls back to safe behavior until models are available.
- Settings exposes the current AI feature status (e.g. Enabled / Disabled / Downloading / Error) so users can quickly see whether AI features are available.

### Main Screen Experience
- **Search + Filter Note**: top search box with a contextual banner (e.g., `カテゴリ「アイデア」でフィルタ中`).
- **Category Accordion**: expandable cards showing memo counts, drag handles, delete/reanalyze icons, and a pencil icon per memo for manual reassignment.
- **Memo Cards**: stacked type/sub-category chips, optional image thumbnail, raw content, summary, source app, timestamp, and quick actions (open/share/delete/reanalyze).
- **Manual Category Dialog**: lets users type a new category (auto-saved as "My Category") or pick from existing ones via a dropdown.

### Management Utilities
- **Re-analyze dialog** warns that categories/summaries may change while memo type remains fixed.
- **Category order** persists after drag-and-drop via `toast_category_order_saved` feedback.
- **Filters** for memo type (Text / Web / Image) and category, accessible from the navigation drawer.

## Architecture Snapshot
| Layer | Key Components |
| --- | --- |
| UI | Jetpack Compose screens (`MainScreen`, `SettingsScreen`), `MainViewModel`, `SettingsViewModel`, Material 3 theme |
| Domain | `Memo`, `MemoGroup`, `CategorizationResult` |
| Data | Room (`MemoEntity`, `CategoryEntity`, DAOs, `MemoizDatabase`), repositories |
| Services | `ContentProcessingLauncher`, `AiCategorizationService`, `MlKitCategorizer`, `CategoryMergeService` |
| Background | `ClipboardProcessingWorker`, WorkManager helpers |
| Utilities | `UsageStatsHelper`, `ImageUriManager`, localization helpers |

## Building & Running
```powershell
cd C:\Users\user\StudioProjects\Memoiz
.\gradlew.bat assembleDebug
```
Install the resulting APK (`app/build/outputs/apk/debug/`). Target devices must run Android 10 (API 29) or newer.

## How to Use the App
1. **Launch Memoiz** and grant requested permissions (Usage Access for source-app detection is optional but recommended).
2. **Capture content** using Process Text, the Share sheet, or the clipboard FAB.
3. **Review memos** grouped by category; tap headers to expand/collapse.
4. **Filter** by memo type or category from the navigation drawer; a banner below the search field indicates the active filters.
5. **Reorder categories** via the drag handle icon in each header.
6. **Edit categories manually** with the pencil icon → choose from existing categories or type a new one (auto-added as "My Category").
7. **Re-analyze** a memo via the refresh icon; confirm in the dialog to keep memo type unchanged while allowing category/summary updates.

## Privacy & Permissions
- All AI inference uses ML Kit’s on-device models; no memo content leaves the device unless future settings explicitly opt into cloud features.
- Usage Stats permission is optional and only queried via `AppOpsManager` to avoid crashes when disabled.

## Tech Stack
- Kotlin 1.9, Jetpack Compose, Material 3
- Room, WorkManager, Kotlin Coroutines
- ML Kit GenAI Prompt, Summarization, and Image Description APIs
- OkHttp + Jsoup for URL fetching, Coil for image loading

## License
Apache License 2.0 — see `LICENSE` for details.
