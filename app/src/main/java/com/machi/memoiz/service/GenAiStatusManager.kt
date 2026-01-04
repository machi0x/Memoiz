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

    suspend fun checkAll(forceOff: GenAiFeatureStates? = null): GenAiFeatureStates = withContext(Dispatchers.IO) {
        if (!isGooglePlayServicesAvailable()) return@withContext GenAiFeatureStates()
        val forced = forceOff ?: GenAiFeatureStates()
        return@withContext try {
            val img = if (forced.imageDescription == FeatureStatus.UNAVAILABLE) FeatureStatus.UNAVAILABLE else imageDescriber.checkFeatureStatus().await()
            // promptModel.checkFeatureStatus() is not available in alpha1
            val gen = if (forced.textGeneration == FeatureStatus.UNAVAILABLE) FeatureStatus.UNAVAILABLE else FeatureStatus.AVAILABLE
            val sum = if (forced.summarization == FeatureStatus.UNAVAILABLE) FeatureStatus.UNAVAILABLE else summarizer.checkFeatureStatus().await()
            GenAiFeatureStates(img, gen, sum)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check feature status", e)
            GenAiFeatureStates()
        }
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

        val tasks = mutableListOf<ListenableFuture<Void>>()

        if (states.imageDescription == FeatureStatus.DOWNLOADABLE) {
            tasks.add(imageDescriber.downloadFeature(downloadCallback))
        }

        // promptModel.downloadFeature() is not available in alpha1

        if (states.summarization == FeatureStatus.DOWNLOADABLE) {
             tasks.add(summarizer.downloadFeature(downloadCallback))
        }

        // Await all downloads to complete.
        tasks.forEach {
            try {
                it.await()
            } catch (e: Exception) {
                Log.e(TAG, "A download task failed.", e)
            }
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
        val image = runCatching { imageDescriber.baseModelName.await() }.getOrNull()
        // promptModel.baseModelName is not available in alpha1
        val prompt = null
        val sum = runCatching { summarizer.baseModelName.await() }.getOrNull()
        Triple(image, prompt, sum)
    }
}
