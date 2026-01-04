package com.machi.memoiz.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.machi.memoiz.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

data class GenAiFeatureStates(
    val imageDescription: Int = FeatureStatus.UNAVAILABLE,
    val textGeneration: Int = FeatureStatus.UNAVAILABLE,
    val summarization: Int = FeatureStatus.UNAVAILABLE
) {
    fun anyDownloadable() = imageDescription == FeatureStatus.DOWNLOADABLE ||
        textGeneration == FeatureStatus.DOWNLOADABLE ||
        summarization == FeatureStatus.DOWNLOADABLE

    fun anyUnavailable() = imageDescription == FeatureStatus.UNAVAILABLE ||
        textGeneration == FeatureStatus.UNAVAILABLE ||
        summarization == FeatureStatus.UNAVAILABLE
}

class GenAiStatusManager(private val context: Context) {
    companion object {
        const val TAG = "GenAiStatusManager"
        const val EXTRA_FORCE_OFF_IMAGE = "force_off_image"
        const val EXTRA_FORCE_OFF_TEXT = "force_off_text"
        const val EXTRA_FORCE_OFF_SUM = "force_off_sum"
    }

    private val imageDescriber: ImageDescriber by lazy {
        ImageDescription.getClient(ImageDescriberOptions.builder(context).build())
    }

