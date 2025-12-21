package com.machi.memoiz.service

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Wrapper that calls ML Kit GenAI APIs to generate a short category
 * and optional sub-category from clipboard content.
 */
class MlKitCategorizer(private val context: Context) {

    private val rewritingClient by lazy {
        Rewriting.getClient(context)
    }

    // Use ML Kit GenAI client for text generation.
    private suspend fun generateText(promptText: String): String? {
        return try {
            val request = RewritingRequest.builder(promptText).build()
            val result = rewritingClient.runInference(request).await()
            result.results.firstOrNull()?.text
        } catch (e: MlKitException) {
            // The model is not downloaded yet. Or other ML Kit related error.
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
            // Build a conservative prompt that asks the model to only pick a candidate when it's a clear match.
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
     * Request the model to return English and Japanese labels as a single-line JSON object: {"en":"...","ja":"..."}
     * Returns Pair(en, ja) or null on failure.
     */
    suspend fun categorizeLocalized(content: String, sourceApp: String?): Pair<String?, String?>? {
        try {
            val shortened = if (content.length > 800) {
                summarize(content) ?: content
            } else content

            val sb = StringBuilder()
            sb.append("Provide two short category labels for the content: one in English (key 'en') and one in Japanese (key 'ja').")
            if (!sourceApp.isNullOrBlank()) {
                sb.append(" Source app: $sourceApp.")
            }
            sb.append("\nReply with a single-line JSON object exactly like: {\"en\":\"Work\", \"ja\":\"仕事\"}. Do not add any explanation.\nContent:\n")
            sb.append(shortened.take(2000))

            val promptText = sb.toString()
            val generated = generateText(promptText)?.trim() ?: return null

            // Try to extract JSON substring if model added extra text
            val jsonString = try {
                val start = generated.indexOf('{')
                val end = generated.lastIndexOf('}')
                if (start >= 0 && end > start) generated.substring(start, end + 1) else generated
            } catch (_: Exception) {
                generated
            }

            return try {
                val obj = JSONObject(jsonString)
                val enRaw = if (obj.has("en")) obj.optString("en", "") else ""
                val jaRaw = if (obj.has("ja")) obj.optString("ja", "") else ""
                val en = enRaw.trim().ifEmpty { null }
                val ja = jaRaw.trim().ifEmpty { null }
                Pair(en, ja)
            } catch (e: Exception) {
                e.printStackTrace()
                // fallback: try to parse crude "en|ja" by splitting on newline or '/'
                val parts = generated.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    Pair(parts[0], parts[1])
                } else Pair(generated, null)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Conservative matcher: given original content and AI's English/Ja suggestions, pick one existing candidate only if it clearly matches.
     * Otherwise return null.
     */
    suspend fun matchCategoryToList(contentExample: String, suggestionEn: String?, suggestionJa: String?, candidates: List<String>): String? {
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
}
