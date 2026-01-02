package com.machi.memoiz.service

import android.content.Context
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.core.text.BidiFormatter

/**
 * Second stage category merging service.
 * Takes the raw AI category suggestion and asks Prompt API to reconcile it
 * against existing categories while treating user-created custom categories
 * as immutable targets (they can absorb others but are never merged away).
 */
class CategoryMergeService(private val context: Context) {

    private val promptModel by lazy {
        Generation.getClient(GenerationConfig.Builder().build())
    }

    private fun getSystemLanguageName(): String {
        return when (Locale.getDefault().language) {
            "ja" -> "Japanese"
            "en" -> "English"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            else -> "English" // fallback
        }
    }

    data class MergeInput(
        val aiCategory: String,
        val aiSubCategory: String? = null,
        val existingCategories: List<String>,
        val customCategories: Set<String> = emptySet(),
        val memoSummary: String? = null
    )

    data class MergeResult(
        val finalCategory: String,
        val mergedIntoCustom: Boolean,
        val description: String? = null
    )

    suspend fun merge(input: MergeInput): MergeResult = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(input)
        val request = generateContentRequest(TextPart(prompt)) { }
        val text = runCatching {
            promptModel.generateContent(request).candidates.firstOrNull()?.text?.trim()
        }.getOrNull()

        val raw = text?.takeIf { it.isNotBlank() }
        val sanitized = sanitizeResponse(raw)
        val finalCategory = sanitized ?: input.aiCategory
        val mergedIntoCustom = input.customCategories.contains(finalCategory)
        MergeResult(finalCategory, mergedIntoCustom, text)
    }

    fun close() {
        promptModel.close()
    }

    private fun buildPrompt(input: MergeInput): String {
        val fixed = if (input.customCategories.isNotEmpty()) {
            "These are FIXED labels created by the user and cannot be merged away: " +
                    input.customCategories.joinToString(", ")
        } else ""

        val existingPool = (input.existingCategories + input.customCategories).toSet()
        val poolText = if (existingPool.isNotEmpty()) {
            "Existing categories include: " + existingPool.joinToString(", ")
        } else ""
        val summaryText = input.memoSummary?.takeIf { it.isNotBlank() }?.let {
            "Memo summary: " + it.replace("\n", " ").take(400)
        } ?: ""

        return buildString {
            appendLine("You are a categorization assistant that helps merge similar categories intelligently.")
            appendLine("Reply in ${getSystemLanguageName()} language.")
            appendLine()
            appendLine("Original AI suggestion: \"${input.aiCategory}\"")
            input.aiSubCategory?.let { appendLine("Sub-category context: $it") }
            if (summaryText.isNotBlank()) {
                appendLine(summaryText)
            }
            appendLine()
            if (fixed.isNotBlank()) {
                appendLine(fixed)
                appendLine()
            }
            if (poolText.isNotBlank()) {
                appendLine(poolText)
                appendLine()
            }
            appendLine("Task: Decide if the suggestion should merge into an existing category.")
            appendLine()
            appendLine("Merging guidelines:")
            appendLine("1. Consider semantic relationships and broader context:")
            appendLine("   - If the suggestion describes a specific activity or subject that naturally belongs to a broader existing category, prefer the existing category.")
            appendLine("2. Use the sub-category context to understand the full meaning:")
            appendLine("   - The sub-category provides additional context about what the content actually contains.")
            appendLine("   - Consider whether this context suggests the content belongs to an existing broader category.")
            appendLine("3. Prioritize user-created categories (marked as FIXED) when semantically related.")
            appendLine("4. Only merge if there's a clear semantic relationship. When in doubt, keep the suggestion separate.")
            appendLine("5. Match exact names, synonyms, or clear parent-child relationships.")
            appendLine()
            appendLine("Decision: Return ONLY the final category name to use (either an existing category or the original suggestion). No explanations, no extra text.")
        }
    }

    private fun sanitizeResponse(raw: String?): String? {
        raw ?: return null
        val firstMeaningfulLine = raw
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: return null

        val separators = listOf(":", "：", "->", "→")
        var candidate = firstMeaningfulLine
        separators.forEach { sep ->
            if (candidate.contains(sep)) {
                candidate = candidate.substringAfter(sep).trim()
            }
        }
        candidate = candidate.trimStart('-', '•', '*').trim()
        candidate = candidate.trim('"', '“', '”').trim()

        return candidate.takeIf { it.isNotBlank() }
    }
}
