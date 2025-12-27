# Agent Guidelines for GenAI API Usage

This document outlines the best practices and mandatory guidelines for using Google's GenAI APIs within the Memoiz project. All current and future development must adhere to these principles.

## 1. Core Principle: User Privacy and Consent First

**Under no circumstances shall this application transmit user data (e.g., clipboard content, memo text) to any external or cloud-based service without explicit, opt-in consent from the user.**

- **Default to On-Device:** The ML Kit GenAI API has on-device capabilities. These should be preferred wherever possible.
- **Explicit Consent Required:** For any feature that requires sending data to a remote server, the user must actively opt-in. This choice must not be pre-selected. A clear and understandable explanation of what data is being sent and why must be provided.
- **Implement a Settings Toggle:** A dedicated section in the app's settings should allow users to easily enable or disable any cloud-based AI features.
- **Handle Sensitive Data:** Be aware that users may copy sensitive information, including passwords, personal details, or financial data. Our code must not persist or transmit such data without explicit user action and consent.

## 2. GenAI API Best Practices

Based on the current implementation (`AiCategorizationService`, `MlKitCategorizer`), we have established a solid pattern for using the API.

### a. Abstraction and Error Handling

- **Use a Centralized Service:** All calls to the ML Kit GenAI API should be channeled through a single, dedicated service like `MlKitCategorizer`. This ensures consistency and makes it easier to enforce policies.
- **Graceful Failure:** API calls can fail. The current implementation correctly handles this by assigning a `FAILURE` category. This is excellent practice. It allows the user to still save their memo and provides a mechanism (`ReanalyzeFailedMemosWorker`) for later processing.
- **Handle Exceptions:** Always wrap API calls in `try-catch` blocks to handle `MlKitException` and other potential errors. Do not let API failures crash the app.

### b. Categorization Flow

Our current two-stage categorization is a robust model:

1.  **Generate Localized Labels:** Use `categorizeLocalized` to get initial category suggestions in multiple languages (e.g., English and Japanese). This provides a good user experience.
2.  **Merge with Existing Categories:** Before creating a new category, use a conservative matcher like `matchCategoryToListLocalized` to see if the suggestion can be merged into an existing user-created category. This respects the user's existing organization.

## 3. Advanced API Usage (from GenAI Samples)

### a. Model Initialization and Configuration

The GenAI models can be configured with specific options. For example, when using the `Summarizer`, you can specify the `InputType`, `OutputType`, and `Language`.

```text
val options =
  SummarizerOptions.builder(this)
    .setInputType(InputType.ARTICLE)
    .setOutputType(OutputType.ONE_BULLET)
    .setLanguage(Language.ENGLISH)
    .build()
val summarizer = Summarization.getClient(options)
```

This allows us to tailor the model's behavior to the specific task at hand.

### b. Resource Management

It is critical to release GenAI resources when they are no longer needed. This is typically done in the `onDestroy()` method of an Activity or the `onCleared()` method of a ViewModel.

```text
override fun onDestroy() {
    super.onDestroy()
    summarizer?.close()
}
```

Failure to do so can lead to memory leaks.

### c. On-Device Model Management

The GenAI APIs provide tools to manage on-device models:

- **`checkFeatureStatus()`**: Checks if a model is available on the device.
- **`downloadFeature()`**: Downloads a model if it is not available.

This allows us to provide on-device functionality and only download models when needed.

### d. Streaming for Responsiveness

For long-running tasks, the GenAI APIs support streaming results. This allows us to display results to the user as they are generated, rather than waiting for the entire task to complete.

```text
summarizer.runInference(summarizeRequest, streamingCallback)
```

This is a good pattern to use for any potentially time-consuming operations.

### e. Rewriting API (RewriterOptions) — language-aware usage

The Rewriting (Rewriter) API can transform text into different styles (formal, concise, emoji, etc.). When supporting multiple languages — especially English and Japanese — prefer explicit, locale-aware configuration rather than a single "default" options object.

