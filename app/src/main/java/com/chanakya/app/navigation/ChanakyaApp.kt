package com.chanakya.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chanakya.app.R
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chanakya.app.di.LocalAppContainer
import kotlinx.coroutines.flow.first

/** A bottom-navigation destination. */
private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Destinations.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevelDestination(Destinations.APPS, R.string.nav_apps, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(Destinations.REPORTS, R.string.nav_reports, Icons.Filled.DateRange),
    TopLevelDestination(Destinations.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/**
 * The root composable. Hosts the navigation graph and shows a floating bottom bar on the
 * top-level screens.
 *
 * The bar is built from a plain [Row] rather than Material3's `NavigationBar`, which is
 * fixed at 80dp tall - noticeably too heavy for this layout.
 */
@Composable
fun ChanakyaApp() {
    val navController = rememberNavController()

    // Ask for the notification permission once, so the daily summary can appear.
    NotificationPermissionEffect()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in Destinations.topLevel

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                ChanakyaBottomBar(
                    currentRoute = currentDestination?.route,
                    onSelect = { route -> navController.navigateToTopLevel(route) },
                )
            }
        },
    ) { innerPadding ->
        ChanakyaNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * The floating navigation pill: compact, inset from the screen edges, with a soft
 * shadow so content scrolls behind it.
 */
@Composable
private fun ChanakyaBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Keep the pill clear of the system navigation controls. Material3's
            // NavigationBar applied this for us; a plain Surface has to ask for it.
            // navigationBarsPadding covers all edges, so it also handles landscape,
            // where the controls sit to the side rather than at the bottom.
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            topLevelDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                BottomBarItem(
                    destination = destination,
                    selected = selected,
                    onClick = { onSelect(destination.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One item in the floating bar: a compact icon over a small label. */
@Composable
private fun BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * Navigate to a top-level destination, reusing the existing instance and avoiding a
 * growing back stack.
 */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Total height of the bar's content. */
private val BAR_HEIGHT = 54.dp

/** Size of each navigation icon. */
private val ICON_SIZE = 22.dp

/**
 * Requests the notification permission once on Android 13+, recording that the prompt
 * has been shown so the user is not asked again on every launch.
 */
@Composable
private fun NotificationPermissionEffect() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The result is reflected in the system settings either way. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return@LaunchedEffect
        if (container.settingsRepository.notificationPromptShown.first()) return@LaunchedEffect

        container.settingsRepository.setNotificationPromptShown()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
