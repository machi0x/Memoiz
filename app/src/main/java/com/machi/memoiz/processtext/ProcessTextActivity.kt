package com.machi.memoiz.processtext

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.machi.memoiz.R
import com.machi.memoiz.data.Memo
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.service.MlKitCategorizer
import com.machi.memoiz.service.determineSourceApp
import kotlinx.coroutines.launch

class ProcessTextActivity : ComponentActivity() {

    private lateinit var categorizer: MlKitCategorizer
    private lateinit var db: MemoizDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categorizer = MlKitCategorizer(this)
        db = MemoizDatabase.getDatabase(this)

        lifecycleScope.launch {
            val handled = when (intent?.action) {
                Intent.ACTION_PROCESS_TEXT -> handleProcessText()
                Intent.ACTION_SEND -> handleSend(intent)
                else -> false
            }

            if (!handled) {
                Toast.makeText(this@ProcessTextActivity, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ProcessTextActivity, R.string.categorization_started, Toast.LENGTH_SHORT).show()
            }

            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private suspend fun handleProcessText(): Boolean {
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (text.isNullOrBlank()) return false

        val sourceApp = determineSourceApp(this)
        val (category, subCategory) = categorizer.categorize(text, sourceApp) ?: return false

        val memo = Memo(
            content = text,
            category = category ?: "Uncategorized",
            subCategory = subCategory,
            sourceApp = sourceApp
        )
        db.memoDao().insert(memo)
        return true
    }

    private suspend fun handleSend(sendIntent: Intent): Boolean {
        val text = sendIntent.getStringExtra(Intent.EXTRA_TEXT)
        val streamUri = sendIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val sourceApp = determineSourceApp(this)

        return if (streamUri != null && sendIntent.type?.startsWith("image/") == true) {
            val bitmap = getBitmapFromUri(streamUri) ?: return false
            val (category, subCategory) = categorizer.categorizeImage(bitmap, sourceApp) ?: return false
            val memo = Memo(
                content = text ?: "",
                imageUri = streamUri.toString(),
                category = category ?: "Image",
                subCategory = subCategory,
                sourceApp = sourceApp
            )
            db.memoDao().insert(memo)
            true
        } else if (!text.isNullOrBlank()) {
            val (category, subCategory) = categorizer.categorize(text, sourceApp) ?: return false
            val memo = Memo(
                content = text,
                category = category ?: "Uncategorized",
                subCategory = subCategory,
                sourceApp = sourceApp
            )
            db.memoDao().insert(memo)
            true
        } else {
            false
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(this.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        categorizer.close()
    }
}
