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
import com.machi.memoiz.util.FailureCategoryHelper
import java.util.Locale
import org.json.JSONObject

/**
 * Wrapper that calls ML Kit GenAI APIs to generate a short category
 * and optional sub-category from clipboard content.
 */
class MlKitCategorizer(private val context: Context) {
    private fun webCategoryLabel(): String = context.getString(R.string.category_web_site)
    private fun imageCategoryLabel(): String = context.getString(R.string.category_image)
    private fun uncategorizableLabel(): String = context.getString(R.string.category_uncategorizable)
    private fun failureCategoryLabel(): String = FailureCategoryHelper.currentLabel(context)

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
                val webContent = fetchUrlContent(content)
                if (webContent == null) {
                     return Triple(failureCategoryLabel(), null, null)
                 }
                val pair = generateCategoryPair(webContent.body.take(2000), sourceApp)
                val localizedMain = localizeText(pair.main)
                val localizedSub = localizeText(pair.sub)
                val summary = summarizeWebContent(webContent.title, webContent.body)
                if (localizedMain.isNullOrBlank()) {
                    return Triple(uncategorizableLabel(), localizedSub, summary)
                }
                return Triple(localizedMain, localizedSub, summary)
            }

            // 1-2 & 1-3: Long and short text
            val summary = if (content.length > 800) {
                summarize(content)
            } else null

            val pair = generateCategoryPair(content.take(2000), sourceApp)
            val localizedMain = localizeText(pair.main)
            val localizedSub = localizeText(pair.sub)
            if (localizedMain.isNullOrBlank()) {
                Triple(uncategorizableLabel(), localizedSub, summary)
            } else {
                Triple(localizedMain, localizedSub, summary)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Triple(failureCategoryLabel(), null, null)
        }
    }

    suspend fun categorizeImage(bitmap: Bitmap, sourceApp: String?): Triple<String?, String?, String?>? {
        return try {
            // Get image description first
            val description = describeImage(bitmap)
            
            // If we have a description, categorize based on it like text
            val localizedDescription = localizeDescription(description)
            if (!localizedDescription.isNullOrBlank()) {
                val pair = generateCategoryPair(localizedDescription.take(2000), sourceApp)
                val localizedMain = localizeText(pair.main)
                val localizedSub = localizeText(pair.sub)
                if (localizedMain.isNullOrBlank()) {
                    Triple(uncategorizableLabel(), localizedSub, localizedDescription)
                } else {
                    Triple(localizedMain, localizedSub, localizedDescription)
                }
            } else {
                Triple(failureCategoryLabel(), null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Triple(failureCategoryLabel(), null, null)
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

    private suspend fun summarize(text: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = SummarizationRequest.builder(text).build()
            summarizer.runInference(request).await().summary
        }.onFailure { it.printStackTrace() }.getOrNull()
    }

    private suspend fun fetchUrlContent(url: String): WebPageContent? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null
            val document = Jsoup.parse(html)
            val bodyText = document.body()?.text().orEmpty()
            val titleText = document.title()?.takeIf { it.isNotBlank() }
            if (bodyText.isBlank()) null else WebPageContent(bodyText, titleText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun summarizeWebContent(title: String?, body: String): String? {
        val instruction = buildString {
            appendLine("You are summarizing a web page. Reply in ${getSystemLanguageName()} language.")
            appendLine("If a page title is provided, output the title verbatim on the first line, then provide a concise 1-2 sentence summary on the following line(s).")
            appendLine("Keep the tone neutral and informative.")
            if (!title.isNullOrBlank()) {
                appendLine("Page title: $title")
            }
            appendLine("Page content:")
            append(body.take(3000))
        }
        return generateText(instruction)
    }

    private fun buildCategorizationPrompt(content: String, sourceApp: String?) = buildUnifiedPrompt(content, sourceApp)

    private fun buildSubCategoryPrompt(content: String, mainCategory: String, sourceApp: String?) = buildUnifiedPrompt(content, sourceApp)

    private fun buildUnifiedPrompt(content: String, sourceApp: String?): String {
        val sb = StringBuilder()
        sb.append("You are a helpful assistant that classifies content into categories.\n")
        sb.append("For the following content, provide a broad category (1 word in most case; Up to 2 words) and a specific sub-category (1-4 words).\n")
        sb.append("Sub category must be distinct from its parent category and provide more specific detail.")
        sb.append("Reply in ${getSystemLanguageName()} language.\n")
        if (!sourceApp.isNullOrBlank()) {
            sb.append("Source app: $sourceApp\n")
        }
        sb.append("Content:\n")
        sb.append(content.take(2000))
        sb.append("\n\nReply with only the category and sub-category separated by a slash, no explanation.")
        return sb.toString()
    }

    private fun parseCategoryPair(response: String?): CategoryPair? {
        if (response.isNullOrBlank()) return null
        val sanitized = response.trim()
        if (sanitized.contains("/")) {
            val parts = sanitized.split("/")
            return CategoryPair(parts.getOrNull(0)?.trim(), parts.getOrNull(1)?.trim())
        }

        var main: String? = null
        var sub: String? = null
        val lines = sanitized.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            val colonIndex = line.indexOf(":")
            if (colonIndex >= 0) {
                val key = line.substring(0, colonIndex).trim().lowercase(Locale.getDefault())
                val value = line.substring(colonIndex + 1).trim().ifEmpty { null }
                when {
                    key.contains("sub") || key.contains("detail") || key.contains("副") -> sub = value
                    key.contains("category") || key.contains("main") || key.contains("親") -> main = value
                }
            }
        }

        if (main == null && lines.isNotEmpty()) {
            main = lines[0]
            sub = lines.getOrNull(1)
        }

        return if (main != null || sub != null) CategoryPair(main, sub) else null
    }

    private suspend fun generateCategoryPair(content: String, sourceApp: String?): CategoryPair {
        val instructions = buildUnifiedPrompt(content, sourceApp)
        val response = generateText(instructions)
        val pair = parseCategoryPair(response)
        return pair ?: CategoryPair(null, null)
    }

    private data class CategoryPair(val main: String?, val sub: String?)

    private data class WebPageContent(val body: String, val title: String?)
 }