- Supported OutputType (examples): ELABORATE, EMOJIFY, SHORTEN, FRIENDLY, PROFESSIONAL, REPHRASE
- Supported Languages (common): ENGLISH, JAPANESE, KOREAN (check feature availability for on-device support)

Recommended approach

We recommend building RewriterOptions per target language and style instead of sharing a single global options object. This lets the app tune the output type to the language and user preference and avoid awkward cross-language style mappings.

We recommend preferring on-device models where possible. Use the checkFeatureStatus API and downloadFeature when appropriate. Only perform remote inference if the user has explicitly opted in.

We recommend providing sensible fallbacks. If a Japanese rewriter model is unavailable on-device, consider falling back to English with a conservative output type such as REPHRASE, or surface a clear "model not available" state in the UI so the user can choose to opt into a cloud operation.

We recommend always closing client instances when finished to avoid resource leaks; for example, call rewriter.close from onCleared or onDestroy.

We recommend using streaming callbacks for long-running operations when possible so the UI can remain responsive and show partial results to the user.

Kotlin example — locale-aware RewriterOptions factory and client creation:

```text
fun buildRewriterOptions(context: Context, locale: Locale, styleHint: String?): RewriterOptions {
  val language = when (locale.language) {
    "ja" -> RewriterOptions.Language.JAPANESE
    "ko" -> RewriterOptions.Language.KOREAN
    else -> RewriterOptions.Language.ENGLISH
  }

  val outputType = when (styleHint?.lowercase(Locale.ROOT)) {
    "concise", "shorten" -> RewriterOptions.OutputType.SHORTEN
    "friendly" -> RewriterOptions.OutputType.FRIENDLY
    "professional" -> RewriterOptions.OutputType.PROFESSIONAL
    "emoji", "emojify" -> RewriterOptions.OutputType.EMOJIFY
    "elaborate" -> RewriterOptions.OutputType.ELABORATE
    else -> RewriterOptions.OutputType.REPHRASE // safe default
  }

  return RewriterOptions.builder(context)
    .setLanguage(language)
    .setOutputType(outputType)
    .build()
}

// Usage example: create a client tuned for device locale
val options = buildRewriterOptions(context, Locale.getDefault(), userStyleHint)
val rewriter = Rewriting.getClient(options)

// Run inference (pseudo-code; follow API for request object and streaming callbacks)
// val request = RewritingRequest.Builder(textToRewrite).build()
// rewriter.runInference(request, callback)

// When finished
// rewriter.close()
```

Why not use one default option object?

- Language nuance: a style that reads naturally in English (e.g., `ELABORATE` or `EMOJIFY`) might not map well to Japanese. Creating per-language options helps preserve naturalness and avoids awkward translations.
- Model availability: different on-device models may be installed for different languages; constructing options on demand lets you check feature status and download only what's necessary.

Practical recommendations for Memoiz:

- Provide a Settings toggle for "Use cloud GenAI features" and respect it everywhere before making remote calls.
- Offer a small mapping UI that exposes a few high-level styles (e.g., "Polish (formal)", "Shorten", "Friendly") and translate those to OutputType using the factory above.
- If a user explicitly requests a cloud-only style and the device model isn't available, clearly show an opt-in consent dialog explaining data transmission.
- Log model feature status (checkFeatureStatus) and use it to pre-warm downloads during idle periods (with user consent) to improve perceived latency.

Notes and links

- The ML Kit GenAI Summarization/Proofreading/Rewriting APIs support English and Japanese (and other languages such as Korean) — check the feature status at runtime and keep resource management disciplined.
- Always handle `MlKitException` and return graceful failure states so memos can still be saved and reanalyzed later.

### f. Image Description API (image-to-text)

ML Kit GenAI provides an Image Description API that generates short, human-readable captions or descriptions for images. This is useful for automatically annotating copied images, improving accessibility, or generating a quick summary for an attached image.

Key points and constraints

