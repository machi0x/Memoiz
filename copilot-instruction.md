# Copilot for Memoiz — Operational Instructions

This document instructs a Copilot-style assistant ("Copilot") working inside the Memoiz repository. It defines mandatory guardrails, editing conventions, i18n rules, and quality gates to ensure safe, consistent changes.

Purpose: Make repository edits safely, consistently, and in a way reviewers can easily validate.

---

## Quick checklist (before editing)
- Write a one-line purpose (e.g., Add "Cat Comment" feature)
- List all files you will change
- Decide required string resources and prepare both EN and JP entries
- Keep changes small and run build/tests locally

---

## Product guardrails (mandatory)
- On-device first: Prefer local processing and models whenever possible.
- Network calls: Disabled by default. Only allowed with explicit owner instruction or explicit user opt-in.
  - Exception: fetching web page content (link preview / page summary) for "web-type" memos is permitted as part of memo ingestion or AI analysis when the memo contains a URL. Even in this case you must:
    - only fetch when the memo explicitly contains a URL or the user has given consent,
    - fetch the minimal necessary fields (title, snippet, summarized text) rather than full raw pages,
    - respect robots.txt and server usage policies,
    - avoid sending fetched raw page content to external services; treat fetched content as sensitive and apply the same non-exfiltration rules,
    - log only anonymized metadata (fetch success/failure, sizes, latency) not page content.
- No data exfiltration: Never send raw memo content, clipboard contents, credentials, or tokens to external services.
- Permission checks: Always check runtime permissions (e.g., AppOpsManager) before using privileged APIs; provide graceful fallback if denied.
- Client lifecycle: Close ML clients and IO handles when idle (e.g., in ViewModel.onCleared()).

---

## Code comment policy (important)
- Use comments for new features or genuinely complex logic that is hard to understand. Avoid adding verbose comments for small fixes.
- Do NOT leave historical decision notes in code (bad example: "// We decided to drop feature A so remove it and ..."). Such rationale belongs in PR descriptions or design docs (CHANGELOG, DESIGN.md), not in code.
- For minor refactors or bug fixes: prefer no comment or a minimal one-line note.
- For new features or non-obvious algorithms: add a short comment (1–3 lines) explaining intent and trade-offs.

---

## Internationalization (i18n) rules (mandatory)
- Memoiz supports Japanese and English. When adding any user-facing text (buttons, dialogs, prompts), always add both JP and EN strings.
- Always append to existing `strings.xml` files. Do NOT overwrite or remove existing resources.
- AI prompt templates and other message templates must be stored in string resources — do not hard-code prompt text in Kotlin/Java code.

Placement examples:
- EN (default): `app/src/main/res/values/strings.xml`
- JP: `app/src/main/res/values-ja/strings.xml`

Example entries (template):
- EN:
  ```xml
  <string name="cat_comment_progress_1">Memoriz is thinking...</string>
  <string name="cat_comment_prompt">Generate a short, heartwarming kitten-style comment about the memo: %1$s</string>
  ```
- JP:
  ```xml
  <string name="cat_comment_progress_1">メモイズが思案中…</string>
  <string name="cat_comment_prompt">次のメモについて、子猫の口調で短く心温まるコメントを作成してください: %1$s</string>
  ```

---

## How to present code changes (PR / suggestion format)
When proposing edits, include the following:
1. Title (one line)
2. Files changed (paths)
3. Short summary (1–3 lines)
4. New/changed strings list (EN and JP)
5. Manual verification steps
6. Security/privacy notes (if applicable)

Keep PRs small and reviewable.

---

## Common implementation examples
- Adding strings: append EN/JP entries to the appropriate `strings.xml` files. Include translator-friendly keys and context comments if needed.
- Adding a FAB button: modify the Compose/View file and reference `@string/` entries for labels and contentDescription (EN/JP).
- Dialog Activity: create `CatCommentActivity.kt` and `res/layout/dialog_cat_comment.xml`. Place UI text in resources.

---

## Quality gates (required before merging)
1. Build: `./gradlew assembleDebug` — pass
2. Lint: `./gradlew :app:lint` — pass
3. Unit tests: `./gradlew :app:testDebugUnitTest` — pass
4. Smoke test: exercise main UI flows on an emulator or small instrumentation test
5. Fix IDE/compiler errors and unresolved references

PowerShell example (run locally):
```
./gradlew assembleDebug; ./gradlew :app:lint; ./gradlew :app:testDebugUnitTest
```

---

## Security & privacy rules
- Default: no network usage. Requests to call external services must be explicit and justified.
- Exception for web-type memos: the app may fetch URL content to create previews or to prepare inputs for AI analysis, but only under the constraints listed under Product guardrails. Fetched page content must be minimized, handled locally when possible, and never exfiltrated without explicit consent.
- Telemetry: only anonymized, aggregated metrics. Never send raw memo contents or clipboard text in telemetry.
- Always implement graceful fallbacks when permissions are denied.

---

## Notes on AI prompts and outputs
- Store prompt templates in string resources and reference them from code. This allows localization and easier updates.
- When generating content using a GenAI API: redact or summarize sensitive fields where possible and avoid sending raw memo text unless explicitly required by the user.
- For the "Cat Comment" feature: the prompt should ask the model to speak as a kitten, avoid meta-comments about its identity, limit output to up to 3 sentences, and include a feeling token chosen from the allowed set (confused, cool, curious, difficult, happy, neutral, thoughtful). The mapping to drawable filenames is: confused.png, cool.png, curious.png, difficult.png, happy.png, neutral.png, thoughtful.png.

---

## Future considerations
- Define explicit model lifecycle policies (download, cache, delete, cancel).
- If adding background model prefetching, require explicit user opt-in and clear UI affordance to manage or remove cached models.

---

This document is the single-source Copilot guide for safe, consistent contributions to the Memoiz repository. If you want adjustments (tone, extra examples, PR templates), tell me what to add.


// Appended reference to Main UI spec
For behavioral details of the Main Screen (categories, headers, memo cards, FABs, and drawer), see `docs/main_ui_spec.md` (authoritative source-of-truth for Main UI behavior).

Enforcement note:
- When modifying Main UI files (see `docs/main_ui_spec.md` → "Doc-update rule" trigger list), Copilot and all contributors MUST update `docs/main_ui_spec.md` and any referenced screenshots in the same PR to reflect the change.
- A repository CI check will warn on pull requests that change Main UI trigger files without including corresponding updates to `docs/main_ui_spec.md` or the `docs/screenshots/` images. Treat CI warnings as required remediation before merge (update the doc or explain the rationale in the PR description).
