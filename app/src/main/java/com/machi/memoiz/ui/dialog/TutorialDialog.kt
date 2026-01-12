package com.machi.memoiz.ui.dialog

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.machi.memoiz.R
import com.machi.memoiz.ui.screens.MainViewModel
import com.machi.memoiz.util.UsageStatsHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TutorialStep(
    @DrawableRes val imageRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun TutorialDialog(
    onFinished: () -> Unit,
    initialStep: Int = 0,
    viewModel: MainViewModel? = null,
    includeMemoizCommentStep: Boolean = true
) {
    val context = LocalContext.current
    val baseSteps = listOf(
        TutorialStep(R.drawable.top_banner, R.string.tutorial_step_overview_title, R.string.tutorial_step_overview_body),
        TutorialStep(R.drawable.share_from_browser, R.string.tutorial_step_share_browser_title, R.string.tutorial_step_share_browser_body),
        TutorialStep(R.drawable.share_from_select, R.string.tutorial_step_share_select_title, R.string.tutorial_step_share_select_body),
        TutorialStep(R.drawable.floating_buttons, R.string.tutorial_step_fab_title, R.string.tutorial_step_fab_body),
        TutorialStep(R.drawable.my_category, R.string.tutorial_step_my_category_title, R.string.tutorial_step_my_category_body),
        TutorialStep(R.drawable.app_usages, R.string.tutorial_step_usage_permission_title, R.string.tutorial_step_usage_permission_body),
        TutorialStep(R.drawable.main_ui, R.string.tutorial_step_main_ui_title, R.string.tutorial_step_main_ui_body),
        TutorialStep(R.drawable.side_panel, R.string.tutorial_step_side_panel_title, R.string.tutorial_step_side_panel_body)
    )

    val steps = if (includeMemoizCommentStep) {
        baseSteps + listOf(
            TutorialStep(R.drawable.memoiz_comment, R.string.tutorial_step_memoiz_comment_title, R.string.tutorial_step_memoiz_comment_body),
            TutorialStep(R.drawable.export, R.string.tutorial_step_export_title, R.string.tutorial_step_export_body)
        )
    } else {
        baseSteps + listOf(
            TutorialStep(R.drawable.export, R.string.tutorial_step_export_title, R.string.tutorial_step_export_body)
        )
    }

    var currentStep by rememberSaveable { mutableStateOf(initialStep.coerceIn(0, steps.lastIndex)) }
    val isLastStep = currentStep == steps.lastIndex
    val step = steps[currentStep]

    // Usage permission related state needs to be available before deciding which description to show
    val tutorialCoroutineScope = rememberCoroutineScope()
    var usagePermissionJob by remember { mutableStateOf<Job?>(null) }
    val usagePermissionStepIndex = steps.indexOfFirst { it.imageRes == R.drawable.app_usages }
    val isUsagePermissionStep = currentStep == usagePermissionStepIndex && usagePermissionStepIndex >= 0
    var usagePermissionGranted by rememberSaveable { mutableStateOf(UsageStatsHelper(context).hasUsageStatsPermission()) }
    LaunchedEffect(isUsagePermissionStep) {
        if (isUsagePermissionStep) {
            usagePermissionGranted = UsageStatsHelper(context).hasUsageStatsPermission()
        }
    }

    val stepTitle = stringResource(step.titleRes)
    // Choose description resource dynamically for usage-permission step when permission already granted
    val stepDescriptionText = if (isUsagePermissionStep) {
        if (usagePermissionGranted) stringResource(R.string.tutorial_step_usage_permission_body_granted)
        else stringResource(step.descriptionRes)
    } else {
        stringResource(step.descriptionRes)
    }

    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.98f)
            .widthIn(min = 600.dp, max = 760.dp)
            .height(720.dp),
        onDismissRequest = onFinished,
        title = { Text(stringResource(R.string.tutorial_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Move the step indicator above the image so its vertical position doesn't change
                // when the description text length changes.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    steps.forEachIndexed { index, _ ->
                        val color = if (index == currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (index == currentStep) 10.dp else 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color)
                        )
                    }
                }

                val imageScrollState = rememberScrollState()
                val isLargeShot = step.imageRes == R.drawable.main_ui || step.imageRes == R.drawable.side_panel
                val baseBoxModifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)

                Box(
                    modifier = if (isLargeShot) baseBoxModifier.weight(0.55f, fill = true).heightIn(max = 250.dp) else baseBoxModifier.heightIn(max = 280.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .let { m -> if (isLargeShot) m.verticalScroll(imageScrollState) else m }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = step.imageRes),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        )
                    }
                }

                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stepDescriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (isUsagePermissionStep && !usagePermissionGranted) {
                    TextButton(onClick = {
                        usagePermissionJob?.cancel()
                        launchUsageAccessSettings(context)
                        usagePermissionJob = tutorialCoroutineScope.launch {
                            val helper = UsageStatsHelper(context)
                            val deadline = System.currentTimeMillis() + 60_000
                            while (System.currentTimeMillis() < deadline && !helper.hasUsageStatsPermission()) {
                                delay(2_000)
                            }
                            if (helper.hasUsageStatsPermission()) {
                                usagePermissionGranted = true
                                // Bring the main UI to foreground once permission is granted.
                                context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    context.startActivity(intent)
                                }
                                currentStep = (currentStep + 1).coerceAtMost(steps.lastIndex)
                            }
                        }
                    }) {
                        Text(stringResource(R.string.tutorial_usage_permission_button))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isLastStep) {
                    onFinished()
                } else {
                    currentStep++
                }
            }) {
                Text(if (isLastStep) stringResource(R.string.tutorial_done) else stringResource(R.string.tutorial_next))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (currentStep > 0) currentStep-- else onFinished()
            }) {
                Text(if (currentStep > 0) stringResource(R.string.tutorial_back) else stringResource(R.string.tutorial_skip))
            }
        }
    )
}

fun launchUsageAccessSettings(context: Context) {
    try {
        Toast.makeText(context, context.getString(R.string.settings_usage_select_app_toast), Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
    }
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}
