package com.filedroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filedroid.ui.home.HomeScreen
import com.filedroid.ui.local.LocalBrowserScreen
import com.filedroid.ui.profiles.ProfileListScreen
import com.filedroid.ui.settings.FtpSettingsScreen
import com.filedroid.ui.settings.SettingsScreen
import com.filedroid.ui.settings.SftpSettingsScreen
import com.filedroid.ui.theme.FileDroidTheme
import com.filedroid.ui.server.ServerControlScreen
import com.filedroid.ui.ssh.SshTerminalScreen
import com.filedroid.ui.transfer.TransferQueueScreen

object Routes {
    const val HOME = "home"
    const val LOCAL_BROWSER = "local_browser"
    const val PROFILES = "profiles"
    const val TRANSFERS = "transfers"
    const val SERVER = "server"
    const val SSH = "ssh"
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
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToLocalBrowser = { navController.navigate(Routes.LOCAL_BROWSER) },
                onNavigateToProfiles = { navController.navigate(Routes.PROFILES) },
                onNavigateToTransfers = { navController.navigate(Routes.TRANSFERS) },
                onNavigateToServer = { navController.navigate(Routes.SERVER) },
                onNavigateToSsh = { navController.navigate(Routes.SSH) }
            )
        }
        composable(Routes.LOCAL_BROWSER) {
            LocalBrowserScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILES) {
            ProfileListScreen(
                onNavigateBack = { navController.popBackStack() },
                onConnect = { navController.navigate("${Routes.PROFILES}/$it") },
                onEdit = { navController.navigate("${Routes.PROFILES}/$it/edit") }
            )
        }
        composable(Routes.TRANSFERS) {
            TransferQueueScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SERVER) {
            ServerControlScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SSH) {
            SshTerminalScreen(onNavigateBack = { navController.popBackStack() })
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
