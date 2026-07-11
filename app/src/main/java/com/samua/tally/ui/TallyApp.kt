package com.samua.tally.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.samua.tally.data.TallyStore
import com.samua.tally.ui.screens.*
import com.samua.tally.ui.theme.TallyTheme

private data class TabItem(val route: String, val title: String, val icon: ImageVector)
private val tabs = listOf(
    TabItem("counters", "Counters", Icons.Rounded.Numbers),
    TabItem("sessions", "Sessions", Icons.Rounded.Timer),
    TabItem("stats", "Stats", Icons.Rounded.BarChart),
    TabItem("history", "History", Icons.Rounded.History),
    TabItem("settings", "Settings", Icons.Rounded.Settings)
)

@Composable
fun TallyApp(store: TallyStore) {
    val state by store.state.collectAsStateWithLifecycle()
    TallyTheme(state.theme, state.accentRaw, state.customAccentHex) {
        val navController = rememberNavController()
        val currentEntry by navController.currentBackStackEntryAsState()
        val route = currentEntry?.destination?.route
        Scaffold(
            bottomBar = {
                if (route?.startsWith("counter/") != true) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route || (route == null && tab.route == "counters"),
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, tab.title) },
                                label = { Text(tab.title) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(navController, startDestination = "counters", modifier = Modifier) {
                composable("counters") { CountersScreen(store, padding) { id -> navController.navigate("counter/$id") } }
                composable("sessions") { SessionsScreen(store, padding) }
                composable("stats") { StatsScreen(store, padding) }
                composable("history") { HistoryScreen(store, padding) }
                composable("settings") { SettingsScreen(store, padding) }
                composable("counter/{id}") { backStack ->
                    CounterDetailScreen(store, backStack.arguments?.getString("id").orEmpty(), padding) { navController.popBackStack() }
                }
            }
        }
    }
}
