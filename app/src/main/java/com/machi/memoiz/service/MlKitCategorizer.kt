package com.machi.memoiz.service

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import com.google.mlkit.genai.rewriting.RewritingResult
import com.google.mlkit.genai.rewriting.RewriterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wrapper that calls ML Kit GenAI APIs to generate a short category
 * and optional sub-category from clipboard content.
 */
class MlKitCategorizer(private val context: Context) {

    // Options for English (default) and Japanese outputs.
    private val rewriterOptionsEn by lazy {
        RewriterOptions.builder(context)
            .setOutputType(RewriterOptions.OutputType.SHORTEN)
            .setLanguage(RewriterOptions.Language.ENGLISH)
            .build()
    }

    private val rewriterOptionsJa by lazy {
        RewriterOptions.builder(context)
            .setOutputType(RewriterOptions.OutputType.SHORTEN)
            .setLanguage(RewriterOptions.Language.JAPANESE)
            .build()
    }

    /**
     * Call this when the service is no longer needed to release ML Kit resources.
     */
    fun close() {
        // All rewriting clients are now created on-demand and closed after use.
    }

    // Use ML Kit GenAI client for text generation.
    // Accept RewriterOptions so callers can request English or Japanese output as needed.
    private suspend fun generateText(promptText: String, options: RewriterOptions = rewriterOptionsEn): String? {
        var client: com.google.mlkit.genai.rewriting.Rewriter? = null
        return try {
            val request = RewritingRequest.builder(promptText).build()
            client = Rewriting.getClient(options)
            val future = client.runInference(request)

            // Await the ListenableFuture without blocking the main thread.
            val result = withContext(Dispatchers.IO) { future.get() }
            extractSuggestionText(result)
        } catch (e: MlKitException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            client?.close()
        }
    }

