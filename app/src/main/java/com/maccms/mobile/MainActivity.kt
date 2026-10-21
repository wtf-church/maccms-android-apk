package com.maccms.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maccms.mobile.data.Vod
import com.maccms.mobile.data.VodCache
import com.maccms.mobile.data.VodRepository
import com.maccms.mobile.ui.browse.BrowseScreen
import com.maccms.mobile.ui.browse.BrowseState
import com.maccms.mobile.ui.detail.DetailScreen
import com.maccms.mobile.ui.home.HomeScreen
import com.maccms.mobile.ui.home.HomeState
import com.maccms.mobile.ui.library.LibraryScreen
import com.maccms.mobile.ui.player.PlayerScreen
import com.maccms.mobile.ui.search.SearchScreen
import com.maccms.mobile.ui.search.SearchState
import com.maccms.mobile.ui.settings.SettingsScreen
import com.maccms.mobile.ui.theme.AppBlack
import com.maccms.mobile.ui.theme.AppSurface
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.MaccmsTheme
import com.maccms.mobile.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaccmsTheme {
                MaccmsRoot()
            }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    TabItem("home", "首页", Icons.Outlined.Home),
    TabItem("browse", "浏览", Icons.Outlined.GridView),
    TabItem("search", "搜索", Icons.Outlined.Search),
    TabItem("library", "我的", Icons.Outlined.Person)
)

@Composable
private fun MaccmsRoot() {
    val context = LocalContext.current
    val repository = remember { VodRepository(context) }
    val navController = rememberNavController()
    val homeState = remember { HomeState() }
    val browseState = remember { BrowseState() }
    val searchState = remember { SearchState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TABS.map { it.route }

    Scaffold(
        containerColor = AppBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                MaccmsBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route -> navigateToTop(navController, route) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    repository = repository,
                    state = homeState,
                    onVodClick = { vod -> openDetail(navController, vod) },
                    onPlayVod = { vod -> openPlayer(navController, vod, null) },
                    onCategoryMore = { typeId ->
                        browseState.selectType(typeId)
                        navigateToTop(navController, "browse")
                    },
                    onOpenSearch = { navigateToTop(navController, "search") }
                )
            }
            composable("browse") {
                BrowseScreen(
                    repository = repository,
                    onVodClick = { vod -> openDetail(navController, vod) },
                    state = browseState
                )
            }
            composable("search") {
                SearchScreen(
                    repository = repository,
                    onVodClick = { vod -> openDetail(navController, vod) },
                    state = searchState
                )
            }
            composable("library") {
                LibraryScreen(
                    onOpenDetail = { vod -> openDetail(navController, vod) },
                    onPlayVod = { vod -> openPlayer(navController, vod, null) },
                    onOpenSettings = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "detail/{vodId}",
                arguments = listOf(navArgument("vodId") { type = NavType.IntType })
            ) { entry ->
                val vodId = entry.arguments?.getInt("vodId") ?: 0
                DetailScreen(
                    vodId = vodId,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onPlay = { vod -> openPlayer(navController, vod, null) },
                    onPlayEpisode = { vod, ep -> openPlayer(navController, vod, ep.name) }
                )
            }
            composable(
                route = "player/{vodId}?episode={episode}",
                arguments = listOf(
                    navArgument("vodId") { type = NavType.IntType },
                    navArgument("episode") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val vodId = entry.arguments?.getInt("vodId") ?: 0
                val episode = entry.arguments?.getString("episode").orEmpty()
                PlayerScreen(
                    vodId = vodId,
                    episodeName = episode,
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun MaccmsBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = AppSurface, tonalElevation = 0.dp) {
        TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                icon = {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = { Text(tab.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandRed,
                    selectedTextColor = BrandRed,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = BrandRed.copy(alpha = 0.14f)
                )
            )
        }
    }
}

private fun openDetail(nav: NavHostController, vod: Vod) {
    VodCache.put(vod)
    nav.navigate("detail/${vod.vod_id}")
}

private fun openPlayer(nav: NavHostController, vod: Vod, episodeName: String?) {
    VodCache.put(vod)
    val route = if (episodeName.isNullOrBlank()) {
        "player/${vod.vod_id}"
    } else {
        "player/${vod.vod_id}?episode=${Uri.encode(episodeName)}"
    }
    nav.navigate(route)
}

private fun navigateToTop(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
