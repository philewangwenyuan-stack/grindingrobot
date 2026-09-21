package com.sinelynx.grindingrobot.feature.map.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sinelynx.grindingrobot.navigation.routes.MapRoutes
import com.sinelynx.grindingrobot.feature.map.ui.MapScreenStep1
import com.sinelynx.grindingrobot.feature.map.ui.MapScreenStep2
import com.sinelynx.grindingrobot.feature.map.ui.MapScreenStep3
import com.sinelynx.grindingrobot.feature.map.ui.MapScreenStep4
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep2ViewModel
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep3ViewModel
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep4ViewModel
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapViewModel
import com.sinelynx.grindingrobot.feature.map.viewmodel.Step4SaveMapDraft
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.model.state.MapBuildSessionStream

/**
 * 地图创建跳转管理
 *
 * @param navController
 */
fun NavGraphBuilder.mapGraph(
    navController: NavHostController
) {
    // 普通“上一步”保留会话以复用底图；各取消入口清理快照，重新进入 Step1 时由其 ViewModel 开启新会话。
    composable<MapRoutes.Step1> { backStackEntry ->
        val route = backStackEntry.toRoute<MapRoutes.Step1>()
        val mapViewModel: MapViewModel = hiltViewModel()
        MapScreenStep1(
            viewModel = mapViewModel,
            mapId = route.mapId,
            onCancelBuild = {
                MapBuildSessionStream.clear()
                navController.navigate(MapRoutes.Home) {
                    popUpTo(MapRoutes.Home) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onNextStep = {
                navController.navigate(
                    MapRoutes.Step2(
                        mapId = route.mapId,
                        mapName = route.mapName
                    )
                )
            }
        )
    }

    composable<MapRoutes.Step2> { backStackEntry ->
        val route = backStackEntry.toRoute<MapRoutes.Step2>()
        val mapScreenStep2ViewModel: MapScreenStep2ViewModel = hiltViewModel()
        MapScreenStep2(
            viewModel = mapScreenStep2ViewModel,
            mapId = route.mapId,
            onCancelBuild = {
                MapBuildSessionStream.clear()
                navController.navigate(MapRoutes.Home) {
                    popUpTo(MapRoutes.Step1(mapId = route.mapId, mapName = route.mapName)) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onPreviousStep = { navController.popBackStack() },
            onNextStep = {
                LogUtils.d("MapAlignment_Debug: MapNavigation composable Step2 onNextStep callback 触发！正在跳转到 Step3...")
                navController.navigate(
                    MapRoutes.Step3(
                        mapId = route.mapId,
                        mapName = route.mapName
                    )
                )
            }
        )
    }

    composable<MapRoutes.Step3> { backStackEntry ->
        val route = backStackEntry.toRoute<MapRoutes.Step3>()
        val mapScreenStep3ViewModel: MapScreenStep3ViewModel = hiltViewModel()
        MapScreenStep3(
            viewModel = mapScreenStep3ViewModel,
            mapId = route.mapId,
            onCancelBuild = {
                MapBuildSessionStream.clear()
                navController.navigate(MapRoutes.Home) {
                    popUpTo(MapRoutes.Step1(mapId = route.mapId, mapName = route.mapName)) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onPreviousStep = { navController.popBackStack() },
            onNextStep = { scanDirection ->
                navController.navigate(
                    MapRoutes.Step4(
                        mapId = route.mapId,
                        mapName = route.mapName,
                        scanDirection = scanDirection
                    )
                )
            }
        )
    }

    composable<MapRoutes.Step4> { backStackEntry ->
        val route = backStackEntry.toRoute<MapRoutes.Step4>()
        val mapScreenStep4ViewModel: MapScreenStep4ViewModel = hiltViewModel()
        MapScreenStep4(
            viewModel = mapScreenStep4ViewModel,
            mapId = route.mapId,
            initialMapName = route.mapName,
            scanDirection = route.scanDirection,
            onCancelBuild = {
                MapBuildSessionStream.clear()
                navController.navigate(MapRoutes.Home) {
                    popUpTo(MapRoutes.Step1(mapId = route.mapId, mapName = route.mapName)) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onPreviousStep = { navController.popBackStack() },
            onSave = { _: Step4SaveMapDraft ->
                navController.navigate(MapRoutes.Home) {
                    popUpTo(MapRoutes.Home) { inclusive = false }
                    launchSingleTop = true
                }
            }
        )
    }
}
