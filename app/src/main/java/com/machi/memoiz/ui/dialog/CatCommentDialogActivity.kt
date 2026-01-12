package com.machi.memoiz.ui.dialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.machi.memoiz.ui.components.MemoPreview
import com.machi.memoiz.R
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.datastore.UiDisplayMode
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.CatCommentService
import com.machi.memoiz.service.Feeling
import com.machi.memoiz.ui.theme.MemoizTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class CatCommentDialogActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MEMO_ID = "extra_memo_id"

        fun start(context: Context, memoId: Long? = null) {
            context.startActivity(Intent(context, CatCommentDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (memoId != null) {
                    putExtra(EXTRA_MEMO_ID, memoId)
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val memoId = intent.getLongExtra(EXTRA_MEMO_ID, -1L).takeIf { it != -1L }
        setContent {
            MemoizTheme {
                // Pass the Activity instance so the Close button can finish() reliably
                CatCommentDialogScreen(this, memoId)
            }
        }
    }
}

@Composable
private fun CatCommentDialogScreen(appContext: Context, targetMemoId: Long?) {
    var processingMessage by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(0) } // 0=processing,1=no-memo,2=show-result,3=no-response
    var selectedMemo by remember { mutableStateOf<Memo?>(null) }
    var commentText by remember { mutableStateOf<String?>(null) }
    var feeling by remember { mutableStateOf<Feeling?>(null) }

    LaunchedEffect(Unit) {
        // select random processing message
        val arr = appContext.resources.getStringArray(R.array.catcomment_processing_array)
        processingMessage = arr.random()

        // enforce minimum display time of 5s
        val start = System.currentTimeMillis()

        // fetch memos
        try {
            val db = MemoizDatabase.getDatabase(appContext)
            val repo = MemoRepository(db.memoDao())
            
            val chosen = if (targetMemoId != null) {
                repo.getMemoById(targetMemoId)
            } else {
                val memos = repo.getAllMemosImmediate()
                if (memos.isEmpty()) null else selectWeightedMemo(memos)
            }

            if (chosen == null) {
                state = 1
            } else {
                selectedMemo = chosen
                // call CatCommentService
                val result = CatCommentService.generateCatComment(appContext, chosen)
                // sanitize the returned text: remove XML-like tags and the feeling label lines
                commentText = sanitizeCatComment(result.text, result.feeling)
                feeling = result.feeling
                if (commentText.isNullOrBlank()) {
                    state = 3
                } else {
                    state = 2
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            state = 3
        }

        val elapsed = System.currentTimeMillis() - start
        val remaining = 5000L - elapsed
        if (remaining > 0) delay(remaining)
    }

    // Once we have result (state==2), record the usage asynchronously
    LaunchedEffect(state, selectedMemo, feeling) {
        if (state == 2 && selectedMemo != null) {
            try {
                // Fire-and-forget: update DataStore to increment EXP if needed and apply feeling
                val memo = selectedMemo
                memo?.let { m ->
                    val mgr = PreferencesDataStoreManager(appContext)
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            mgr.recordCatCommentUsage(m.id, feeling?.name)
                        } catch (_: Exception) { }
                    }
                }
            } catch (e: Exception) {
                // ignore; non-critical
                e.printStackTrace()
            }
        }
    }

    // Show as centered dialog card with orange border (similar to ProcessingDialogActivity)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(3.dp, Color(0xFFFFA400)), // orange frame
            colors = CardDefaults.cardColors(containerColor = Color.Black), // Black background
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            // Content directly in the card with no inner white padding
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 520.dp)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog Title
                Text(
                    text = stringResource(R.string.catcomment_dialog_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = Color(0xFFFFA400),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                when (state) {
                    0 -> {
                        CircularProgressIndicator(color = Color(0xFFFFA400))
                        Spacer(modifier = Modifier.height(12.dp))
                        val prefix = stringResource(R.string.catcomment_processing_prefix)
                        Text(
                            text = "$prefix ${processingMessage}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { (appContext as? android.app.Activity)?.finish() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFA400))
                        ) {
                            Text(stringResource(R.string.catcomment_close))
                        }
                    }
                    1 -> {
                        Image(painter = painterResource(id = R.drawable.confused), contentDescription = null, modifier = Modifier.size(160.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.catcomment_no_memo_message),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { (appContext as? android.app.Activity)?.finish() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFA400))
                        ) {
                            Text(stringResource(R.string.catcomment_close))
                        }
                    }
                    3 -> {
                        Image(painter = painterResource(id = R.drawable.neutral), contentDescription = null, modifier = Modifier.size(160.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.catcomment_no_response_message),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { (appContext as? android.app.Activity)?.finish() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFA400))
                        ) {
                            Text(stringResource(R.string.catcomment_close))
                        }
                    }
                    2 -> {
                        // Result: memo preview + comment + feeling image + close
                        selectedMemo?.let { memo ->
                            // Show each memo part centered
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                MemoPreview(memo = memo, appUiDisplayMode = UiDisplayMode.LIGHT)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Cat comment area: Speech bubble style (White background, Black text)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White
                            ) {
                                Text(
                                    text = commentText ?: "",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Black
                                )
                            }
                            // Triangle tail pointing down to the kitten image
                            Canvas(
                                modifier = Modifier
                                    .size(24.dp, 12.dp)
                                    .offset(y = (-1).dp) // Slight overlap to prevent visible seam
                            ) {
                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(size.width / 2f, size.height)
                                    close()
                                }
                                drawPath(path, Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val imageRes = when (feeling) {
                            Feeling.confused -> R.drawable.confused
                            Feeling.cool -> R.drawable.cool
                            Feeling.curious -> R.drawable.curious
                            Feeling.difficult -> R.drawable.difficult
                            Feeling.happy -> R.drawable.happy
                            Feeling.neutral -> R.drawable.neutral
                            Feeling.thoughtful -> R.drawable.thoughtful
                            Feeling.scared -> R.drawable.scared
                            else -> R.drawable.neutral
                        }
                        Image(painter = painterResource(id = imageRes), contentDescription = null, modifier = Modifier.size(140.dp))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Disclaimer
                        Text(
                            text = stringResource(R.string.catcomment_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF4444),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { (appContext as? android.app.Activity)?.finish() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFA400))
                        ) {
                            Text(stringResource(R.string.catcomment_close))
                        }
                    }
                }
            }
        }
    }
}

