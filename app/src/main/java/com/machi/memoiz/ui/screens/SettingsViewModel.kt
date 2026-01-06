package com.machi.memoiz.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.datastore.UiDisplayMode
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.service.GenAiStatusManager
import com.machi.memoiz.service.GenAiFeatureStates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.data.entity.MemoType
import com.machi.memoiz.domain.model.Memo as DomainMemo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider

// zip4j imports
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.exception.ZipException

/**
 * Contract (interface) used by SettingsScreen so Preview can inject a Fake VM.
 */
interface SettingsScreenViewModel {
    val genAiPreferences: Flow<com.machi.memoiz.data.datastore.UserPreferences>
    val baseModelNames: StateFlow<Triple<String?, String?, String?>>
    val featureStates: StateFlow<GenAiFeatureStates?>

    // Allow SettingsScreen to request an explicit refresh of GenAI status
    fun refreshFeatureStates()

    fun requestTutorial()
    fun remergeAllMemos(context: Context)
    fun setUseImageDescription(use: Boolean)
    fun setUseTextGeneration(use: Boolean)
    fun setUseSummarization(use: Boolean)
    // New: set UI display mode
    fun setUiDisplayMode(mode: UiDisplayMode)

    // Export / Import API
    // Returns content Uri for the created ZIP file on success, or null on failure
    suspend fun exportMemos(context: Context, password: String? = null): Uri?

    // Imports memos from the given Uri (ZIP). password may be null to attempt without password.
    // If the ZIP is encrypted and password is required, return ImportResult with message = "PASSWORD_REQUIRED".
    suspend fun importMemos(uri: Uri, context: Context, password: String? = null): ImportResult
}

// Result returned by importMemos
data class ImportResult(val added: Int, val skipped: Int, val errors: Int, val message: String? = null)

/**
 * ViewModel for Settings screen.
 */
