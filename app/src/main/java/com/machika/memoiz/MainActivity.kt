package com.machika.memoiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.machika.memoiz.data.MemoizDatabase
import com.machika.memoiz.data.repository.CategoryRepository
import com.machika.memoiz.data.repository.MemoRepository
import com.machika.memoiz.ui.screens.MainScreen
import com.machika.memoiz.ui.screens.MainViewModel
import com.machika.memoiz.ui.screens.SettingsScreen
import com.machika.memoiz.ui.screens.SettingsViewModel
import com.machika.memoiz.ui.theme.MemoizTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var database: MemoizDatabase
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var memoRepository: MemoRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize database and repositories
        database = MemoizDatabase.getDatabase(applicationContext)
        categoryRepository = CategoryRepository(database.categoryDao())
        memoRepository = MemoRepository(database.memoDao())
        
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
                            val viewModel = MainViewModel(memoRepository, categoryRepository)
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        
                        composable("settings") {
                            val viewModel = SettingsViewModel(categoryRepository)
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
