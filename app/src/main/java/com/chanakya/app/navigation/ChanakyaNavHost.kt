package com.chanakya.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.chanakya.app.ui.appdetail.AppDetailScreen
import com.chanakya.app.ui.apps.AppsScreen
import com.chanakya.app.ui.home.HomeScreen
import com.chanakya.app.ui.legal.AboutScreen
import com.chanakya.app.ui.legal.PrivacyScreen
import com.chanakya.app.ui.legal.TermsScreen
import com.chanakya.app.ui.networkdetail.NetworkDetailScreen
import com.chanakya.app.ui.reports.ReportsScreen
import com.chanakya.app.ui.settings.SettingsScreen

/**
 * The navigation graph. Each top-level route is a screen; the app detail screen takes
 * the package name as an argument.
 */
@Composable
fun ChanakyaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
        modifier = modifier,
    ) {
        composable(Destinations.HOME) {
            HomeScreen(onSeeAllApps = { navController.navigate(Destinations.APPS) })
        }
        composable(Destinations.APPS) {
            AppsScreen(onAppClick = { packageName -> navController.navigate(Destinations.appDetail(packageName)) })
        }
        composable(
            route = Destinations.APP_DETAIL,
            arguments = listOf(navArgument(Destinations.ARG_PACKAGE_NAME) { type = NavType.StringType }),
        ) { entry ->
            val packageName = entry.arguments?.getString(Destinations.ARG_PACKAGE_NAME).orEmpty()
            AppDetailScreen(
                packageName = packageName,
                onBack = { navController.popBackStack() },
                onOpenNetwork = { networkId ->
                    navController.navigate(Destinations.networkDetail(networkId))
                },
            )
        }
        composable(
            route = Destinations.NETWORK_DETAIL,
            arguments = listOf(navArgument(Destinations.ARG_NETWORK_ID) { type = NavType.StringType }),
        ) { entry ->
            val networkId = entry.arguments?.getString(Destinations.ARG_NETWORK_ID).orEmpty()
            NetworkDetailScreen(
                networkId = networkId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.REPORTS) {
            ReportsScreen()
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onOpenAbout = { navController.navigate(Destinations.ABOUT) },
                onOpenTerms = { navController.navigate(Destinations.TERMS) },
                onOpenPrivacy = { navController.navigate(Destinations.PRIVACY) },
            )
        }
        composable(Destinations.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.TERMS) {
            TermsScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
