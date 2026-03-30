package com.filedroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.ui.editor.FileEditorScreen
import com.filedroid.ui.home.HomeScreen
import com.filedroid.ui.local.LocalBrowserScreen
import com.filedroid.ui.profiles.ProfileListScreen
import com.filedroid.ui.remote.RemoteBrowserScreen
import com.filedroid.ui.settings.FtpSettingsScreen
import com.filedroid.ui.settings.SettingsScreen
import com.filedroid.ui.settings.SftpSettingsScreen
import com.filedroid.ui.theme.FileDroidTheme
import com.filedroid.ui.theme.ThemeViewModel
import com.filedroid.ui.server.ServerControlScreen
import com.filedroid.ui.ssh.SshProfileListScreen
import com.filedroid.ui.ssh.SshTerminalScreen
import com.filedroid.ui.transfer.TransferQueueScreen
import com.filedroid.ui.tunnel.TunnelScreen
import com.filedroid.ui.dual.DualPanelScreen

object Routes {
    const val HOME = "home"
    const val LOCAL_BROWSER = "local_browser"
    const val PROFILES = "profiles"
    const val REMOTE_BROWSER = "remote_browser"
    const val TRANSFERS = "transfers"
    const val SERVER = "server"
    const val TUNNEL = "tunnel"
    const val DUAL_PANEL = "dual_panel"
    const val SSH_PROFILES = "ssh_profiles"
    const val SSH = "ssh"
    const val SETTINGS = "settings"
    const val SETTINGS_FTP = "settings/ftp"
    const val SETTINGS_SFTP = "settings/sftp"
    const val LOCAL_EDITOR  = "editor/local"   // + ?path=...&isNew=...
    const val REMOTE_EDITOR = "editor/remote"  // + ?profileId=...&remotePath=...&isNew=...
}

@Composable
fun FileDroidApp(themeViewModel: ThemeViewModel) {
    val prefs by themeViewModel.prefs.collectAsState()
    FileDroidTheme(prefs = prefs) {
        val navController = rememberNavController()
        NavGraph(navController = navController, themeViewModel = themeViewModel)
    }
}

@Composable
fun NavGraph(navController: NavHostController, themeViewModel: ThemeViewModel) {
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
                onNavigateToSsh = { navController.navigate(Routes.SSH_PROFILES) },
                onNavigateToTunnel = { navController.navigate(Routes.TUNNEL) },
                onNavigateToDualPanel = { navController.navigate(Routes.DUAL_PANEL) }
            )
        }
        composable(Routes.LOCAL_BROWSER) {
            LocalBrowserScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { path ->
                    navController.navigate("${Routes.LOCAL_EDITOR}?path=${Uri.encode(path)}&isNew=false")
                },
                onNewFile = { dir ->
                    navController.navigate("${Routes.LOCAL_EDITOR}?path=${Uri.encode(dir)}&isNew=true")
                }            )
        }
        composable(Routes.PROFILES) {
            ProfileListScreen(
                onNavigateBack = { navController.popBackStack() },
                onConnect = { navController.navigate("${Routes.REMOTE_BROWSER}/$it") }
            )
        }
        composable(
            route = "${Routes.REMOTE_BROWSER}/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: return@composable
            RemoteBrowserScreen(
                profileId = profileId,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { path ->
                    navController.navigate("${Routes.REMOTE_EDITOR}?profileId=$profileId&remotePath=${Uri.encode(path)}&isNew=false")
                },
                onNewFile = { dir ->
                    navController.navigate("${Routes.REMOTE_EDITOR}?profileId=$profileId&remotePath=${Uri.encode(dir)}&isNew=true")
                }
            )
        }
        composable(Routes.TRANSFERS) {
            TransferQueueScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SERVER) {
            // Scope to the HOME back stack entry so state survives navigation away and back
            val homeEntry = remember(it) { navController.getBackStackEntry(Routes.HOME) }
            val serverViewModel: com.filedroid.ui.server.ServerViewModel = hiltViewModel(homeEntry)
            ServerControlScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = serverViewModel
            )
        }
        composable(Routes.SSH_PROFILES) {
            SshProfileListScreen(
                onNavigateBack = { navController.popBackStack() },
                onConnect = { profile, password ->
                    if (profile.usePrivateKey) {
                        navController.navigate("${Routes.SSH}/key/${profile.id}")
                    } else {
                        navController.navigate("${Routes.SSH}/pass/${profile.host}/${profile.port}/${profile.username}/$password")
                    }
                }
            )
        }
        composable(
            route = "${Routes.SSH}/pass/{host}/{port}/{username}/{password}",
            arguments = listOf(
                navArgument("host") { type = NavType.StringType },
                navArgument("port") { type = NavType.IntType },
                navArgument("username") { type = NavType.StringType },
                navArgument("password") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val host = backStackEntry.arguments?.getString("host") ?: ""
            val port = backStackEntry.arguments?.getInt("port") ?: 22
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val password = backStackEntry.arguments?.getString("password") ?: ""
            SshTerminalScreen(
                initialHost = host,
                initialPort = port,
                initialUsername = username,
                initialPassword = password,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "${Routes.SSH}/key/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: return@composable
            SshTerminalScreen(
                initialSshProfileId = profileId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SSH) {
            SshTerminalScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToFtp = { navController.navigate(Routes.SETTINGS_FTP) },
                onNavigateToSftp = { navController.navigate(Routes.SETTINGS_SFTP) },
                onNavigateBack = { navController.popBackStack() },
                themeVm = themeViewModel
            )
        }
        composable(Routes.SETTINGS_FTP) {
            FtpSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_SFTP) {
            SftpSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.TUNNEL) {
            TunnelScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.DUAL_PANEL) {
            DualPanelScreen(onNavigateBack = { navController.popBackStack() })
        }
        // Local file editor
        composable(
            route = "${Routes.LOCAL_EDITOR}?path={path}&isNew={isNew}",
            arguments = listOf(
                navArgument("path")  { type = NavType.StringType },
                navArgument("isNew") { type = NavType.StringType; defaultValue = "false" }
            )
        ) {
            FileEditorScreen(onNavigateBack = { navController.popBackStack() })
        }
        // Remote file editor
        composable(
            route = "${Routes.REMOTE_EDITOR}?profileId={profileId}&remotePath={remotePath}&isNew={isNew}",
            arguments = listOf(
                navArgument("profileId")   { type = NavType.StringType },
                navArgument("remotePath")  { type = NavType.StringType },
                navArgument("isNew")       { type = NavType.StringType; defaultValue = "false" }
            )
        ) {
            FileEditorScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
