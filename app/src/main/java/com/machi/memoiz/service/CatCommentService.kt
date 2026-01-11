package com.machi.memoiz.service

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.machi.memoiz.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.machi.memoiz.domain.model.Memo

private const val TAG = "CatCommentService"

enum class Feeling {
    confused, cool, curious, difficult, happy, neutral, thoughtful, scared
}

data class CatCommentResult(
    val text: String?,
    val feeling: Feeling?
)

object CatCommentService {
    private val promptModel by lazy {
        Generation.getClient(GenerationConfig.Builder().build())
    }

    /**
     * Generate a cat comment result using a two-step prompt process:
     * 1. Generate the kitten comment text only.
     * 2. Wait 3 seconds (as requested) to ensure separation.
     * 3. Generate the feeling label based on the comment text.
     */
    suspend fun generateCatComment(context: Context, memo: Memo, localeLanguage: String = java.util.Locale.getDefault().language): CatCommentResult = withContext(Dispatchers.IO) {
        try {
            // STEP 1: Generate comment text only
            val commentPrompt = buildCommentPrompt(memo, context)
            val commentRaw = withTimeoutOrNull(10000L) {
                val request = generateContentRequest(TextPart(commentPrompt)) { }
                promptModel.generateContent(request).candidates.firstOrNull()?.text?.trim()
            }
            
            Log.d(TAG, "Step 1 (Comment) Raw: $commentRaw")
            
            if (commentRaw.isNullOrBlank()) {
                return@withContext CatCommentResult(null, Feeling.neutral)
            }

            // Clean up: collapse multiple newlines into one, then truncate
            val cleanedComment = truncateToMaxSentences(commentRaw.replace(Regex("\\n{2,}") , "\n").trim(), 3)

            // STEP 2: Explicit delay of 3 seconds as requested
            delay(3000L)

            // STEP 3: Guess feeling from the comment text
            val feelingPrompt = context.getString(R.string.catcomment_prompt_feeling, cleanedComment)
            val feelingRaw = withTimeoutOrNull(8000L) {
                val request = generateContentRequest(TextPart(feelingPrompt)) { }
                promptModel.generateContent(request).candidates.firstOrNull()?.text?.trim()?.lowercase()
            }
            
            Log.d(TAG, "Step 3 (Feeling) Raw: $feelingRaw")

            val detectedFeeling = Feeling.values().find { f -> 
                feelingRaw?.contains(f.name) == true 
            } ?: Feeling.neutral

            CatCommentResult(cleanedComment, detectedFeeling)
        } catch (e: Exception) {
            Log.w(TAG, "generateCatComment failed: ${e.message}")
            CatCommentResult(null, Feeling.neutral)
        }
    }

    private fun truncateToMaxSentences(text: String, max: Int): String {
        if (text.isBlank()) return text
        val parts = text.split(Regex("(?<=[.!?。！？])\\s+"))
        return if (parts.size <= max) text else parts.take(max).joinToString(" ").trim()
    }

    private fun buildCommentPrompt(memo: Memo, context: Context): String {
        val memoData = StringBuilder()
        memoData.append("Category: ${memo.category}\n")
        memo.subCategory?.let { memoData.append("Subcategory: $it\n") }
        when (memo.memoType) {
            com.machi.memoiz.data.entity.MemoType.IMAGE -> memoData.append("Image description: ${memo.content}\n")
            com.machi.memoiz.data.entity.MemoType.WEB_SITE -> memoData.append("URL: ${memo.content}\nSummary: ${memo.summary ?: ""}\n")
            else -> memoData.append("Text: ${memo.content}\n")
        }

        val template = context.getString(R.string.catcomment_prompt_comment)
        return String.format(template, memoData.toString())
    }
}
