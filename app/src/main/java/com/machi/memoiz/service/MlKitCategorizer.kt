package com.machi.memoiz.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.common.MlKitException
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
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.ImagePart

/**
 * Wrapper that calls ML Kit GenAI APIs to generate a short category
 * and optional sub-category from clipboard content.
 */
class MlKitCategorizer(private val context: Context, private val summarizationOnlyMode: Boolean = false) {
    private val TAG = "MlKitCategorizer"
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
        if (summarizationOnlyMode) return text
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
                val summaryText = summarizeWebContent(webContent.title, webContent.body)
                val localizedSummaryOnly = localizeText(summaryText)
                val combinedSummary = when {
                    !webContent.title.isNullOrBlank() && !localizedSummaryOnly.isNullOrBlank() -> "${webContent.title}\n${localizedSummaryOnly.trim()}"
                    !webContent.title.isNullOrBlank() -> webContent.title
                    else -> localizedSummaryOnly
                }
                if (summarizationOnlyMode) {
                    return Triple(null, null, combinedSummary)
                }
                val pair = generateCategoryPair(webContent.body.take(2000), sourceApp)
                Log.d(TAG, "categorize: primaryPairFromWeb body-> main=${pair.main} sub=${pair.sub}")
                val localizedMain = localizeText(pair.main)
                val localizedSub = localizeText(pair.sub)
                Log.d(TAG, "categorize: after localization main=${localizedMain} sub=${localizedSub} summaryPresent=${!combinedSummary.isNullOrBlank()}")
                if (localizedMain.isNullOrBlank()) {
                    return Triple(uncategorizableLabel(), localizedSub, combinedSummary)
                }
                return Triple(localizedMain, localizedSub, combinedSummary)
            }

            // 1-2 & 1-3: Long and short text
            val summary = if (content.length > 800) {
                summarize(content)
            } else null

            if (summarizationOnlyMode) {
                return Triple(null, null, summary)
            }

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

    suspend fun categorizeImage(bitmap: Bitmap, sourceApp: String?, imageUri: String? = null): Triple<String?, String?, String?>? {
        return try {
            // Get image description first
            val description = describeImage(bitmap, imageUri)

            val localizedDescription = localizeDescription(description)
            if (!localizedDescription.isNullOrBlank()) {
                if (summarizationOnlyMode) {
                    return Triple(null, null, localizedDescription)
                }
                val pair = generateCategoryPair(localizedDescription.take(2000), sourceApp)
                Log.d(TAG, "categorizeImage: primaryPairFromDescription main=${pair.main} sub=${pair.sub} descriptionPreview=${localizedDescription?.replace('\n',' ')?.take(200)}")
                val localizedMain = localizeText(pair.main)
                val localizedSub = localizeText(pair.sub)
                Log.d(TAG, "categorizeImage: after localization main=${localizedMain} sub=${localizedSub}")
                if (localizedMain.isNullOrBlank()) {
                    Log.d(TAG, "categorizeImage: final selection -> uncategorizable; using description as summary")
                    Triple(uncategorizableLabel(), localizedSub, localizedDescription)
                } else {
                    Log.d(TAG, "categorizeImage: final selection -> main=${localizedMain} sub=${localizedSub}")
                    Triple(localizedMain, localizedSub, localizedDescription)
                }
            } else {
                Triple(failureCategoryLabel(), null, null)
            }
        } catch (e: PermanentCategorizationException) {
            e.printStackTrace()
            val errorMessage = mapPermanentErrorMessage(e.errorCode)
            Triple(uncategorizableLabel(), null, errorMessage)
        } catch (e: Exception) {
            e.printStackTrace()
            Triple(failureCategoryLabel(), null, null)
        }
    }

    private suspend fun generateText(prompt: String): String? = withContext(Dispatchers.IO) {
        if (summarizationOnlyMode) return@withContext null
        val result = runCatching {
            val request = generateContentRequest(TextPart(prompt)) { }
            promptModel.generateContent(request).candidates.firstOrNull()?.text?.trim()
        }.onFailure { it.printStackTrace() }.getOrNull()

        // Log a truncated preview of prompt and result to verify Generation API behavior
        try {
            val promptPreview = prompt.replace('\n', ' ').take(300)
            val respPreview = result?.replace('\n', ' ')?.take(600) ?: "<null>"
            Log.d(TAG, "generateText promptPreview=\"$promptPreview\" -> respPreview=\"$respPreview\"")
        } catch (_: Exception) {
            // Swallow logging errors to avoid impacting production flow
        }

        result
    }

    // Accept an optional imageUri so we can attempt ImagePart(uri) fallback when available.
    private suspend fun describeImage(bitmap: Bitmap, imageUri: String? = null): String? = withContext(Dispatchers.IO) {
        var workingBitmap = bitmap
        // If the passed bitmap has been recycled, avoid accessing its properties and try to recover from URI.
        if (workingBitmap.isRecycled) {
            Log.w(TAG, "describeImage: input bitmap is already recycled; attempting to reload from imageUri")
            val reloaded = runCatching { loadBitmapFromUri(imageUri) }.getOrNull()
            if (reloaded != null) {
                workingBitmap = reloaded
                Log.d(TAG, "describeImage: reloaded bitmap from uri, size=${workingBitmap.width}x${workingBitmap.height}")
            } else {
                Log.w(TAG, "describeImage: unable to reload bitmap from uri; continuing with recycled bitmap may fail")
            }
        }
        Log.d(TAG, "describeImage: start (hasUri=${!imageUri.isNullOrBlank()}) width=${if (!workingBitmap.isRecycled) workingBitmap.width else -1} height=${if (!workingBitmap.isRecycled) workingBitmap.height else -1}")
        // Create a safe copy used for all ML Kit calls so that the original may be recycled elsewhere.
        var safeCopy: Bitmap? = null
        try {
            safeCopy = try {
                workingBitmap.copy(workingBitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (copyEx: Exception) {
                Log.w(TAG, "describeImage: initial bitmap.copy failed: ${copyEx.message}")
                null
            }

            val bitmapForRequests = safeCopy ?: workingBitmap

            try {
                Log.d(TAG, "describeImage: calling ImageDescriber.runInference")
                val request = ImageDescriptionRequest.builder(bitmapForRequests).build()
                val result = imageDescriber.runInference(request).await()
                val desc = result.description
                Log.d(TAG, "describeImage: imageDescriber returned description present=${!desc.isNullOrBlank()}")
                return@withContext desc
            } catch (e: GenAiException) {
                // Direct GenAiException (not wrapped)
                val ge = e
                Log.w(TAG, "describeImage: GenAiException code=${ge.errorCode} message=${ge.message}")

                // Try centralized fallback; if it returns a non-null text, use it
                val fallbackText = tryGenerationFallback(bitmapForRequests, imageUri)
                if (!fallbackText.isNullOrBlank()) return@withContext fallbackText

                // Fallback didn't produce result: treat as permanent if appropriate
                if (isPermanentGenAiError(ge.errorCode)) {
                    Log.w(TAG, "describeImage: permanent genai error (code=${ge.errorCode}), throwing PermanentCategorizationException")
                    throw PermanentCategorizationException(ge.errorCode)
                }

                Log.d(TAG, "describeImage: transient GenAiException, returning null")
                return@withContext null
            } catch (me: MlKitException) {
                // Some ML Kit GenAI errors are wrapped in MlKitException.cause as GenAiException.
                val ge = me.cause as? GenAiException
                if (ge != null) {
                    Log.w(TAG, "describeImage: GenAiException code=${ge.errorCode} message=${ge.message}")

                    val fallbackText = tryGenerationFallback(bitmapForRequests, imageUri)
                    if (!fallbackText.isNullOrBlank()) return@withContext fallbackText

                    if (isPermanentGenAiError(ge.errorCode)) {
                        Log.w(TAG, "describeImage: permanent genai error (code=${ge.errorCode}), throwing PermanentCategorizationException")
                        throw PermanentCategorizationException(ge.errorCode)
                    }

                    Log.d(TAG, "describeImage: transient GenAiException, returning null")
                    return@withContext null
                }

                // If MlKitException but not GenAiException cause, log and return null
                Log.w(TAG, "describeImage: MlKitException (non-GenAI): ${me.message}")
                me.printStackTrace()
                return@withContext null
            } catch (e: Exception) {
                // Some implementations may surface GenAI errors with different wrapping; detect ErrorCode 11 in messages
                val msg = (e.message ?: e.toString()).takeIf { it.isNotBlank() } ?: ""
                if (msg.contains("ErrorCode 11") || msg.contains("RESPONSE_PROCESSING_ERROR")) {
                    Log.w(TAG, "describeImage: detected RESPONSE_PROCESSING_ERROR in exception message -> attempting Generation fallback")
                    val fallbackText = tryGenerationFallback(bitmapForRequests, imageUri)
                    if (!fallbackText.isNullOrBlank()) return@withContext fallbackText
                    Log.w(TAG, "describeImage: fallback failed after detected ErrorCode 11, treating as permanent failure (code=11)")
                    throw PermanentCategorizationException(GenAiException.ErrorCode.RESPONSE_PROCESSING_ERROR)
                }

                Log.w(TAG, "describeImage: unexpected exception: ${e.message}")
                e.printStackTrace()
                return@withContext null
            }
        } finally {
            try {
                if (safeCopy != null && !safeCopy.isRecycled) safeCopy.recycle()
            } catch (_: Exception) {}
        }
     }

    // Custom exception to mark permanent categorization failures (use Int errorCode from GenAiException)
    private class PermanentCategorizationException(val errorCode: Int) : Exception("Permanent error: $errorCode")

    private fun isPermanentGenAiError(errorCode: Int): Boolean = when (errorCode) {
        GenAiException.ErrorCode.RESPONSE_PROCESSING_ERROR,
        GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR,
        GenAiException.ErrorCode.REQUEST_TOO_SMALL,
        GenAiException.ErrorCode.REQUEST_TOO_LARGE,
        GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR,
        GenAiException.ErrorCode.INVALID_INPUT_IMAGE -> true
        else -> false
    }

    private fun mapPermanentErrorMessage(errorCode: Int): String {
        val additional = when (errorCode) {
            GenAiException.ErrorCode.RESPONSE_PROCESSING_ERROR -> context.getString(R.string.error_image_additional_response_processing)
            GenAiException.ErrorCode.REQUEST_TOO_SMALL -> context.getString(R.string.error_image_additional_request_too_small)
            GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR -> context.getString(R.string.error_image_additional_response_generation)
            GenAiException.ErrorCode.REQUEST_TOO_LARGE -> context.getString(R.string.error_image_additional_request_too_large)
            GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR -> context.getString(R.string.error_image_additional_request_processing)
            GenAiException.ErrorCode.INVALID_INPUT_IMAGE -> context.getString(R.string.error_image_additional_invalid_input_image)
            else -> ""
        }
        val codeText = errorCode.toString()
        return if (additional.isNotBlank()) {
            context.getString(R.string.error_image_analysis_permanent_with_additional_code, additional, codeText)
        } else {
            context.getString(R.string.error_image_analysis_permanent_with_code, codeText)
        }
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
            response.close()
            val document = Jsoup.parse(html)
            val bodyText = document.body().text().orEmpty()
            val titleText = document.title().takeIf { it.isNotBlank() }
            val contentText = if (bodyText.isNotBlank()) bodyText else titleText
            if (contentText.isNullOrBlank()) null else WebPageContent(contentText, titleText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun summarizeWebContent(title: String?, body: String): String? {
         val instruction = buildString {
             appendLine("You are summarizing a web page. Reply in ${getSystemLanguageName()} language.")
             appendLine("Keep the tone neutral and informative.")
             appendLine("Do not generate section headings or titles; respond with 1-2 concise sentences only.")
             if (!title.isNullOrBlank()) {
                 appendLine("Page title (context only, do NOT restate): $title")
             }
             appendLine("Page content:")
             append(body.take(3000))
         }
         return generateText(instruction)
     }

    // Attempt Generation API fallback: 1) ImagePart(bitmap) 2) ImagePart(uri) (prepared via ImageUriManager).
    private suspend fun tryGenerationFallback(bitmap: Bitmap, imageUri: String?): String? = withContext(Dispatchers.IO) {
         val safePrompt = "Describe the image in English in one concise sentence using only non-identifying details. Do NOT mention faces, identities, ages, gender, or any sensitive attributes."

         // 1) ImagePart(bitmap)
        Log.d(TAG, "describeImage: trying Generation fallback with ImagePart(bitmap)")
        try {
            // Defensive copy: the incoming bitmap may be recycled elsewhere; create a safe copy for the SDK.
            val safeBitmap = try {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (copyEx: Exception) {
                Log.w(TAG, "describeImage: bitmap.copy failed: ${copyEx.message}")
                null
            }

            if (safeBitmap != null) {
                try {
                    val genRequestBitmap = generateContentRequest(ImagePart(safeBitmap), TextPart(safePrompt)) {
                        temperature = 0.0f
                        candidateCount = 1
                        maxOutputTokens = 200
                    }
                    val genResultBitmap = runCatching { promptModel.generateContent(genRequestBitmap) }.getOrNull()
                    val textBitmap = genResultBitmap?.candidates?.firstOrNull()?.text?.trim()
                    Log.d(TAG, "describeImage: Generation (bitmap) result present=${!textBitmap.isNullOrBlank()}")
                    if (!textBitmap.isNullOrBlank()) {
                        Log.i(TAG, "describeImage: Fallback succeeded (bitmap) -> ${textBitmap}")
                        return@withContext textBitmap
                    }
                } catch (inner: Exception) {
                    Log.w(TAG, "describeImage: Generation (bitmap) threw: ${inner.message}")
                    inner.printStackTrace()
                } finally {
                    // Ensure we recycle our copy if created to avoid leak
                    try { if (!safeBitmap.isRecycled) safeBitmap.recycle() } catch (_: Exception) {}
                }
            } else {
                Log.w(TAG, "describeImage: skipping bitmap fallback because safe copy could not be created")
            }
        } catch (innerAll: Exception) {
            Log.w(TAG, "describeImage: Generation (bitmap) outer threw: ${innerAll.message}")
            innerAll.printStackTrace()
        }

         // 2) ImagePart(uri) if available
         if (imageUri.isNullOrBlank()) {
             Log.d(TAG, "describeImage: no imageUri provided, skipping uri fallback")
             return@withContext null
         }

         Log.d(TAG, "describeImage: trying Generation fallback with ImagePart(uri) uri=$imageUri")
         try {
             val parsedUri = android.net.Uri.parse(imageUri)
             val prepared = ImageUriManager.prepareUriForWork(context, parsedUri, forceCopy = true)
             if (prepared == null) {
                 Log.w(TAG, "describeImage: prepared URI is null; skipping uri fallback to avoid unreadable URI")
                 return@withContext null
             }

             val uriToUse = prepared
             val mlkitPackage = "com.google.android.gms"
             var granted = false
             if (uriToUse.scheme == "content") {
                 try {
                     context.grantUriPermission(mlkitPackage, uriToUse, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                     granted = true
                 } catch (gpEx: Exception) {
                     Log.w(TAG, "describeImage: grantUriPermission failed: ${gpEx.message}")
                     gpEx.printStackTrace()
                 }
             }

             try {
                // Try direct ImagePart(uri). If it fails (e.g., FileNotFound), attempt to copy into cache and use FileProvider URI.
                try {
                    // Instead of passing content:// to ML Kit (which may run in another process), try to open and read bytes here and use ImagePart(bytes).
                    val directBytes = runCatching { context.contentResolver.openInputStream(uriToUse)?.use { it.readBytes() } }.getOrNull()
                    if (directBytes != null && directBytes.isNotEmpty()) {
                        Log.d(TAG, "describeImage: read bytes from uriToUse size=${directBytes.size}")
                        val genRequestUriBytes = generateContentRequest(ImagePart(directBytes), TextPart(safePrompt)) {
                             temperature = 0.0f
                             candidateCount = 1
                             maxOutputTokens = 200
                         }
                        val genResultUriBytes = runCatching { promptModel.generateContent(genRequestUriBytes) }.getOrNull()
                        val textUriBytes = genResultUriBytes?.candidates?.firstOrNull()?.text?.trim()
                        Log.d(TAG, "describeImage: Generation (uriBytes) result present=${!textUriBytes.isNullOrBlank()}")
                        if (!textUriBytes.isNullOrBlank()) {
                            Log.i(TAG, "describeImage: Fallback succeeded (uriBytes) -> ${textUriBytes}")
                            return@withContext textUriBytes
                        }
                    } else {
                         Log.w(TAG, "describeImage: could not read bytes from uriToUse (may be inaccessible)")
                         throw java.io.FileNotFoundException("Can't open content uri or read bytes")
                     }
                } catch (fnf: java.io.FileNotFoundException) {
                    Log.w(TAG, "describeImage: ImagePart(uri) read failed -> will try copying to cache and retry: ${fnf.message}")
                    fnf.printStackTrace()

                     // Attempt to copy original parsedUri (not necessarily uriToUse) to cache and retry
                     val fallbackFile = runCatching { copyUriToCacheFile(parsedUri) }.getOrNull()
                     if (fallbackFile != null) {
                         try {
                             Log.d(TAG, "describeImage: copied fallback file created at=${fallbackFile.absolutePath} size=${fallbackFile.length()}")
                             val bytes = try {
                                 val b = fallbackFile.readBytes()
                                 Log.d(TAG, "describeImage: copied file read bytes=${b.size}")
                                 b
                            } catch (readEx: Exception) {
                                Log.w(TAG, "describeImage: reading copied file failed: ${readEx.message}")
                                readEx.printStackTrace()
                                null
                            }

                            if (bytes != null && bytes.isNotEmpty()) {
                                 val genRequestFileBytes = generateContentRequest(ImagePart(bytes), TextPart(safePrompt)) {
                                     temperature = 0.0f
                                     candidateCount = 1
                                     maxOutputTokens = 200
                                 }
                                 val genResultFileBytes = runCatching { promptModel.generateContent(genRequestFileBytes) }.getOrNull()
                                 val textFileBytes = genResultFileBytes?.candidates?.firstOrNull()?.text?.trim()
                                 Log.d(TAG, "describeImage: Generation (fileBytes) result present=${!textFileBytes.isNullOrBlank()}")
                                 if (!textFileBytes.isNullOrBlank()) {
                                     Log.i(TAG, "describeImage: Fallback succeeded (fileBytes) -> ${textFileBytes}")
                                     return@withContext textFileBytes
                                 }
                             } else {
                                 Log.w(TAG, "describeImage: copied file read produced empty bytes")
                             }
                        } catch (retryEx: Exception) {
                            Log.w(TAG, "describeImage: retry with copied file (bytes) failed: ${retryEx.message}")
                            retryEx.printStackTrace()
                        } finally {
                            try { fallbackFile.delete() } catch (_: Exception) {}
                        }
                    } else {
                        Log.w(TAG, "describeImage: copying to cache failed or returned null")
                    }
                }
             } finally {
                 if (granted) {
                     try {
                         context.revokeUriPermission(uriToUse, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                     } catch (rvEx: Exception) {
                         Log.w(TAG, "describeImage: revokeUriPermission failed: ${rvEx.message}")
                         rvEx.printStackTrace()
                     }
                 }
             }
         } catch (uriEx: Exception) {
             Log.w(TAG, "describeImage: Generation (uri) threw: ${uriEx.message}")
             uriEx.printStackTrace()
         } catch (_: Exception) {
             Log.w(TAG, "describeImage: Generation (uri) threw (ignored)")
         }

         return@withContext null
     }

    // Load a bitmap from a content Uri string, with simple downsampling to avoid OOM.
     private fun loadBitmapFromUri(imageUri: String?): Bitmap? {
         if (imageUri.isNullOrBlank()) return null
         return try {
             val uri = android.net.Uri.parse(imageUri)
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                 val src = ImageDecoder.createSource(context.contentResolver, uri)
                 ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                     // Optional: set target size/config here if desired
                 }
             } else {
                 context.contentResolver.openInputStream(uri)?.use { stream ->
                     BitmapFactory.decodeStream(stream)
                 }
             }
         } catch (_: Exception) {
             Log.w(TAG, "loadBitmapFromUri failed")
             null
         }
     }

    // Simple data holders and prompt builders used by categorization functions
    private data class WebPageContent(val body: String, val title: String?)

    private data class CategoryPair(val main: String?, val sub: String?)

    private fun buildUnifiedPrompt(content: String, sourceApp: String?): String {
        val sb = StringBuilder()
        sb.append("You are a helpful assistant that classifies content into categories.\n")
        sb.append("For the following content, provide a broad category (1 word in most case; Up to 2 words) and a specific sub-category (1-4 words).\n")
        sb.append("Sub category must be distinct from its parent category and provide more specific detail.\n")
        sb.append("Reply in ${getSystemLanguageName()} language.\n")
        if (!sourceApp.isNullOrBlank()) sb.append("Source app: $sourceApp\n")
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

    // Copy input Uri into app cache and return a FileProvider content:// URI for it, or null on failure.
    private fun copyUriToCacheFile(src: android.net.Uri): java.io.File? {
        return try {
            val input = context.contentResolver.openInputStream(src) ?: return null
            val tmp = java.io.File.createTempFile("memo_img_", ".jpg", context.cacheDir)
            tmp.outputStream().use { output -> input.copyTo(output) }
            tmp
        } catch (_: Exception) {
            Log.w(TAG, "copyUriToCacheFile failed")
            null
        }
    }
}
// End of MlKitCategorizer.kt
