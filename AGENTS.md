# Memoiz Agents Overview

This document explains the internal responsibilities for assistants working on Memoiz.

## Product Guardrails
- **On-device first**: Clipboard content, memos, and analysis outputs must never leave the device unless a future settings toggle explicitly opts into cloud inference.
- **Explicit consent**: Any remote processing (not currently implemented) requires opt-in UI with clear copy.
- **Usage access**: Use `AppOpsManager` checks before querying usage stats to avoid crashes when the permission is missing.

## Categorization Agent Responsibilities
1. **MlKitCategorizer**
   - Generates localized category, subcategory, summary suggestions.
   - Uses ML Kit Prompt/Summarization/Image Description APIs with locale-aware options (English & Japanese supported today).
   - Must close clients on `ViewModel.onCleared()` or when no longer needed.
2. **CategoryMergeService**
   - Reconciles AI output with user categories (built-in + custom "My Categories").
   - Enforces conservative merging to prevent duplicate categories.
3. **ContentProcessingLauncher & Workers**
   - Entry point for clipboard/share/process-text flows.
   - Offload heavy work to `ClipboardProcessingWorker`; handle failures by queueing re-analysis jobs.

## UI Agent Responsibilities
- **MainScreen**: Maintains search/filter state, shows filter note banner, drag-to-reorder headers, manual category dialog. Also responsible for initiating on-device AI model downloads when required, and for presenting download progress and error states to users.
- **SettingsScreen**: Exposes current AI feature status (Enabled / Disabled / Downloading / Error) so users can quickly see whether AI features are available. Also hosts toggles for cloud opt-ins, diagnostics, and custom category management.
- **Manual Category Dialog**: Must keep text field + existing-category dropdown in sync; new names auto-register as "My Category".
- **Re-analyze UX**: Show confirm dialog explaining that memo type stays fixed while category/summary may change.

## Testing & Telemetry
- Prioritize on-device instrumentation tests for categorization flows.
- Log only anonymized metrics derived from category counts or system locale—never raw memo content.

## Future Considerations
- Model management: agents should monitor model download lifecycle (start/progress/success/failure) and provide retry/rollback behavior.
- Evaluate background prefetch of ML models with user consent.