    private val summarizer: Summarizer by lazy {
        Summarization.getClient(
            SummarizerOptions.builder(context)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.ONE_BULLET)
                .build()
        )
    }

    private val promptModel by lazy {
        Generation.getClient(GenerationConfig.Builder().build())
    }

    // Track current download futures so we can cancel them from UI
    private val ongoingDownloads = mutableListOf<ListenableFuture<Void>>()

    // Helper to pretty-print FeatureStatus ints
    private fun statusName(@FeatureStatus s: Int): String = when (s) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "UNKNOWN($s)"
    }

    suspend fun checkAll(forceOff: GenAiFeatureStates? = null): GenAiFeatureStates = withContext(Dispatchers.IO) {
        // Note: treat `forceOff` as nullable. Previously code used a default
        // GenAiFeatureStates() (which itself defaulted to UNAVAILABLE) when
        // forceOff was null, unintentionally disabling real status checks.
        val forced = forceOff // nullable; only non-null when caller intends to force-off

        // Log incoming forced flags for debugging (show if null)
        if (forced != null) {
            Log.d(TAG, "checkAll invoked; forced flags: image=${statusName(forced.imageDescription)} text=${statusName(forced.textGeneration)} sum=${statusName(forced.summarization)}")
        } else {
            Log.d(TAG, "checkAll invoked; no forceOff flags — performing real checks")
        }

        // If Google Play Services unavailable, return cached if present else default UNAVAILABLEs
        val gmsStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        Log.d(TAG, "GooglePlayServices availability code: $gmsStatus")
        if (gmsStatus != ConnectionResult.SUCCESS) {
            Log.w(TAG, "Google Play Services not available (code=$gmsStatus). Returning UNAVAILABLE for all features.")
            return@withContext GenAiFeatureStates()
        }

        // For each feature, try to query; on failure, fall back to cached value if any.
        // Perform per-feature checks without caching; a failure in one won't overwrite others.
        val imgState = if (forced?.imageDescription == FeatureStatus.UNAVAILABLE) {
            // Caller explicitly requested imageDescription to be forced OFF
            FeatureStatus.UNAVAILABLE
        } else {
            runCatching { imageDescriber.checkFeatureStatus().await() }
                .onFailure { Log.w(TAG, "ImageDescription.checkFeatureStatus failed", it) }
                .getOrDefault(FeatureStatus.UNAVAILABLE)
        }

        var genState = if (forced?.textGeneration == FeatureStatus.UNAVAILABLE) {
            FeatureStatus.UNAVAILABLE
        } else {
            // Use promptModel.checkStatus() if available (suspend fun returning @FeatureStatus Int).
            val got = runCatching { promptModel.checkStatus() }
                .onFailure { Log.w(TAG, "Generation.checkStatus failed", it) }
                .getOrDefault(FeatureStatus.UNAVAILABLE)
            Log.d(TAG, "promptModel.checkStatus returned: ${statusName(got)} ($got)")
            got
        }

        // WORKAROUND: Some devices/SDKs report textGeneration as DOWNLOADABLE while
        // the prompt generation API actually works. Treat DOWNLOADABLE as AVAILABLE
        // for textGeneration to avoid spurious "model download" dialogs. TODO: Investigate
        // root cause (why promptModel.checkStatus returns DOWNLOADABLE even when generateContent works)
        if (genState == FeatureStatus.DOWNLOADABLE) {
            Log.w(TAG, "Treating textGeneration DOWNLOADABLE as AVAILABLE (workaround)")
            genState = FeatureStatus.AVAILABLE
        }

        val sumState = if (forced?.summarization == FeatureStatus.UNAVAILABLE) {
            FeatureStatus.UNAVAILABLE
        } else {
            runCatching { summarizer.checkFeatureStatus().await() }
                .onFailure { Log.w(TAG, "Summarizer.checkFeatureStatus failed", it) }
                .getOrDefault(FeatureStatus.UNAVAILABLE)
        }

        val result = GenAiFeatureStates(imgState, genState, sumState)
        Log.d(TAG, "checkAll result image=${statusName(result.imageDescription)} (${result.imageDescription}) text=${statusName(result.textGeneration)} (${result.textGeneration}) sum=${statusName(result.summarization)} (${result.summarization})")
        result
    }

    suspend fun downloadMissing(states: GenAiFeatureStates) = withContext(Dispatchers.IO) {
        val downloadCallback = object : DownloadCallback {
            override fun onDownloadCompleted() {
                Log.d(TAG, "Model download completed.")
            }
            override fun onDownloadFailed(e: GenAiException) {
                Log.e(TAG, "Model download failed", e)
            }
            override fun onDownloadStarted(bytesToDownload: Long) {
                 Log.d(TAG, "Model download started. Size: $bytesToDownload")
            }
            override fun onDownloadProgress(totalBytesDownloaded: Long) {
                 Log.d(TAG, "Model download progress: $totalBytesDownloaded")
            }
        }

        downloadMissing(states, downloadCallback)
    }

    // New overload: allow caller to supply a DownloadCallback so UI can show progress
    suspend fun downloadMissing(states: GenAiFeatureStates, downloadCallback: DownloadCallback, onError: (Throwable) -> Unit = {} ) = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<ListenableFuture<Void>>()

        // We'll aggregate totals from multiple downloads and forward aggregated values to the caller's callback
        var aggregateTotalBytes = 0L
        val perTaskDownloaded = mutableMapOf<Int, Long>()
        val perTaskTotal = mutableMapOf<Int, Long>()
        var taskIndex = 0

        fun forwardStarted(total: Long) {
            // Forward aggregated total
            downloadCallback.onDownloadStarted(total)
        }

        fun forwardProgress(aggregateDownloaded: Long) {
            downloadCallback.onDownloadProgress(aggregateDownloaded)
        }

        fun forwardFailed(e: GenAiException) {
            downloadCallback.onDownloadFailed(e)
        }

        fun forwardCompleted() {
            downloadCallback.onDownloadCompleted()
        }

        // Helper to create a per-task wrapper callback that updates aggregates
        fun makeWrapperCallback(index: Int): DownloadCallback {
            return object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {
                    perTaskTotal[index] = bytesToDownload
                    aggregateTotalBytes += bytesToDownload
                    // Notify caller of aggregated total
                    forwardStarted(aggregateTotalBytes)
                }

                override fun onDownloadFailed(e: GenAiException) {
                    forwardFailed(e)
                }

                override fun onDownloadProgress(totalBytesDownloadedForTask: Long) {
                    perTaskDownloaded[index] = totalBytesDownloadedForTask
                    val aggregateDownloaded = perTaskDownloaded.values.sum()
                    forwardProgress(aggregateDownloaded)
                }

                override fun onDownloadCompleted() {
                    // Ensure this task is accounted as fully downloaded
                    perTaskDownloaded[index] = perTaskTotal[index] ?: perTaskDownloaded.getOrDefault(index, 0L)
                    val aggregateDownloaded = perTaskDownloaded.values.sum()
                    forwardProgress(aggregateDownloaded)
                    // If all tasks have completed we will call forwardCompleted when awaiting futures
                }
            }
        }

        try {
            if (states.imageDescription == FeatureStatus.DOWNLOADABLE) {
                val idx = taskIndex++
                val wrapper = makeWrapperCallback(idx)
                val f = imageDescriber.downloadFeature(wrapper)
                tasks.add(f)
            }

            // promptModel.downloadFeature() is not available in alpha1

            if (states.summarization == FeatureStatus.DOWNLOADABLE) {
                 val idx = taskIndex++
                 val wrapper = makeWrapperCallback(idx)
                 val f = summarizer.downloadFeature(wrapper)
                 tasks.add(f)
            }

            // Register ongoing downloads for potential cancellation
            synchronized(ongoingDownloads) {
                ongoingDownloads.clear()
                ongoingDownloads.addAll(tasks)
            }

            // Await all downloads to complete.
            tasks.forEach {
                try {
                    it.await()
                } catch (e: Exception) {
                    Log.e(TAG, "A download task failed.", e)
                    // If this is a GenAiException, forward it to the callback; otherwise just log and continue.
                    if (e is GenAiException) {
                        forwardFailed(e)
                    } else {
                        // Forward non-GenAiException via onError so UI can show a unified error state.
                        onError(e)
                    }
                }
            }

            // At the end forward completed
            forwardCompleted()
        } finally {
            // Clear ongoing downloads in any case
            synchronized(ongoingDownloads) {
                ongoingDownloads.clear()
            }
        }
    }

    /**
     * Cancel any in-progress model downloads started by this manager.
     * This calls cancel(true) on underlying ListenableFuture tasks.
     */
    fun cancelDownloads() {
        synchronized(ongoingDownloads) {
            ongoingDownloads.forEach { f ->
                try {
                    f.cancel(true)
                } catch (ignored: Exception) {
                    // ignore
                }
            }
            ongoingDownloads.clear()
        }
    }

    fun buildNotificationChannel(channelId: String) {
        // Min SDK is 34; notification channels are always supported.
         val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
         val channel = NotificationChannel(
             channelId,
             context.getString(R.string.genai_status_notification_title),
             NotificationManager.IMPORTANCE_LOW
         )
         manager.createNotificationChannel(channel)
     }

    fun buildUnavailableNotification(channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.genai_status_notification_title))
            .setContentText(context.getString(R.string.genai_status_notification_text))
            .setLargeIcon(null as Bitmap?)
            .setAutoCancel(true)
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val gms = GoogleApiAvailability.getInstance()
        return gms.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    fun close() {
        runCatching { imageDescriber.close() }
        runCatching { summarizer.close() }
        runCatching { promptModel.close() }
    }

    suspend fun getBaseModelNames(): Triple<String?, String?, String?> = withContext(Dispatchers.IO) {
        val image = runCatching { imageDescriber.baseModelName.await() }
            .onFailure { Log.w(TAG, "imageDescriber.baseModelName failed", it) }
            .getOrNull()

        val prompt = runCatching { promptModel.getBaseModelName() }
            .onFailure { Log.w(TAG, "promptModel.baseModelName failed", it) }
            .getOrNull()

        val sum = runCatching { summarizer.baseModelName.await() }
            .onFailure { Log.w(TAG, "summarizer.baseModelName failed", it) }
            .getOrNull()

        Triple(image, prompt, sum)
    }

    /**
     * Returns true if any feature which this manager can download is currently DOWNLOADABLE.
     * Currently promptModel (textGeneration) does not support download in this SDK, so only
     * imageDescription and summarization are considered downloadable.
     */
    fun hasDownloadableFeatures(states: GenAiFeatureStates): Boolean {
        return (states.imageDescription == FeatureStatus.DOWNLOADABLE) || (states.summarization == FeatureStatus.DOWNLOADABLE)
    }
}
