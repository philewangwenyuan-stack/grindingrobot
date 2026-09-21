package com.sinelynx.grindingrobot.feature.main.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sinelynx.grindingrobot.feature.main.ui.HomeScreen
import com.sinelynx.grindingrobot.feature.main.ui.TaskReplayScreen
import com.sinelynx.grindingrobot.feature.main.viewmodel.HomeViewModel
import com.sinelynx.grindingrobot.feature.main.viewmodel.TaskRecordViewModel
import com.sinelynx.grindingrobot.feature.main.viewmodel.TaskReplayViewModel
import com.sinelynx.grindingrobot.navigation.routes.ConnectRoutes
import com.sinelynx.grindingrobot.navigation.routes.MapRoutes

/**
 * 应用主壳（顶栏、左侧模块切换）与地图首页入口。
 */
fun NavGraphBuilder.mainHomeGraph(navController: NavHostController) {
    composable<MapRoutes.Home> {
        val homeViewModel: HomeViewModel = hiltViewModel()
        HomeScreen(
            viewModel = homeViewModel,
            onAddMap = {
                navController.navigate(MapRoutes.Step1(mapId = MapRoutes.LIVE_MAP_ID))
            },
            onEditMap = { mapId, mapName ->
                navController.navigate(MapRoutes.Step1(mapId = mapId, mapName = mapName))
            },
            onClose = { navController.navigate(ConnectRoutes.Wifi) }
        )
    }

    composable<MapRoutes.TaskReplay> { backStackEntry ->
        val route = backStackEntry.toRoute<MapRoutes.TaskReplay>()
        val taskReplayViewModel: TaskReplayViewModel = hiltViewModel()
        
        LaunchedEffect(route.executionId, route.taskId) {
            taskReplayViewModel.load(route.executionId, route.taskId)
        }
        
        val uiState by taskReplayViewModel.uiState.collectAsStateWithLifecycle()
        TaskReplayScreen(
            uiState = uiState,
            onBack = taskReplayViewModel::navigateBack
        )
    }
}
