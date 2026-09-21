package com.sinelynx.grindingrobot.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.sinelynx.grindingrobot.feature.connect.navigation.connectGraph
import com.sinelynx.grindingrobot.feature.main.navigation.mainHomeGraph
import com.sinelynx.grindingrobot.navigation.routes.MapRoutes
import com.sinelynx.grindingrobot.feature.map.navigation.mapGraph
import kotlinx.coroutines.flow.collectLatest

/**
 *
 * @param navigator
 * @param modifier
 * @author Dreamj
 */
@Composable
fun AppNavHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    LaunchedEffect(navController) {
        navigator.navigationEvents.collectLatest { event ->
            navController.handleNavigationEvent(event)
        }
    }

    NavHost(
        navController = navController,
        startDestination = MapRoutes.Home,
        modifier = modifier
    ) {
        mainHomeGraph(navController = navController)
        mapGraph(navController = navController)
        connectGraph(navController = navController)
    }
}

