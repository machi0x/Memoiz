@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.machi.memoiz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.CategoryRepository
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.service.ClipboardMonitorService
import com.machi.memoiz.ui.ViewModelFactory
import com.machi.memoiz.ui.screens.MainScreen
import com.machi.memoiz.ui.screens.MainViewModel
import com.machi.memoiz.ui.screens.SettingsScreen
import com.machi.memoiz.ui.screens.SettingsViewModel
import com.machi.memoiz.ui.theme.MemoizTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModelFactory: ViewModelFactory
    private var keepSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install splash screen
        installSplashScreen().setKeepOnScreenCondition {
            keepSplash
        }

        // Initialize database and repositories
        val database = MemoizDatabase.getDatabase(applicationContext)
        val categoryRepository = CategoryRepository(database.categoryDao())
        val memoRepository = MemoRepository(database.memoDao())

        // Create ViewModelFactory
        viewModelFactory = ViewModelFactory(categoryRepository, memoRepository)

        // If launched with request to start clipboard monitor, start service
        handleIntent(intent)
        
        // Hide splash screen after 1.5 seconds
        lifecycleScope.launch {
            delay(1500)
            keepSplash = false
        }

        setContent {
            MemoizTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            // Specify the ViewModel type explicitly to avoid type inference errors
                            val viewModel: MainViewModel = viewModel(factory = viewModelFactory)
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        composable("settings") {
                            val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == com.machi.memoiz.boot.BootReceiver.ACTION_START_CLIPBOARD_MONITOR) {
            // Start the ClipboardMonitorService as foreground service
            val svcIntent = Intent(this, ClipboardMonitorService::class.java)
            startForegroundService(svcIntent)
        }
    }
}
