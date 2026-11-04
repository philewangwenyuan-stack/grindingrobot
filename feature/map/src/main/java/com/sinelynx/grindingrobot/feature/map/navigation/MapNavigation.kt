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
    // “上一步”复用固定底图；退出仅清理 APP 显示缓存，设备草稿保留，重新进入时恢复。
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
                        mapId = mapViewModel.editingMapId ?: route.mapId,
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
                    MapRoutes.DrawingImport(
                        mapId = route.mapId,
                        mapName = route.mapName
                    )
                )
            }
        )
    }

    composable<MapRoutes.DrawingImport> { backStackEntry ->
        val route = backStackEntry.toRoute<MapRoutes.DrawingImport>()
        val drawingViewModel: com.sinelynx.grindingrobot.feature.map.viewmodel.DrawingImportViewModel = hiltViewModel()
        com.sinelynx.grindingrobot.feature.map.ui.DrawingImportScreen(drawingViewModel, route.mapId,
            onPrevious = { navController.popBackStack() },
            onContinue = { navController.navigate(MapRoutes.Step3(route.mapId,route.mapName)) },
            onCancel = {
                MapBuildSessionStream.clear()
                navController.navigate(MapRoutes.Home) { popUpTo(MapRoutes.Home) { inclusive = false }; launchSingleTop = true }
            })
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
