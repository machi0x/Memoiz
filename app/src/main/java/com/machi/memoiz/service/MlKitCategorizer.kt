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
        return if (Locale.getDefault().language == "ja") "Japanese" else "English"
    }
    
    private fun getSummarizerLanguage(): Int {
        return if (Locale.getDefault().language == "ja") {
            SummarizerOptions.Language.JAPANESE
        } else {
            SummarizerOptions.Language.ENGLISH
        }
    }

    private suspend fun localizeText(text: String?): String? {
        if (text.isNullOrBlank()) return text
        return if (Locale.getDefault().language == "ja") {
            runCatching {
                generateText(
                    "Translate the following text to Japanese. Provide only the translated text without extra words.\nText: ${text.take(500)}"
                ) ?: text
            }.getOrDefault(text)
        } else text
    }

    private suspend fun localizeDescription(description: String?): String? = localizeText(description)

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
            val localizedCategory = localizeText(category)
            val subCategory = generateText(buildSubCategoryPrompt(content, localizedCategory ?: "", sourceApp))
            val localizedSubCategory = localizeText(subCategory)
            Triple(localizedCategory, localizedSubCategory, summary)
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
            val localizedDescription = localizeDescription(description)
            if (!localizedDescription.isNullOrBlank()) {
                val category = generateText(buildCategorizationPrompt(localizedDescription, sourceApp))
                val localizedCategory = localizeText(category)
                val subCategory = generateText(buildSubCategoryPrompt(localizedDescription, localizedCategory ?: "", sourceApp))
                val localizedSubCategory = localizeText(subCategory)
                Triple(localizedCategory, localizedSubCategory, localizedDescription)
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
            val request = ImageDescriptionRequest.builder(bitmap).build()
             val result = imageDescriber
                 .runInference(request)
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
        sb.append("Suggest a broad category (1-3 common words) for the following content.\n")
        sb.append("Think of a general shelf label (e.g., '動物の画像', '金融ニュース', '生活のヒント') rather than a specific proper noun.\n")
        sb.append("Reply in ${getSystemLanguageName()} language.\n")
        if (!sourceApp.isNullOrBlank()) {
            sb.append("Source app: $sourceApp\n")
        }
        sb.append("Content:\n")
        sb.append(content.take(2000))
        sb.append("\n\nReply with only the broad category name, no explanation.")
        return sb.toString()
    }

    private fun buildSubCategoryPrompt(content: String, mainCategory: String, sourceApp: String?): String {
        val sb = StringBuilder()
        sb.append("Provide a specific sub-category (1-4 words) that narrows down the main category.\n")
        sb.append("Use the same language (${getSystemLanguageName()}) and include concrete nouns or short phrases relevant to the content.\n")
        sb.append("Main category: $mainCategory\n")
        if (!sourceApp.isNullOrBlank()) {
            sb.append("Source app: $sourceApp\n")
        }
        sb.append("Content:\n")
        sb.append(content.take(2000))
        sb.append("\n\nReply with only the sub-category text, no explanation.")
        return sb.toString()
    }
}
