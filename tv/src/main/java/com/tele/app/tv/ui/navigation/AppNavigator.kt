package com.tele.app.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tele.app.tv.ui.detail.DetailScreen
import com.tele.app.tv.ui.main.MainScreen
import com.tele.app.tv.ui.player.PlayerScreen

@Composable
fun AppNavigator() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(onItemSelected = { item ->
                navController.navigate("detail/${item.id}")
            })
        }

        composable("detail/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            DetailScreen(
                itemId = id,
                onPlay = { magnetUri, fileIndex ->
                    navController.navigate("player?magnet=$magnetUri&file=$fileIndex")
                }
            )
        }

        composable("player") { backStackEntry ->
            val args = backStackEntry.arguments ?: return@composable
            PlayerScreen(
                magnetUri = args.getString("magnet") ?: "",
                fileIndex = args.getInt("file", 0),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
