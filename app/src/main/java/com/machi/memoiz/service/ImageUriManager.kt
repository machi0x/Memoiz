package com.machi.memoiz.service

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Ensures inbound image URIs remain readable after the originating activity finishes
 * by either persisting the granted permission or copying the payload into app storage.
 */
object ImageUriManager {
    private const val IMAGE_DIR = "shared_images"

    fun prepareUriForWork(context: Context, uri: Uri): Uri? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT && persistPermission(context, uri)) {
            return uri
        }
        return copyToInternalStorage(context, uri)
    }

    private fun persistPermission(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.isSuccess
    }

    private fun copyToInternalStorage(context: Context, uri: Uri): Uri? {
        val resolver = context.contentResolver
        val imagesDir = File(context.filesDir, IMAGE_DIR).apply { if (!exists()) mkdirs() }
        val extension = resolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val safeExtension = extension?.takeIf { it.isNotBlank() } ?: "jpg"
        val target = File(imagesDir, "memo_${System.currentTimeMillis()}.$safeExtension")

        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to open input stream for uri: $uri")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }.getOrNull()
    }
}
