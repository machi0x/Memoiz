# Main UI — Behavioral Specification

Source of truth: the current implementation in `app/src/main/java/com/machi/memoiz/ui/screens/MainScreen.kt`, `MemoCard` composables, and `MainViewModel.kt`. Update this document whenever those files change; see "Doc-update rule" at the end.

## Purpose and scope
This document describes the expected behavior and visual grouping for the Main Screen (category list, category headers, memo cards), Floating Action Button (FAB) behavior, the navigation drawer, and related interactions. This is a behavioral specification (no pixel-perfect values). Use MaterialTheme semantic tokens for color/shape.

## Key source files / symbols
- `app/src/main/java/com/machi/memoiz/ui/screens/MainScreen.kt` — `MainScreen` composable, lazy list, FAB logic, drawer.
- Composables that render memo UI: look for `MemoCard`, `CategoryAccordion`, `CategoryHeader` in the same package.
- `app/src/main/java/com/machi/memoiz/ui/MainViewModel.kt` — ordering, expanded categories, actions (`toggleCategoryExpanded`, `onCategoryMoved`, `recordMemoUsed`).
- Strings: `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ja/strings.xml`.
- Relevant drawables: `app/src/main/res/drawable-xxhdpi/*` (pins, feeling icons, etc).

## Overview (high-level behavior)
- Categories are shown in the user-selected order (manual ordering) or according to the active `SortMode`. Rendering is flattened in a `LazyColumn`: header item then (if expanded) memo items, repeated.
- Header and its memo items form a contiguous group (“island”) when expanded. There is a vertical spacer between groups only.
- Use semantic colors (MaterialTheme colorScheme) rather than hard-coded RGB values.

## Category ordering & grouping
- Manual order: categories follow the sequence provided by the ViewModel (user-chosen).
- Sort modes: when manual order is off, categories are sorted by configured `SortMode` (e.g., CREATED_DESC, CATEGORY_NAME, MOST_USED).
- Rendering pattern: for each category
  1. Header item (reorderable)
  2. Optional divider
  3. Memo items (when expanded)
  4. Spacer between groups (8.dp)

## Category header (composition & behavior)
- Left-to-right elements:
  - Expand/collapse icon (chevron)
  - Category name and memo count formatted as: `CategoryName (N)`
  - Optional “New!” badge (for categories with new memos)
- Right-side elements:
  - Action icons (delete for custom categories), drag handle (reorder affordance)
- Interactions:
  - Tap header toggles expansion (`toggleCategoryExpanded`)
  - Drag handle supports reorder; `onCategoryMoved` invoked with new indices
- Visual:
  - Header uses a `Card` with a semantic container color (current implementation: `surfaceVariant`).
  - When expanded and memos exist, header bottom corners are squared (top corners rounded) so header + first memo appear contiguous (the “island” effect).

## Island rule & memo cards
- Island rule: when category has memos and is expanded, header and the first memo visually join as a single block (rounded top corners on header; first memo aligns to continue block).
- Implementation notes:
  - Header shape: `RoundedCornerShape(topStart=12.dp, topEnd=12.dp, bottomStart=0.dp, bottomEnd=0.dp)` when expanded with memos.
  - A `HorizontalDivider()` is emitted before memo items per current code, but the combined background/shape preserves island perception.
- MemoCard content:
  - Memo type icon (TEXT / WEB / IMAGE)
  - Optional sub-category chip
  - Optional image thumbnail (use the same decoration as main display)
  - Text content or URL summary
  - Timestamp and quick actions: open/share/delete/reanalyze
- Pins:
  - Local image memos (app-owned URI): red pin
  - External-only image URIs: blue pin

## Colors & shapes (semantic)
- Prefer MaterialTheme tokens: `colorScheme.surfaceVariant`, `onSurfaceVariant`, `primary`, etc.
- Avoid hard-coded color constants in new code. Document any exception with justification.

## FABs and action menu (behavior)
- Auto-hide behavior:
  - FAB auto-hides when the user scrolls down; shown otherwise. Derived from `lazyListState` scroll snapshot.
