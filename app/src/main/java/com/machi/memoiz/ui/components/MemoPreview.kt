package com.machi.memoiz.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.machi.memoiz.data.entity.MemoType
import com.machi.memoiz.domain.model.Memo
import androidx.compose.ui.draw.clip
import com.machi.memoiz.data.datastore.UiDisplayMode

@Composable
fun MemoPreview(
    memo: Memo, 
    modifier: Modifier = Modifier,
    appUiDisplayMode: UiDisplayMode? = null
) {
    Column(
        modifier = modifier.wrapContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (memo.memoType) {
            MemoType.IMAGE -> {
                if (!memo.imageUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(memo.imageUri)),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            MemoType.WEB_SITE -> {
                val webTitle = memo.summary?.substringBefore("\n")?.trim()?.takeIf { it.isNotBlank() }
                ChromeStyleUrlBar(
                    url = memo.content,
                    title = webTitle,
                    appUiDisplayMode = appUiDisplayMode
                )
            }
            else -> {
                CampusNoteTextAligned(
                    text = memo.content,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }
        }
    }
}