class SettingsViewModel(
    private val preferencesManager: PreferencesDataStoreManager,
    private val genAiStatusManager: GenAiStatusManager
) : ViewModel(), SettingsScreenViewModel {
    // Helper to pretty-print FeatureStatus int constants for logs
    private fun featureStatusName(@com.google.mlkit.genai.common.FeatureStatus status: Int): String {
        return when (status) {
            com.google.mlkit.genai.common.FeatureStatus.AVAILABLE -> "AVAILABLE"
            com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
            com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
            else -> "UNKNOWN($status)"
        }
    }

    private fun prettyStates(s: GenAiFeatureStates?): String {
        if (s == null) return "null"
        return "image=${featureStatusName(s.imageDescription)} text=${featureStatusName(s.textGeneration)} sum=${featureStatusName(s.summarization)}"
    }

    override val genAiPreferences = preferencesManager.userPreferencesFlow

    private val _baseModelNames = MutableStateFlow<Triple<String?, String?, String?>>(Triple(null, null, null))
    override val baseModelNames = _baseModelNames.asStateFlow()

    // Expose current feature status so the UI can show available/downloadable/unavailable
    private val _featureStates = MutableStateFlow<GenAiFeatureStates?>(null)
    override val featureStates = _featureStates.asStateFlow()

    private val _loadingFeatureStates = MutableStateFlow(false)
    // Expose _loadingFeatureStates internally only; public property was unused and caused linter warning

    init {
        viewModelScope.launch {
            _baseModelNames.value = genAiStatusManager.getBaseModelNames()
            // initial load
            refreshFeatureStates()
        }
    }

    override fun requestTutorial() {
        viewModelScope.launch { preferencesManager.requestTutorial() }
    }

    override fun remergeAllMemos(context: Context) {
        ContentProcessingLauncher.enqueueMergeWork(context, null)
    }

    // Legacy force-off setters removed. The Settings UI still exposes 'use' toggles,
    // but those preferences were removed — keep methods as no-ops to preserve runtime calls.
    override fun setUseImageDescription(use: Boolean) {
        // No-op: stored force-off preference removed. UI toggle does not persist.
        Log.d("SettingsViewModel", "setUseImageDescription called but force-off flags removed; no-op")
    }

    override fun setUseTextGeneration(use: Boolean) {
        // No-op
        Log.d("SettingsViewModel", "setUseTextGeneration called but force-off flags removed; no-op")
    }

    override fun setUseSummarization(use: Boolean) {
        // No-op
        Log.d("SettingsViewModel", "setUseSummarization called but force-off flags removed; no-op")
    }

    override fun setUiDisplayMode(mode: UiDisplayMode) {
        viewModelScope.launch {
            preferencesManager.setUiDisplayMode(mode)
        }
    }

    override fun refreshFeatureStates() {
        viewModelScope.launch {
            _loadingFeatureStates.value = true
            try {
                // Try to refresh base model names first; preserve parts that failed to load.
                val prevNames = _baseModelNames.value
                val newNames = runCatching { genAiStatusManager.getBaseModelNames() }.getOrNull()
                if (newNames != null) {
                    _baseModelNames.value = Triple(
                        newNames.first ?: prevNames.first,
                        newNames.second ?: prevNames.second,
                        newNames.third ?: prevNames.third
                    )
                }

                // Then check feature states; on failure keep previous value to avoid flipping to UNAVAILABLE.
                val prevStates = _featureStates.value
                var checked = runCatching { genAiStatusManager.checkAll() }.getOrNull()
                Log.d("SettingsViewModel", "initial checkAll returned: ${prettyStates(checked)}; prevStates=${prettyStates(prevStates)}")
                // If first check failed or returned all UNAVAILABLE and we had no previous value,
                // retry once after a short delay to avoid transient GMS/GenAI hiccups causing false UNAVAILABLE.
                val prev = prevStates
                val firstAllUnavailable = checked != null && (
                    checked.imageDescription == com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE &&
                    checked.textGeneration == com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE &&
                    checked.summarization == com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE
                )
                if ((checked == null || (firstAllUnavailable && prev == null))) {
                    try {
                        delay(500)
                        checked = runCatching { genAiStatusManager.checkAll() }.getOrNull()
                        Log.d("SettingsViewModel", "retry checkAll returned: ${prettyStates(checked)}")
                    } catch (_: Exception) {
                        // ignore retry failures
                    }
                }

                // If still null after retry, keep prevStates (avoid overwriting with null).
                if (checked == null) {
                    Log.w("SettingsViewModel", "checkAll returned null even after retry; will keep previous state if any")
                    // leave checked null so subsequent logic preserves prevStates
                }

                if (checked != null) {
                    // Use the freshly checked state directly. Inference based on
                    // model name hints can create a UI mismatch where the status
                    // appears AVAILABLE even though checkAll reports UNAVAILABLE.
                    // To ensure Settings reflects the real checked status, accept
                    // the checked result as authoritative.
                    _featureStates.value = checked
                } else {
                    // keep prevStates
                }
            } catch (_: Exception) {
                // keep previous values on unexpected errors
            } finally {
                _loadingFeatureStates.value = false
            }
        }
    }

    // Export memos to ZIP (meta.json + images). Returns content Uri for ZIP on success, or null on failure.
    override suspend fun exportMemos(context: Context, password: String?): Uri? {
        return try {
            withContext(Dispatchers.IO) {
                val db = MemoizDatabase.getDatabase(context)
                val repo = MemoRepository(db.memoDao())
                val all = repo.getAllMemosImmediate()

                // Prepare temporary directory
                val cacheDir = File(context.cacheDir, "memoiz_export")
                if (cacheDir.exists()) cacheDir.deleteRecursively()
                cacheDir.mkdirs()

                val imagesDir = File(cacheDir, "images")
                imagesDir.mkdirs()

                val memosArray = JSONArray()
                var imgIndex = 0
                for (memo in all) {
                    val m = JSONObject()
                    m.put("content", memo.content)
                    m.put("memoType", memo.memoType)
                    m.put("category", memo.category)
                    if (!memo.subCategory.isNullOrEmpty()) m.put("subCategory", memo.subCategory)
                    if (!memo.summary.isNullOrEmpty()) m.put("summary", memo.summary)
                    if (!memo.sourceApp.isNullOrEmpty()) m.put("sourceApp", memo.sourceApp)
                    m.put("createdAt", memo.createdAt)

                    if (memo.memoType == MemoType.IMAGE && !memo.imageUri.isNullOrEmpty()) {
                        // Copy image content into imagesDir
                        try {
                            val imageUri = Uri.parse(memo.imageUri)
                            val ext = File(imageUri.lastPathSegment ?: "").extension.ifEmpty { "jpg" }
                            val fname = String.format("img_%04d.%s", imgIndex++, ext)
                            val target = File(imagesDir, fname)
                            context.contentResolver.openInputStream(imageUri)?.use { input ->
                                target.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            m.put("imageFile", "images/$fname")
                        } catch (e: Exception) {
                            Log.w("SettingsViewModel", "failed to copy image for export: ${memo.imageUri}", e)
                        }
                    }

                    memosArray.put(m)
                }

                val root = JSONObject()
                root.put("version", 1)
                root.put("imagesIncluded", true)
                // Include user-defined custom categories and category order in the export metadata
                try {
                    val prefs = preferencesManager.userPreferencesFlow.first()
                    val customCats = JSONArray()
                    // Convert to list first to avoid platform-type iterator ambiguities
                    for (cat in prefs.customCategories.toList()) {
                        // Ensure we pass a String to JSONArray.put to avoid overload ambiguity
                        customCats.put(cat.toString())
                    }
                    root.put("customCategories", customCats)

                    val orderArr = JSONArray()
                    for (name in prefs.categoryOrder.toList()) {
                        orderArr.put(name.toString())
                    }
                    root.put("categoryOrder", orderArr)
                } catch (_: Exception) {
                    // If reading preferences fails, continue without categories
                }
                root.put("memos", memosArray)

                val metaFile = File(cacheDir, "meta.json")
                metaFile.writeText(root.toString())

                // Create ZIP
                val timestamp = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                val isJapanese = Locale.getDefault().language.startsWith("ja")
                val zipName = if (isJapanese) "メモイズのエクスポートデータ($timestamp).zip" else "Memoiz_export_$timestamp.zip"
                val zipFile = File(context.cacheDir, zipName)
                if (zipFile.exists()) zipFile.delete()

                if (password.isNullOrBlank()) {
                    val zf = ZipFile(zipFile)
                    zf.addFile(metaFile)
                    if (imagesDir.exists()) zf.addFolder(imagesDir)
                } else {
                    val zf = ZipFile(zipFile, password.toCharArray())
                    val params = ZipParameters()
                    params.isEncryptFiles = true
                    params.encryptionMethod = EncryptionMethod.AES
                    params.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    zf.addFile(metaFile, params)
                    if (imagesDir.exists()) zf.addFolder(imagesDir, params)
                }

                // Return FileProvider URI for the created zip file
                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                contentUri
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "exportMemos failed", e)
            null
        }
    }

    // Import memos from ZIP Uri. If the ZIP is encrypted and no password or wrong password supplied,
    // return ImportResult with message="PASSWORD_REQUIRED" so the UI can prompt the user.
    override suspend fun importMemos(uri: Uri, context: Context, password: String?): ImportResult {
        var added = 0
        var skipped = 0
        var errors = 0
        try {
            return withContext(Dispatchers.IO) {
                val db = MemoizDatabase.getDatabase(context)
                val repo = MemoRepository(db.memoDao())

                // Copy Uri to a temporary zip file
                val tempZip = File.createTempFile("memoiz_import_", ".zip", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempZip.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext ImportResult(0, 0, 1, "Cannot open input stream")

                val extractDir = File(context.cacheDir, "memoiz_import_extracted")
                if (extractDir.exists()) extractDir.deleteRecursively()
                extractDir.mkdirs()

                // Try to extract without password first
                try {
                    val zf = ZipFile(tempZip)
                    zf.extractAll(extractDir.absolutePath)
                } catch (e: ZipException) {
                    // If it's encrypted and password required, signal UI
                    Log.w("SettingsViewModel", "zip extract failed (no password): ${e.message}")
                    // If caller provided password, try it below; otherwise return special message
                    if (password.isNullOrEmpty()) {
                        return@withContext ImportResult(0, 0, 1, "PASSWORD_REQUIRED")
                    } else {
                        try {
                            val zf = ZipFile(tempZip, password.toCharArray())
                            zf.extractAll(extractDir.absolutePath)
                        } catch (e2: ZipException) {
                            Log.w("SettingsViewModel", "zip extract failed with provided password", e2)
                            return@withContext ImportResult(0, 0, 1, "Invalid password or corrupted file")
                        }
                    }
                }

                // Read meta.json
                val metaFile = File(extractDir, "meta.json")
                if (!metaFile.exists()) {
                    return@withContext ImportResult(0, 0, 1, "meta.json not found in archive")
                }

                val root = JSONObject(metaFile.readText())
                // Restore custom categories and category order if present
                try {
                    val customCats = root.optJSONArray("customCategories")
                    if (customCats != null) {
                        for (i in 0 until customCats.length()) {
                            val name = customCats.optString(i)
                            if (!name.isNullOrBlank()) {
                                try { preferencesManager.addCustomCategory(name) } catch (_: Exception) { }
                            }
                        }
                    }
                    val orderArr = root.optJSONArray("categoryOrder")
                    if (orderArr != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until orderArr.length()) {
                            val name = orderArr.optString(i)
                            if (!name.isNullOrBlank()) list.add(name)
                        }
                        if (list.isNotEmpty()) {
                            try { preferencesManager.updateCategoryOrder(list) } catch (_: Exception) { }
                        }
                    }
                } catch (_: Exception) {
                    // ignore preference restore failures
                }
                val memosArr = root.optJSONArray("memos") ?: JSONArray()
                val imagesDir = File(extractDir, "images")

                for (i in 0 until memosArr.length()) {
                    try {
                        val o = memosArr.getJSONObject(i)
                        val content = o.optString("content", "").trim()
                        if (content.isEmpty()) {
                            errors++
                            continue
                        }

                        val existing = repo.getMemoByContentImmediate(content)
                        if (existing != null) {
                            skipped++
                            continue
                        }

                        val memoType = o.optString("memoType", MemoType.TEXT)

                        var imageUriStr: String? = null
                        if (memoType == MemoType.IMAGE && o.has("imageFile") && imagesDir.exists()) {
                            val imagePath = o.optString("imageFile")
                            val extractedImage = File(extractDir, imagePath)
                            if (extractedImage.exists()) {
                                // Move/copy into app files dir and provide FileProvider URI
                                val targetDir = File(context.filesDir, "imported_images")
                                if (!targetDir.exists()) targetDir.mkdirs()
                                val target = File(targetDir, extractedImage.name)
                                extractedImage.copyTo(target, overwrite = true)
                                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
                                imageUriStr = contentUri.toString()
                            }
                        }

                        val category = o.optString("category", "")
                        val subCategory = if (o.has("subCategory")) o.optString("subCategory") else null
                        val summary = if (o.has("summary")) o.optString("summary") else null
                        val sourceApp = if (o.has("sourceApp")) o.optString("sourceApp") else null
                        val createdAt = o.optLong("createdAt", System.currentTimeMillis())

                        val newMemo = DomainMemo(
                            content = content,
                            imageUri = imageUriStr,
                            memoType = memoType,
                            category = category,
                            subCategory = subCategory,
                            summary = summary,
                            sourceApp = sourceApp,
                            createdAt = createdAt
                        )

                        repo.insertMemo(newMemo)
                        added++
                    } catch (e: Exception) {
                        Log.w("SettingsViewModel", "failed to import a memo item", e)
                        errors++
                    }
                }

                // Cleanup extracted files (keep imported copies in app filesDir)
                try { extractDir.deleteRecursively() } catch (_: Exception) {}

                ImportResult(added, skipped, errors, null)
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "importMemos failed", e)
            return ImportResult(added, skipped, errors + 1, e.message)
        }
    }
}

// Preview for Settings screen: adapt fake VM to new signatures

