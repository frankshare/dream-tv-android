package com.dream.tv.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dream.tv.AppViewModel
import com.dream.tv.ui.detail.DetailScreen
import com.dream.tv.ui.glass.GlassTabBar
import com.dream.tv.ui.glass.TabItem
import com.dream.tv.ui.home.HomeScreen
import com.dream.tv.ui.mine.MineScreen
import com.dream.tv.ui.player.PlayerScreen
import com.dream.tv.ui.search.SearchScreen
import com.dream.tv.ui.settings.SettingsScreen
import com.dream.tv.ui.settings.SourceManageScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search?query={query}"
    const val MINE = "mine"
    const val SETTINGS = "settings"
    const val SOURCES = "sources"
    const val DETAIL = "detail/{sourceId}/{vodId}"
    const val PLAYER = "player/{sourceId}/{vodId}/{episodeIndex}?resumeMs={resumeMs}"

    fun detail(sourceId: String, vodId: String) = "detail/$sourceId/$vodId"
    fun player(sourceId: String, vodId: String, episodeIndex: Int, resumeMs: Long = 0L) =
        "player/$sourceId/$vodId/$episodeIndex?resumeMs=$resumeMs"
}

private val TABS = listOf(
    TabItem("home", "首页", Icons.Filled.Home),
    TabItem("search", "搜索", Icons.Filled.Search),
    TabItem("mine", "我的", Icons.Filled.Favorite),
    TabItem("settings", "设置", Icons.Filled.Settings),
)

private val TAB_ROUTES = setOf("home", "search", "mine", "settings")

@Composable
fun DreamNavRoot(app: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val isTab = TAB_ROUTES.any { route.startsWith(it) }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = {
                fadeIn(tween(260)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    tween(300), initialOffset = { it / 18 }
                )
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = {
                fadeOut(tween(220)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    tween(260), targetOffset = { it / 18 }
                )
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    app = app,
                    onOpenSearch = { query ->
                        nav.navigate("search?query=$query") {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }
            composable(
                Routes.SEARCH,
                arguments = listOf(navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }),
            ) { entry ->
                val query = entry.arguments?.getString("query").orEmpty()
                SearchScreen(
                    app = app,
                    initialQuery = query,
                    onOpenDetail = { s, id -> nav.navigate(Routes.detail(s, id)) },
                )
            }
            composable(Routes.MINE) {
                MineScreen(
                    app = app,
                    onOpenDetail = { s, id -> nav.navigate(Routes.detail(s, id)) },
                    onPlayHistory = { s, id, ep, resume ->
                        nav.navigate(Routes.player(s, id, ep, resume))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    app = app,
                    onOpenSources = { nav.navigate(Routes.SOURCES) },
                )
            }
            composable(Routes.SOURCES) {
                SourceManageScreen(app = app, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("vodId") { type = NavType.StringType },
                ),
            ) { entry ->
                DetailScreen(
                    app = app,
                    sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                    vodId = entry.arguments?.getString("vodId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onPlay = { s, id, ep, resume ->
                        nav.navigate(Routes.player(s, id, ep, resume))
                    },
                    onSwitchSource = { s, id ->
                        nav.navigate(Routes.detail(s, id)) {
                            popUpTo(Routes.DETAIL) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                Routes.PLAYER,
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("vodId") { type = NavType.StringType },
                    navArgument("episodeIndex") { type = NavType.IntType },
                    navArgument("resumeMs") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                PlayerScreen(
                    app = app,
                    sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                    vodId = entry.arguments?.getString("vodId").orEmpty(),
                    startEpisode = entry.arguments?.getInt("episodeIndex") ?: 0,
                    resumeMs = entry.arguments?.getLong("resumeMs") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onSwitchSource = { s, id, resume ->
                        nav.navigate(Routes.player(s, id, 0, resume)) {
                            popUpTo(Routes.PLAYER.substringBefore('?')) { inclusive = true }
                        }
                    },
                )
            }
        }

        if (isTab) {
            GlassTabBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                tabs = TABS,
                selected = route.substringBefore('?').ifBlank { Routes.HOME },
                onSelect = { id ->
                    nav.navigate(id) {
                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
