package com.machi.memoiz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.datastore.UserPreferences
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.ui.ViewModelFactory
import com.machi.memoiz.ui.screens.MainScreen
import com.machi.memoiz.ui.screens.MainViewModel
import com.machi.memoiz.ui.screens.SettingsScreen
import com.machi.memoiz.ui.screens.SettingsViewModel
import com.machi.memoiz.ui.theme.MemoizTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toArgb
import androidx.work.WorkManager
import com.machi.memoiz.data.datastore.UiDisplayMode

class MainActivity : ComponentActivity() {

    private lateinit var viewModelFactory: ViewModelFactory
    private var keepSplash = true
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register permission launcher before using it
        requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _: Boolean ->
            // No UI required here; if needed we could show a Snackbar or log the result
        }

        // Request POST_NOTIFICATIONS (minSdk >= 34 guarantees the API is available)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Install splash screen
        installSplashScreen().setKeepOnScreenCondition {
            keepSplash
        }

        // Initialize database and repositories
        val database = MemoizDatabase.getDatabase(applicationContext)
        val memoRepository = MemoRepository(database.memoDao())
        val preferencesManager = PreferencesDataStoreManager(applicationContext)

        // Create ViewModelFactory
        val workManager = WorkManager.getInstance(applicationContext)
        viewModelFactory = ViewModelFactory(memoRepository, preferencesManager, workManager, applicationContext)

        // Hide splash screen after 1.5 seconds
        lifecycleScope.launch {
            delay(1500)
            keepSplash = false
        }

        setContent {
            // collect user preferences to determine UI display mode
            val prefsState = preferencesManager.userPreferencesFlow.collectAsState(initial = UserPreferences())
            val uiMode = prefsState.value.uiDisplayMode
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (uiMode) {
                UiDisplayMode.LIGHT -> false
                UiDisplayMode.DARK -> true
                UiDisplayMode.SYSTEM -> systemDark
            }

            MemoizTheme(darkTheme = darkTheme) {
                val colorScheme = MaterialTheme.colorScheme
                val isDarkTheme = darkTheme
                val view = window
                val windowInsetsController = WindowCompat.getInsetsController(view, view.decorView)
                SideEffect {
                    view.statusBarColor = colorScheme.background.toArgb()
                    windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
                }
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
}
