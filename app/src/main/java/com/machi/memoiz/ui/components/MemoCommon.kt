package com.machi.memoiz.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.machi.memoiz.R
import com.machi.memoiz.data.datastore.UiDisplayMode
import com.machi.memoiz.ui.theme.Yomogi

@Composable
fun CampusNoteTextAligned(
    text: String,
    modifier: Modifier = Modifier,
    lineHeight: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    val density = LocalDensity.current
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val shape = RoundedCornerShape(12.dp)
    val bgColor = colorResource(id = R.color.campus_note_bg)
    val textColor = colorResource(id = R.color.campus_note_text)
    val verticalLineColor = colorResource(id = R.color.campus_note_line_vertical)
    val horizontalLineColor = colorResource(id = R.color.campus_note_line_horizontal)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = lineHeight, fontFamily = Yomogi),
        color = textColor,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .drawBehind {
                drawRect(bgColor)
                val marginX = 28.dp.toPx()
                drawLine(
                    color = verticalLineColor,
                    start = Offset(marginX, 0f),
                    end = Offset(marginX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                val lineOffset = 2.dp.toPx()
                var y = lineHeightPx - lineOffset
                while (y < size.height) {
                    drawLine(
                        color = horizontalLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += lineHeightPx
                }
            }
            .padding(start = 44.dp, top = 2.dp, end = 16.dp, bottom = 16.dp))
}

@Composable
fun ChromeStyleUrlBar(
    url: String,
    title: String? = null,
    modifier: Modifier = Modifier,
    appUiDisplayMode: UiDisplayMode? = null
) {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val effectiveConfig = remember(configuration, appUiDisplayMode) {
        if (appUiDisplayMode == null || appUiDisplayMode == UiDisplayMode.SYSTEM) {
            configuration
        } else {
            val copy = android.content.res.Configuration(configuration)
            copy.uiMode = copy.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()
            copy.uiMode = when (appUiDisplayMode) {
                UiDisplayMode.DARK -> copy.uiMode or android.content.res.Configuration.UI_MODE_NIGHT_YES
                UiDisplayMode.LIGHT -> copy.uiMode or android.content.res.Configuration.UI_MODE_NIGHT_NO
                else -> copy.uiMode
            }
            copy
        }
    }
    val themedCtx = remember(effectiveConfig) { ctx.createConfigurationContext(effectiveConfig) }
    val bgColor = Color(themedCtx.resources.getColor(R.color.chrome_omnibox_bg, themedCtx.theme))
    val borderColor = Color(themedCtx.resources.getColor(R.color.chrome_omnibox_border, themedCtx.theme))
    val textColor = Color(themedCtx.resources.getColor(R.color.chrome_omnibox_text, themedCtx.theme))

    Surface(
        modifier = modifier
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(26.dp),
        color = bgColor,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        val hasTitle = !title.isNullOrBlank()
        if (hasTitle) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "URL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Column(
                    modifier = Modifier.widthIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title!!,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp),
                    tint = Color(0xFFFFD700)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "URL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp),
                    tint = Color(0xFFFFD700)
                )
            }
        }
    }
}
