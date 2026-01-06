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
- **Stage 1**: `MlKitCategorizer` generates main/sub categories + summaries using ML Kit GenAI (Prompt, Summarization, Image Description APIs). The categorizer logs the inference flow so developers can trace "analysis → error → fallback → result" in logcat.
- **Image fallback**: when an image analysis fails with a permanent GenAI error (for example AICore inference/policy errors), the app attempts a fallback to the text-generation (Prompt) API by sending an image part plus a short prompt to describe the image in English while avoiding sensitive wording. The primary fallback uses a bitmap-based ImagePart for reliability; if that fails the implementation will try a file/URI variant as a secondary attempt.
- **Stage 2**: `CategoryMergeService` reconciles AI output with existing and custom categories to reduce duplication. Note: automatic "batch re-analysis" flows were removed — merges and reclassification happen during individual memo processing or when explicitly triggered by the user per memo.
- **Localization**: summaries and category hints adapt to the system language (English or Japanese). Image captions default to English, then are rewritten when the locale is Japanese.
- **Memo Integrity**: re-analysis never changes memo type; failed analyses can be re-queued individually but global/batch re-analysis has been removed to avoid unexpected quota consumption and running AI while the app is backgrounded.

### AI model management
- The app performs on-device AI model management when needed: the main UI can trigger model downloads and shows progress or error states to the user.
- If a model download fails, the UI surfaces a clear error state and offers a retry option; the app falls back to safe behavior until models are available.
- Settings exposes the current AI feature status (e.g. Enabled / Disabled / Downloading / Error) so users can quickly see whether AI features are available.

### Main Screen Experience
- **Search + Filter Note**: top search box with a contextual banner (e.g., `カテゴリ「アイデア」でフィルタ中`).
- **Category Accordion**: expandable cards showing memo counts, drag handles and a pencil icon per memo for manual reassignment. (Category-level batch re-analysis controls have been removed.)
- **Memo Cards**: stacked type/sub-category chips, optional image thumbnail, raw content, summary, source app, timestamp, and quick actions (open/share/delete/reanalyze). Re-analyze is per-memo only.
- **Pin color for image memos**: image memos that include an app-local copy (file:// or FileProvider URI owned by the app) display a red pin; image memos that only reference an external URI show a blue pin.
- **Manual Category Dialog**: lets users type a new category (auto-saved as "My Category") or pick from existing ones via a dropdown.

### Management Utilities
- **Re-analyze dialog** warns that categories/summaries may change while memo type remains fixed; re-analyze runs per-memo and will not run AI work while the app is backgrounded.
- **Category order** persists after drag-and-drop via `toast_category_order_saved` feedback.
- **Filters** for memo type (Text / Web / Image) and category, accessible from the navigation drawer.

### Import / Export
- The Settings screen provides import/export tools to move user data between devices.
- Exports are produced as an optionally password-protected ZIP file (encrypted) and imports accept the same format. The export flow:
  - Prompts the user for an optional password to encrypt the archive.
  - Shows progress and success/failure UI while writing the archive.
  - Uses the `zip4j` library for strong, tested ZIP encryption/decryption.
  - Never logs raw memo content or passwords; all file I/O runs off the UI thread and surfaces friendly error messages when needed.

Use the Settings → Export / Import options to backup or restore your memos. Encrypted exports require the same password to import.

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