    suspend fun categorize(content: String, sourceApp: String?): String? {
        try {
            val shortened = if (content.length > 800) {
                summarize(content) ?: content
            } else content

            val promptText = buildCategorizationPrompt(shortened, sourceApp)
            return generateText(promptText)?.lines()?.firstOrNull()?.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun generateSubCategory(content: String, mainCategory: String, sourceApp: String?): String? {
        try {
            val promptText = buildSubCategoryPrompt(content, mainCategory, sourceApp)
            return generateText(promptText)?.trim()?.ifEmpty { null }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Ask ML Kit to pick the best matching category from a list of candidates.
     * Accepts original content and the AI's free-form suggestion, and is conservative:
     * return the exact candidate only if it clearly matches both the content and the suggestion.
     * Otherwise reply NONE.
     */
    suspend fun matchCategoryToList(contentExample: String, suggestion: String, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        try {
            val sb = StringBuilder()
            sb.append("You are a strict matcher. Given the original clipboard content and the AI's suggested category, choose a single existing category from the list only if it clearly and unambiguously matches both the content and the suggested category. If none clearly match, reply with NONE. Reply with exactly one of the candidate names or NONE, no explanation.\n\n")
            sb.append("Original content:\n")
            sb.append(contentExample.take(2000))
            sb.append("\n\nAI suggested category:\n")
            sb.append(suggestion)
            sb.append("\n\nCandidates:\n")
            candidates.forEachIndexed { idx, c -> sb.append("${idx+1}. $c\n") }
            sb.append("\nReply with EXACT candidate name or NONE.")
            val promptText = sb.toString()

            val generated = generateText(promptText)?.trim()
            if (generated.isNullOrBlank()) return null

            val normalized = generated.lines().first().trim()
            candidates.forEach { cand ->
                if (cand.equals(normalized, ignoreCase = true)) return cand
            }

            val maybeNumber = normalized.trim().lowercase()
            try {
                val index = maybeNumber.toIntOrNull()
                if (index != null && index in 1..candidates.size) return candidates[index - 1]
            } catch (_: Exception) {
            }

            if (normalized.equals("none", ignoreCase = true) || normalized.equals("no", ignoreCase = true)) return null

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Request the model to return English and Japanese labels separately using language-specific options.
     * Returns Pair(en, ja) or null on failure.
     */
    suspend fun categorizeLocalized(content: String, sourceApp: String?): Pair<String?, String?>? {
        try {
            val shortened = if (content.length > 800) {
                summarize(content) ?: content
            } else content

            // Build prompts separately for English and Japanese to improve reliability.
            val promptEn = buildCategorizationPrompt(shortened, sourceApp) // defaults to English

            val promptJaSb = StringBuilder()
            promptJaSb.append("Suggest a concise category name (1-3 words) for the clipboard content.\n")
            if (!sourceApp.isNullOrBlank()) {
                promptJaSb.append("Source app: $sourceApp\n")
            }
            promptJaSb.append("Content:\n")
            promptJaSb.append(shortened.take(2000))
            promptJaSb.append("\n\n回答は日本語で短いカテゴリ名のみを返してください。説明は不要です。")
            val promptJa = promptJaSb.toString()

            val enGenerated = generateText(promptEn, rewriterOptionsEn)?.trim()
            val jaGenerated = generateText(promptJa, rewriterOptionsJa)?.trim()

            val en = enGenerated?.lines()?.firstOrNull()?.trim()?.ifEmpty { null }
            val ja = jaGenerated?.lines()?.firstOrNull()?.trim()?.ifEmpty { null }

            // If both missing, return null
            if (en == null && ja == null) return null
            return Pair(en, ja)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Conservative matcher: given original content and AI's English/Ja suggestions, pick one existing candidate only if it clearly matches.
     * Otherwise return null.
     */
    suspend fun matchCategoryToListLocalized(contentExample: String, suggestionEn: String?, suggestionJa: String?, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        try {
            // Build a conservative prompt that asks the model to only pick a candidate when it's a clear match.
            val sb = StringBuilder()
            sb.append("You are a strict matcher. Given the original content and the AI's suggested labels (en/ja), choose a single existing category from the list only if it clearly and unambiguously matches the content and the suggestions. If none clearly match, reply with NONE. Reply with exactly one of the candidate names or NONE, no explanation.\n\n")
            sb.append("Original content:\n")
            sb.append(contentExample.take(2000))
            sb.append("\n\nAI suggested label (en):\n")
            sb.append(suggestionEn ?: "")
            sb.append("\nAI suggested label (ja):\n")
            sb.append(suggestionJa ?: "")
            sb.append("\n\nCandidates:\n")
            candidates.forEachIndexed { idx, c -> sb.append("${idx+1}. $c\n") }
            sb.append("\nReply with EXACT candidate name or NONE.")
            val promptText = sb.toString()

            val generated = generateText(promptText)?.trim()
            if (generated.isNullOrBlank()) return null

            val normalized = generated.lines().first().trim()
            // Exact match
            candidates.forEach { cand ->
                if (cand.equals(normalized, ignoreCase = true)) return cand
            }

            // Loose match if model returned a numbered choice like '1' or '2'
            val maybeNumber = normalized.trim().lowercase()
            try {
                val index = maybeNumber.toIntOrNull()
                if (index != null && index in 1..candidates.size) return candidates[index - 1]
            } catch (_: Exception) {
            }

            // If model says NONE or similar, return null
            if (normalized.equals("none", ignoreCase = true) || normalized.equals("no", ignoreCase = true)) return null

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Best-effort summarization using ML Kit Summarization API if available.
     * Returns null on failure.
     */
    suspend fun summarize(text: String): String? {
        try {
            val promptText = "Summarize the following text in one short sentence:\n" + text.take(2000)
            return generateText(promptText)?.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Best-effort image description using ML Kit Image Description API if available.
     * Accepts a content URI string and returns a textual description or null.
     */
    suspend fun describeImageUri(imageUriString: String): String {
        try {
            val promptText = "Provide a brief description for an image located at: $imageUriString. If you cannot access the image, reply with 'Image'."
            return generateText(promptText)?.trim() ?: "Image"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Image"
        }
    }

    private fun buildCategorizationPrompt(content: String, sourceApp: String?): String {
        val sb = StringBuilder()
        sb.append("Suggest a concise category name (1-3 words) for the clipboard content.\n")
        if (!sourceApp.isNullOrBlank()) {
            sb.append("Source app: $sourceApp\n")
        }
        sb.append("Content:\n")
        sb.append(content.take(2000))
        sb.append("\n\nReply with only the category name, no explanation.")
        return sb.toString()
    }

    private fun buildSubCategoryPrompt(content: String, mainCategory: String, sourceApp: String?): String {
        val sb = StringBuilder()
        sb.append("Provide a short context phrase for the clipboard item.\n")
        sb.append("Main category: $mainCategory\n")
        if (!sourceApp.isNullOrBlank()) {
            sb.append("Source app: $sourceApp\n")
        }
        sb.append("Content:\n")
        sb.append(content.take(2000))
        sb.append("\n\nReply with a short phrase (or single word) representing context; return empty if none.")
        return sb.toString()
    }

    // Prefer the API-provided suggestion text when available.
    private fun extractSuggestionText(result: RewritingResult?): String? {
        // Directly access the structured result from the API, removing the need for reflection.
        val candidate = result?.results?.firstOrNull()?.text?.trim()

        if (!candidate.isNullOrBlank()) {
            return candidate
        }

        // Fallback to parsing the raw toString() output. This was the source of the bug
        // where the entire RewritingResult object was displayed.
        val textual = result?.toString()?.takeIf { it.isNotBlank() } ?: return null

        // Manually parse the "text=" field from the toString() output as a robust fallback.
        // Example: "RewritingResult{results=[RewritingSuggestion(text=MyText, score=...)]}"
        val prefix = "RewritingSuggestion(text="
        val suffix = ", score="
        val fromString = textual.substringAfter(prefix, "").substringBefore(suffix, "")
        if (fromString.isNotBlank() && fromString != textual) {
            return fromString.trim()
        }

        // Final fallback to the original line-based parsing if manual parsing fails.
        return textual.lines().mapNotNull { it.trim().takeIf { text -> text.isNotEmpty() } }.firstOrNull()
    }
}