private fun selectWeightedMemo(memos: List<Memo>): Memo {
    // weight: latest 5 -> 5, next 5 -> 3, rest -> 1
    val sorted = memos.sortedByDescending { it.createdAt }
    val weighted = mutableListOf<Memo>()
    for ((idx, m) in sorted.withIndex()) {
        val weight = when {
            idx < 5 -> 5
            idx < 10 -> 3
            else -> 1
        }
        repeat(weight) { weighted.add(m) }
    }
    return weighted.random()
}

/**
 * Remove XML-like tags (e.g. <comment>, <feeling>) and strip any isolated feeling label lines.
 */
private fun sanitizeCatComment(raw: String?, feeling: Feeling?): String? {
    raw ?: return null
    // Remove any tags like <...>
    var s = raw.replace(Regex("<[^>]*>"), "").trim()

    // More aggressive feeling label removal
    val feelings = Feeling.values().map { it.name }
    val lines = s.lines().map { it.trim() }.toMutableList()
    
    if (lines.isNotEmpty()) {
        val lastLine = lines.last().lowercase(Locale.getDefault())
        // Check for "feeling: confused", "feeling - confused", or just "confused"
        val matchedFeeling = feelings.find { 
            lastLine == it || 
            lastLine == "${it}ing" || // handle "confusing" vs "confused"
            lastLine.contains("feeling: $it") ||
            lastLine.contains("feeling:$it") ||
            lastLine.contains("feeling - $it")
        }
        
        if (matchedFeeling != null) {
            lines.removeAt(lines.size - 1)
        } else {
            // Also check if it's attached to the end of the last line
            var last = lines.last()
            feelings.forEach { f ->
                val fLower = f.lowercase(Locale.getDefault())
                val patterns = listOf("feeling: $fLower", "feeling:$fLower", "feeling - $fLower", "feeling $fLower", "feeling: ${fLower}ing")
                patterns.forEach { p ->
                    if (last.lowercase(Locale.getDefault()).endsWith(p)) {
                        last = last.substring(0, last.lowercase(Locale.getDefault()).lastIndexOf(p)).trim()
                    }
                }
                // Handle just the word if it's clearly a label at the end
                if (last.lowercase(Locale.getDefault()).endsWith(" $fLower") || last.lowercase(Locale.getDefault()).endsWith(" ${fLower}ing")) {
                    val wordLen = if (last.lowercase(Locale.getDefault()).endsWith("${fLower}ing")) fLower.length + 3 else fLower.length
                    last = last.substring(0, last.length - wordLen).trim()
                }
            }
            lines[lines.size - 1] = last
        }
    }
    s = lines.filter { it.isNotEmpty() }.joinToString("\n")

    // As extra sanitization, remove any left-over markers
    s = s.replace("<comment>", "").replace("</comment>", "").replace("<feeling>", "").replace("</feeling>", "")

    return s.trim().ifEmpty { null }
}
