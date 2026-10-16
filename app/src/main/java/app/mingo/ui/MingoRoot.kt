package app.mingo.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.mingo.MingoApp
import app.mingo.R
import app.mingo.ui.about.AboutScreen
import app.mingo.ui.components.AuroraBackground
import app.mingo.ui.components.GlassNavBar
import app.mingo.ui.components.LocalBottomBarPadding
import app.mingo.ui.components.NavItem
import app.mingo.ui.downloads.DownloadsScreen
import app.mingo.ui.home.HomeScreen
import app.mingo.ui.icons.MingoIcons
import app.mingo.ui.library.LibraryScreen
import app.mingo.ui.settings.SettingsScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object DownloadsRoute
@Serializable data object LibraryRoute
@Serializable data object SettingsRoute
@Serializable data object AboutRoute

private val topLevelRoutes = listOf(HomeRoute, DownloadsRoute, LibraryRoute, SettingsRoute)

@Composable
fun MingoRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedIndex = if (backStackEntry?.destination?.hasRoute(AboutRoute::class) == true) {
        topLevelRoutes.indexOf(SettingsRoute) // About lives under Settings
    } else {
        topLevelRoutes.indexOfFirst { route -> backStackEntry?.destination?.hasRoute(route::class) == true }.coerceAtLeast(0)
    }
    val hazeState = rememberHazeState()

    val navItems = listOf(
        NavItem(MingoIcons.Home, stringResource(R.string.nav_home)),
        NavItem(MingoIcons.Downloads, stringResource(R.string.nav_downloads)),
        NavItem(MingoIcons.Library, stringResource(R.string.nav_library)),
        NavItem(MingoIcons.Settings, stringResource(R.string.nav_settings)),
    )
    val bottomPadding = 64.dp + 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.hazeSource(hazeState)) {
            CompositionLocalProvider(LocalBottomBarPadding provides bottomPadding) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
                    exitTransition = { fadeOut(tween(120)) },
                ) {
                    composable<HomeRoute> {
                        HomeScreen(onOpenDownloads = { navController.navigateTopLevel(DownloadsRoute) })
                    }
                    composable<DownloadsRoute> { DownloadsScreen() }
                    composable<LibraryRoute> { LibraryScreen() }
                    composable<SettingsRoute> { SettingsScreen(onOpenAbout = { navController.navigate(AboutRoute) }) }
                    composable<AboutRoute> { AboutScreen(onBack = { navController.popBackStack() }) }
                }
            }
        }

        // Fade content out under the status bar so scrolled text never collides with the clock and icons.
        Box(
            Modifier
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
                    ),
                ),
        )

        GlassNavBar(
            items = navItems,
            selectedIndex = selectedIndex,
            onSelect = { navController.navigateTopLevel(topLevelRoutes[it]) },
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
