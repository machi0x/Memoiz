package com.machi.memoiz.service

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import com.machi.memoiz.R
import java.util.Locale

/**
 * Wrapper that calls ML Kit GenAI APIs to generate a short category
 * and optional sub-category from clipboard content.
 */
class MlKitCategorizer(private val context: Context) {
    private fun webCategoryLabel(): String = context.getString(R.string.category_web_site)
    private fun imageCategoryLabel(): String = context.getString(R.string.category_image)
    private fun uncategorizableLabel(): String = context.getString(R.string.category_uncategorizable)
    
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
    
    private fun getSummarizerLanguage(): SummarizerOptions.Language {
        return when (Locale.getDefault().language) {
            "ja" -> SummarizerOptions.Language.JAPANESE
            "ko" -> SummarizerOptions.Language.KOREAN
            else -> SummarizerOptions.Language.ENGLISH
        }
    }

    private val promptModel: GenerativeModel by lazy {
        // Align with sample app: explicit builder so future config tweaks are easier.
        Generation.getClient(GenerationConfig.Builder().build())
    }

    private val summarizer: Summarizer by lazy {
        Summarization.getClient(
            SummarizerOptions.builder(context)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.ONE_BULLET)
                .setLanguage(getSummarizerLanguage())
                .build()
        )
    }

    private val imageDescriber: ImageDescriber by lazy {
        ImageDescription.getClient(ImageDescriberOptions.builder(context).build())
    }

    fun close() {
        // Explicitly close clients that expose closeable resources.
        summarizer.close()
        imageDescriber.close()
        promptModel.close()
    }

    suspend fun categorize(content: String, sourceApp: String?): Triple<String?, String?, String?>? {
        return try {
            // 1-1: URL
            if (content.startsWith("http")) {
                val webContent = fetchUrlContent(content) ?: return Triple(webCategoryLabel(), null, null)
                val subCategory = generateText(buildCategorizationPrompt(webContent, sourceApp))
                val summary = summarize(webContent)
                return Triple(webCategoryLabel(), subCategory, summary)
            }

            // 1-4: Garbage text
            if (content.length < 4) {
                return Triple(uncategorizableLabel(), null, null)
            }

            // 1-2 & 1-3: Long and short text
            val summary = if (content.length > 800) {
                summarize(content)
            } else null

            val category = generateText(buildCategorizationPrompt(content, sourceApp))
            val subCategory = generateText(buildSubCategoryPrompt(content, category ?: "", sourceApp))
            Triple(category, subCategory, summary)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun categorizeImage(bitmap: Bitmap, sourceApp: String?): Triple<String?, String?, String?>? {
        return try {
            // Get image description first
            val description = describeImage(bitmap)
            
            // If we have a description, categorize based on it like text
            if (!description.isNullOrBlank()) {
                val category = generateText(buildCategorizationPrompt(description, sourceApp))
                val subCategory = generateText(buildSubCategoryPrompt(description, category ?: "", sourceApp))
                Triple(category, subCategory, description)
            } else {
                // Fallback to image category if description fails
                Triple(imageCategoryLabel(), null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun generateText(prompt: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = generateContentRequest(TextPart(prompt)) { }
            promptModel.generateContent(request).candidates.firstOrNull()?.text?.trim()
        }.onFailure { it.printStackTrace() }.getOrNull()
    }

    private suspend fun describeImage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val result = imageDescriber
                .runInference(ImageDescriptionRequest.builder(bitmap).build())
                .await()
            result.description
        }.onFailure { it.printStackTrace() }.getOrNull()
    }

    suspend fun summarize(text: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = SummarizationRequest.builder(text).build()
            summarizer.runInference(request).await().summary
        }.onFailure { it.printStackTrace() }.getOrNull()
    }

    private suspend fun fetchUrlContent(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null
            Jsoup.parse(html).body().text()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildCategorizationPrompt(content: String, sourceApp: String?): String {
        val sb = StringBuilder()
        sb.append("Suggest a concise category name (1-3 words) for the following content.\n")
        sb.append("Reply in ${getSystemLanguageName()} language.\n")
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
        sb.append("Provide a very brief sub-category (1-3 words maximum) for the clipboard item.\n")
        sb.append("Reply in ${getSystemLanguageName()} language.\n")
        sb.append("Main category: $mainCategory\n")
        if (!sourceApp.isNullOrBlank()) {
            sb.append("Source app: $sourceApp\n")
        }
        sb.append("Content:\n")
        sb.append(content.take(2000))
        sb.append("\n\nReply with only 1-3 words, no explanation. Keep it extremely short and concise.")
        return sb.toString()
    }
}
