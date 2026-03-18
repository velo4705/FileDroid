package com.filedroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filedroid.ui.home.HomeScreen
import com.filedroid.ui.settings.FtpSettingsScreen
import com.filedroid.ui.settings.SettingsScreen
import com.filedroid.ui.settings.SftpSettingsScreen
import com.filedroid.ui.theme.FileDroidTheme

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SETTINGS_FTP = "settings/ftp"
    const val SETTINGS_SFTP = "settings/sftp"
}

@Composable
fun FileDroidApp() {
    FileDroidTheme {
        val navController = rememberNavController()
        NavGraph(navController = navController)
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToFtp = { navController.navigate(Routes.SETTINGS_FTP) },
                onNavigateToSftp = { navController.navigate(Routes.SETTINGS_SFTP) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS_FTP) {
            FtpSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS_SFTP) {
            SftpSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
