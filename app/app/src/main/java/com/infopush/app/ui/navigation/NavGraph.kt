package com.infopush.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infopush.app.data.repository.MessageRepository
import com.infopush.app.data.repository.SettingsRepository
import com.infopush.app.ui.screens.LoginScreen
import com.infopush.app.ui.screens.MessageDetailScreen
import com.infopush.app.ui.screens.MessageListScreen
import com.infopush.app.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Messages : Screen("messages")
    data object MessageDetail : Screen("message/{messageId}") {
        fun createRoute(messageId: String) = "message/$messageId"
    }
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    isLoggedIn: Boolean,
    settingsRepo: SettingsRepository,
    messageRepo: MessageRepository,
    onLoginSuccess: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val startDestination = if (isLoggedIn) Screen.Messages.route else Screen.Login.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(
                settingsRepo = settingsRepo,
                onLoginSuccess = {
                    onLoginSuccess()
                    navController.navigate(Screen.Messages.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Messages.route) {
            MessageListScreen(
                messageRepo = messageRepo,
                settingsRepo = settingsRepo,
                onMessageClick = { messageId ->
                    navController.navigate(Screen.MessageDetail.createRoute(messageId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.MessageDetail.route,
            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val messageId = backStackEntry.arguments?.getString("messageId") ?: return@composable
            MessageDetailScreen(
                messageId = messageId,
                messageRepo = messageRepo,
                settingsRepo = settingsRepo,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                settingsRepo = settingsRepo,
                messageRepo = messageRepo,
                onBack = { navController.popBackStack() },
                onLogout = {
                    onLogout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
