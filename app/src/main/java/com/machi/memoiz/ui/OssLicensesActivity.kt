package com.machi.memoiz.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.machi.memoiz.R
import com.machi.memoiz.ui.theme.MemoizTheme
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

class OssLicensesActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MemoizTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings_oss_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back)
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    // produceLibraries returns State<Libs?>
                    val libraries by produceLibraries()
                    val fonts by produceLibraries(R.raw.aboutlibraries_fonts)

                    // Merge fonts into main libraries when both available
                    val merged: Libs? = remember(libraries, fonts) {
                        // copy delegated props into locals to avoid smart-cast issues
                        val left = libraries
                        val right = fonts
                        when {
                            left == null && right == null -> null
                            left == null -> right
                            right == null -> left
                            else -> {
                                try {
                                    // Combine the immutable lists/maps into new immutable collections
                                    val combinedLibs = (left.libraries + right.libraries).toImmutableList()
                                    val combinedLicenses = (left.licenses + right.licenses).toImmutableSet()
                                    left.copy(libraries = combinedLibs, licenses = combinedLicenses)
                                } catch (e: Throwable) {
                                    Log.e("OssLicensesActivity", "Failed to merge libs: ${e.message}")
                                    left
                                }
                            }
                        }
                    }

                    // Debug log to verify custom entries
                    merged?.let { libs ->
                        val ids = libs.libraries.mapNotNull { it.uniqueId }
                        Log.d("OssLicensesActivity", "Merged libraries count=${ids.size}; contains mplus1code=${ids.contains("mplus1code")}; yomogi=${ids.contains("yomogi")}; dotgothic16=${ids.contains("dotgothic16")}")
                    } ?: Log.d("OssLicensesActivity", "merged libs is null")

                    LibrariesContainer(
                        libraries = merged,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                }
            }
        }
    }
}
