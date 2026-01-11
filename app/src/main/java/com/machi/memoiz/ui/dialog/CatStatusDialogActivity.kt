package com.machi.memoiz.ui.dialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.machi.memoiz.R
import com.machi.memoiz.BuildConfig
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.datastore.MemoizStatus
import com.machi.memoiz.ui.theme.MemoizTheme
import kotlinx.coroutines.flow.collectLatest

class CatStatusDialogActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CatStatusDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MemoizTheme {
                CatStatusDialogScreen(this)
            }
        }
    }
}

@Composable
private fun CatStatusDialogScreen(appContext: Context) {
    val mgr = remember { PreferencesDataStoreManager(appContext) }
    var status by remember { mutableStateOf(MemoizStatus()) }

    LaunchedEffect(Unit) {
        mgr.memoizStatusFlow().collectLatest { s ->
            status = s
        }
    }

    // choose image resource
    val imageRes = remember(status) {
        val threshold = if (BuildConfig.DEBUG) 1 else 15
        val vals = listOf(status.kindness, status.coolness, status.smartness, status.curiosity)
        if (vals.all { it < threshold }) {
            R.drawable.status_neutral
        } else {
            // determine max with priority kindness > coolness > smartness > curiosity
            val map = listOf("kindness" to status.kindness, "coolness" to status.coolness, "smartness" to status.smartness, "curiosity" to status.curiosity)
            val maxVal = map.maxByOrNull { it.second }?.second ?: 0
            when (map.first { it.second == maxVal }.first) {
                "kindness" -> R.drawable.status_heartful
                "coolness" -> R.drawable.status_cool
                "smartness" -> R.drawable.status_smart
                "curiosity" -> R.drawable.status_curious
                else -> R.drawable.status_neutral
            }
        }
    }

    // Dialog UI (similar style to CatCommentDialogActivity)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(3.dp, Color(0xFFFFA400)),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 520.dp)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val dotGothic = remember { FontFamily(Font(R.font.dotgothic16_regular)) }

                // Title: dotgothic, white
                Text(
                    text = stringResource(R.string.status_dialog_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = dotGothic),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Image(painter = painterResource(id = imageRes), contentDescription = null, modifier = Modifier.size(140.dp))
                Spacer(modifier = Modifier.height(12.dp))

                // Status area: white frame + black background, text in white using dotgothic
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))
                        .background(Color.Black, shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.status_exp_label, status.exp), color = Color.White, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = dotGothic))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            // First row: やさしさ と かっこよさ
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.status_param_yasashisa, status.kindness),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = dotGothic),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    textAlign = TextAlign.Start
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.status_param_kakkoyosa, status.coolness),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = dotGothic),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Second row: かしこさ と こうきしん
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.status_param_kashikosa, status.smartness),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = dotGothic),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.status_param_koukishin, status.curiosity),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = dotGothic),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Note: normal font, outside the framed box, white color
                Text(text = stringResource(R.string.status_note), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { (appContext as? android.app.Activity)?.finish() }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text(stringResource(R.string.status_close), style = MaterialTheme.typography.labelLarge.copy(fontFamily = dotGothic))
                }
            }
        }
    }
}
