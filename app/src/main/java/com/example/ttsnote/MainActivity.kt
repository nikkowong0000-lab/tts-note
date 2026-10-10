package com.example.ttsnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ttsnote.ui.NoteEditScreen
import com.example.ttsnote.ui.NoteListScreen
import com.example.ttsnote.ui.NoteViewModel
import com.example.ttsnote.ui.NoteViewModelFactory
import com.example.ttsnote.ui.ThemeMode
import com.example.ttsnote.ui.theme.TTSNoteTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NoteViewModel by viewModels {
        val app = application as TTSNoteApplication
        NoteViewModelFactory(app.repository, app.preferenceManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.Auto -> isSystemInDarkTheme()
            }

            TTSNoteTheme(darkTheme = darkTheme) {
                EchoNotesApp(viewModel)
            }
        }
    }
}

@Composable
fun EchoNotesApp(viewModel: NoteViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController, 
        startDestination = "list",
        enterTransition = {fadeIn() + slideInHorizontally { it } },
        exitTransition = { fadeOut() + slideOutHorizontally { -it } },
        popEnterTransition = { fadeIn() + slideInHorizontally { -it } },
        popExitTransition = { fadeOut() + slideOutHorizontally { it } }
    ) {
        composable("list") {
            NoteListScreen(
                viewModel = viewModel,
                onNoteClick = { noteId ->
                    navController.navigate("edit/$noteId")
                },
                onAddNoteClick = {
                    navController.navigate("edit/-1")
                }
            )
        }
        composable(
            route = "edit/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: -1L
            NoteEditScreen(
                noteId = noteId,
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
