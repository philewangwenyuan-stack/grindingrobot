package com.sinelynx.grindingrobot.feature.connect.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.sinelynx.grindingrobot.feature.connect.ui.ConnectWifiRoute
import com.sinelynx.grindingrobot.navigation.routes.ConnectRoutes
import com.sinelynx.grindingrobot.navigation.routes.MapRoutes

fun NavGraphBuilder.connectGraph(
    navController: NavHostController
) {
    composable<ConnectRoutes.Wifi> {
        ConnectWifiRoute(
            onBackHomeClick = { navController.popBackStack() },
            onWifiConnected = {
                navController.navigate(MapRoutes.Home) {
                    launchSingleTop = true
                    popUpTo<ConnectRoutes.Wifi> {
                        inclusive = true
                    }
                }
            }
        )
    }
}