- Purpose: produce concise descriptions of the visible content of an image (objects, scenes, actions). Not a replacement for full computer vision pipelines when you need precise object detection or OCR.
- Privacy: treat images like text — never upload images to a cloud service without explicit opt-in by the user. Default to on-device inference whenever possible.
- Supported modes: on-device and cloud (depending on model availability and library release). Check feature availability at runtime using the API's feature status checks.
- Input sizing: downscale very large images before sending to the model to reduce memory and latency. Preserve aspect ratio and handle EXIF rotation properly.
- Safety: the model may produce sensitive content descriptions for images that contain personal data. Add a review step before any auto-sharing.

Dependency (Gradle)

To use the Image Description API, add its Gradle dependency (beta example shown):

```text
implementation("com.google.mlkit:genai-image-description:1.0.0-beta1")
```

Recommended workflow

1. Check the user's settings consent for any cloud-based processing.
2. Check on-device feature status for the image description model and download if appropriate.
3. Build ImageDescriptionOptions (specify Language if the API supports it) per locale and use case.
4. Create the image description client, run inference with an image input, and close the client when finished.
5. Provide a safe fallback (e.g., show "No local model available" and prompt user to opt into cloud processing) if the device model is missing.

Pseudo-code example (follow the exact ML Kit API for request/response classes in your project):

```text
// Prepare options tuned to locale
val imageDescOptions = ImageDescriptionOptions.builder(context)
  .setLanguage(ImageDescriptionOptions.Language.ENGLISH) // or JAPANESE based on locale
  .build()

// Create client
val imageDescriber = ImageDescription.getClient(imageDescOptions)

// Prepare image input (Bitmap/ByteArray/InputImage). Downscale if necessary and handle EXIF rotation.
// val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)

// Build request and run inference (pseudo API names; use actual ML Kit request builders)
// val request = ImageDescriptionRequest.Builder(inputImage).build()
// imageDescriber.runInference(request, callback)

// Process the returned description(s) and present them in the UI or attach them to the memo

// When done
// imageDescriber.close()
```

Practical tips for Memoiz

- If Clipboard contains an image, run a quick on-device feature check; if available, produce a short caption and show it to the user for acceptance before saving.
- Keep generated captions short (one or two sentences / a short phrase) to avoid cluttering the memo list.
- For multilingual support, construct `ImageDescriptionOptions` according to the user's UI locale (English and Japanese supported by ML Kit GenAI samples). If a localized model is not available, fall back to English and display this fallback clearly to the user.
- Log model availability and errors to help telemetry and to pre-warm downloads during idle periods with the user's permission.

## 4. API Versioning

The GenAI libraries we are using (`alpha`, `beta`) are not yet stable. Be prepared for breaking changes in future library updates. When updating dependencies, thoroughly test all AI-related functionality.

## 5. Code Examples

### Checking for User Consent

Before making any potentially remote API call, check for user consent.

```text
// In your settings/preferences management
suspend fun canUseCloudAi(): Boolean {
    // This should read from a DataStore or SharedPreferences value
    // that is controlled by the user in the Settings screen.
    return userPreferences.isCloudAiEnabled()
}

// In your service (e.g., AiCategorizationService)
// Note: replace the placeholder '...' with actual parameters in real code.
suspend fun categorizeContent(content: String, params: Any) {
    if (canUseCloudAi()) {
        // Proceed with API call
        val result = mlKitCategorizer.categorizeLocalized(content, sourceApp)
        // ...
    } else {
        // Return a default or "Failure" state
        return CategorizationResult(finalCategoryName = "FAILURE")
    }
}
```

## 6. Passive Clipboard Capture Responsibilities

- Memoiz no longer runs a foreground clipboard-monitoring service. Agents must respect this power-saving design and rely on user-triggered entry points only (Process Text action, share intents, clipboard button).
- When adding new clipboard-related functionality, always confirm it is initiated by explicit user action and flows through `ContentProcessingLauncher` so WorkManager can handle heavy work off the main thread.
- Documentation updates are mandatory whenever a new trigger or entry point is introduced so that privacy expectations remain transparent.