- Expanded vs collapsed:
  - Collapsed: single `FloatingActionButton` with `Add` icon.
  - Expanded: a column of `ExtendedFloatingActionButton`s (create, pick image, paste), plus small close button. Each uses `@string/*` labels.
- All FAB labels and contentDescription must be localized by adding EN/JP strings.
- Cat Comment action:
  - Appears as a dropdown/menu item per memo (string key `fab_cat_comment_label`) and launches `CatCommentDialogActivity`.

## Navigation drawer / side panel
- Contents:
  - Top banner image
  - Settings (`drawer_settings`), status (optional), Add category (`drawer_add_category`)
  - Filters: memo type (All, Text, Web Site, Image) and category list with icons (custom vs built-in)
- Each drawer item is `NavigationDrawerItem` with icon, label, and selection state.

## Interactions & gestures
- Drag-to-reorder categories via drag handle; call `viewModel.onCategoryMoved`.
- Tap header toggles expansion.
- Memo quick actions:
  - Reanalyze: uses `ContentProcessingLauncher.enqueueSingleMemoReanalyze`.
  - Delete, edit category, record usage via `viewModel.recordMemoUsed`.
- Scrolling affects FAB visibility.

## Accessibility & i18n
- All actionable UI elements must have `contentDescription` read from `strings.xml` (EN/JP).
- Add EN/JP entries for all new strings. Do not remove or overwrite existing resources—append only.
- Prompt templates (AI prompts) must be stored as string resources.

## Screenshots (current images)
The repository now contains actual screenshots under `docs/screenshots/`. Refer to the files below for visual examples used in this spec.

- `docs/screenshots/one_category_example.png` — Main screen showing a single category expanded (demonstrates the "island" rule where the header and first memo are visually contiguous).
- `docs/screenshots/side_panel_example.png` — Navigation drawer / side panel example (shows filters, add category, settings entry and category list).
- `docs/screenshots/FAB_expanded.png` — FAB expanded state showing the column of actions (create, pick image, paste) and the small close button.
- `docs/screenshots/opened_option_menu.png` — Per-memo options menu (shows quick actions and the Cat Comment menu item).
- `docs/screenshots/sort_key_selection.png` — Sort / ordering selection UI (shows available SortMode choices and manual-order icon state).

Instructions to update or replace these images:
1. Capture screenshots on an emulator or device matching the intended scenario.
2. Resize to ~1200×800 (recommended) and save using the exact file names above under `docs/screenshots/`.
3. Commit the PNG files and, if necessary, adjust the captions in this document to reflect UI changes.

## Doc-update rule (operational)
When any of the files below are modified in a PR, the PR must include a corresponding update to `docs/main_ui_spec.md`:
- `app/src/main/java/com/machi/memoiz/ui/screens/MainScreen.kt`
- Files containing `MemoCard` composable(s)
- Files containing `CategoryAccordion` / category header composables
- `app/src/main/java/com/machi/memoiz/ui/MainViewModel.kt`
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ja/strings.xml` (if UI labels change)
- Relevant drawables in `app/src/main/res/drawable-xxhdpi/` that alter main UI appearance

Operational rules:
- Copilot must update `docs/main_ui_spec.md` in the same PR that changes any trigger file. The update can be a short textual revision and/or screenshot replacement.
- Updates should prefer explicit textual notes of behavioral changes (what changed and why).
- Commit message convention: `feat(main-ui): <short change>; docs: update main_ui_spec`.

## CI / future automation (optional)
- Optionally add a CI check that detects changes to trigger files and warns if `docs/main_ui_spec.md` is not updated. If desired, we can draft a GitHub Action for this.

## How Copilot should use this spec
- Treat this document as authoritative when modifying the Main Screen.
- If a code change requires behavior change, update the spec in the same PR and explain the rationale.
- If ambiguous, prefer code as single source of truth and reflect the code in the spec.

---

This file was generated from the approved draft.
