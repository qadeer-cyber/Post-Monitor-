package com.affiliatemonitor.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument

import com.affiliatemonitor.app.ui.screens.DashboardScreen
import com.affiliatemonitor.app.ui.screens.InputHubScreen
import com.affiliatemonitor.app.ui.screens.LogsScreen
import com.affiliatemonitor.app.ui.screens.PostDetailScreen
import com.affiliatemonitor.app.ui.screens.PostedScreen
import com.affiliatemonitor.app.ui.screens.QueueScreen
import com.affiliatemonitor.app.ui.screens.SettingsScreen
import com.affiliatemonitor.app.ui.theme.DeepBg
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.TextMuted

object Routes {
    const val Dashboard = "dashboard"
    const val InputHub = "input_hub"
    const val Queue = "queue"
    const val Posted = "posted"
    const val Logs = "logs"
    const val Settings = "settings"
    const val PostDetail = "posts/{id}"
    fun postDetail(id: Int) = "posts/$id"
}

data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab(Routes.Dashboard, "Dashboard", Icons.Outlined.Dashboard),
    NavTab(Routes.InputHub, "Input Hub", Icons.Outlined.AddCircle),
    NavTab(Routes.Queue, "Queue", Icons.Outlined.Inbox),
    NavTab(Routes.Posted, "Posted", Icons.Outlined.CheckCircle),
    NavTab(Routes.Logs, "Logs", Icons.Outlined.Article),
    NavTab(Routes.Settings, "Settings", Icons.Outlined.Settings),
)

@Composable
fun AppNav(nav: NavHostController) {
    NavHost(
        navController = nav,
        startDestination = Routes.Dashboard,
    ) {
        composable(Routes.Dashboard) { DashboardScreen() }
        composable(Routes.InputHub) { InputHubScreen() }
        composable(Routes.Queue) { QueueScreen(onOpen = { nav.navigate(Routes.postDetail(it)) }) }
        composable(Routes.Posted) { PostedScreen(onOpen = { nav.navigate(Routes.postDetail(it)) }) }
        composable(Routes.Logs) { LogsScreen() }
        composable(Routes.Settings) { SettingsScreen() }
        composable(
            route = Routes.PostDetail,
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { backStack ->
            val id = backStack.arguments?.getInt("id") ?: 0
            PostDetailScreen(postId = id, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    NavigationBar(
        containerColor = DeepBg,
        contentColor = TextMuted,
    ) {
        tabs.forEach { tab ->
            val selected = current?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonBlue,
                    selectedTextColor = NeonBlue,
                    indicatorColor = Color(0x3300E5FF),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                ),
            )
        }
    }
}


