package com.machi.memoiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.CategoryRepository
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.ui.ViewModelFactory
import com.machi.memoiz.ui.screens.MainScreen
import com.machi.memoiz.ui.screens.SettingsScreen
import com.machi.memoiz.ui.theme.MemoizTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModelFactory: ViewModelFactory
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize database and repositories
        val database = MemoizDatabase.getDatabase(applicationContext)
        val categoryRepository = CategoryRepository(database.categoryDao())
        val memoRepository = MemoRepository(database.memoDao())
        
        // Create ViewModelFactory
        viewModelFactory = ViewModelFactory(categoryRepository, memoRepository)
        
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
                            val viewModel = viewModel(factory = viewModelFactory)
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        
                        composable("settings") {
                            val viewModel = viewModel(factory = viewModelFactory)
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
