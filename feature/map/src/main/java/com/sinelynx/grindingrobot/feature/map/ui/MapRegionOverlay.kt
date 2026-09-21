package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sinelynx.grindingrobot.core.model.state.TaskMapDisplaySnapshot
import com.sinelynx.grindingrobot.feature.map.viewmodel.toUiItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewRegionItem
import kotlin.math.cos
import kotlin.math.sin

internal enum class MapRegionOverlayKind {
    Erase,
    Work,
    Obstacle
}

internal val MapEraseBorderColor = Color(0xFFFFFFFF)
internal val MapWorkBorderColor = Color(0xFF24BE1C)
// 编辑态保留红色；回显态使用独立颜色，避免影响临时禁区编辑。
internal val MapObstacleBorderColor = Color(0xFFD82B2A)
private val MapConfirmedObstacleColor = Color(0xFF808080)

/**
 * 地图坐标（米）→ 未施加页面变换的图片像素：减原点、撤销地图朝向、除分辨率并翻转 Y。
 * 最后乘“实际图片尺寸 / 响应地图尺寸”；不额外乘 previewScaleX/Y，页面负责显示旋转和适配。
 */
fun worldPointToBitmapOffset(
    point: MapPreviewPointItem,
    geo: MapGeo,
    mapImageSize: Pair<Int, Int>,
    bitmapSize: Pair<Int, Int>
): Offset? {
    if (
        geo.resolution <= 0f || geo.mapWidth <= 0 || geo.mapHeight <= 0 ||
        mapImageSize.first <= 0 || mapImageSize.second <= 0 ||
        bitmapSize.first <= 0 || bitmapSize.second <= 0
    ) {
        return null
    }
    val dx = point.x - geo.originX.toFloat()
    val dy = point.y - geo.originY.toFloat()
    val radians = Math.toRadians(geo.headingDeg.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val mapMetersX = cosine * dx + sine * dy
    val mapMetersY = -sine * dx + cosine * dy
    val mapPixelX = mapMetersX / geo.resolution
    val mapPixelY = geo.mapHeight - mapMetersY / geo.resolution
    return Offset(
        x = mapPixelX * bitmapSize.first / mapImageSize.first.toFloat(),
        y = mapPixelY * bitmapSize.second / mapImageSize.second.toFloat()
    )
}

/**
 * pointMapper 决定输出是位图还是屏幕坐标；contentScale 只抵消画布缩放造成的描边变粗。
 * 工作区填充可由调用方关闭；擦除区仍为白色，drawBorder=false 时不会留下擦除边框。
 */
internal fun DrawScope.drawMapRegionOverlays(
    regions: List<MapPreviewRegionItem>,
    kind: MapRegionOverlayKind,
    pointMapper: (MapPreviewPointItem) -> Offset?,
    contentScale: Float = 1f,
    selectedRegionIds: Set<String> = emptySet(),
    drawBorder: Boolean = true,
    workFillColor: Color = Color.White
) {
    val safeScale = contentScale.coerceAtLeast(0.0001f)
    regions.asSequence()
        .filter { it.enabled }
        .forEach { region ->
            val points = region.points.mapNotNull(pointMapper)
            if (points.size < 3) return@forEach
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                close()
            }
            val fillColor = when (kind) {
                MapRegionOverlayKind.Erase -> Color.White
                MapRegionOverlayKind.Work -> workFillColor
                MapRegionOverlayKind.Obstacle -> MapConfirmedObstacleColor
            }
            if (fillColor.alpha > 0f) {
                drawPath(path = path, color = fillColor)
            }
            val borderColor = when (kind) {
                MapRegionOverlayKind.Erase -> MapEraseBorderColor
                MapRegionOverlayKind.Work -> MapWorkBorderColor
                MapRegionOverlayKind.Obstacle -> MapConfirmedObstacleColor
            }
            val selected = kind == MapRegionOverlayKind.Work && region.regionId in selectedRegionIds
            if (drawBorder) drawPath(
                path = path,
                color = borderColor,
                style = Stroke(width = (if (selected) 2.dp else 1.dp).toPx() / safeScale)
            )
        }
}

internal fun DrawScope.drawConfirmedObstaclePath(path: Path, contentScale: Float = 1f) {
    val safeScale = contentScale.coerceAtLeast(0.0001f)
    drawPath(path = path, color = MapConfirmedObstacleColor)
    drawPath(
        path = path,
        color = MapConfirmedObstacleColor,
        style = Stroke(width = 1.dp.toPx() / safeScale)
    )
}

/**
 * 设备任务页的区域底层：擦除区 → 所选工作区 → 公共禁区 → 本轮临时禁区。
 * 工作区只描边，擦除区白色无描边；裁剪区仍保留在 snapshot.regions 中，但不参与本层绘制。
 * 公共禁区不随工作区选择过滤，临时禁区来自任务配置快照，不向地图区域接口反向持久化。
 *
 * mapper 已输出最终屏幕坐标，因此这里不再缩放 Canvas，描边保持屏幕尺寸。
 * 调用方必须在本层之后绘制规划路径，再调用 drawTaskDisplayAnchors，最后绘制实际轨迹和小车。
 */
fun DrawScope.drawTaskDisplayRegions(
    snapshot: TaskMapDisplaySnapshot, geo: MapGeo, bitmapSize: Pair<Int, Int>,
    rotationDeg: Float, viewportScale: Float = 1f, viewportOffset: Offset = Offset.Zero
) {
    val regions = snapshot.regions ?: return
    val mapper: (MapPreviewPointItem) -> Offset? = { point ->
        taskDisplayPoint(point, geo, bitmapSize, rotationDeg, viewportScale, viewportOffset)
    }
    drawMapRegionOverlays(regions.eraseRegions.map { it.toUiItem() }, MapRegionOverlayKind.Erase,
        mapper, drawBorder = false)
    drawMapRegionOverlays(regions.workRegions.filter { it.region.regionId in snapshot.selectedRegionIds }
        .map { it.region.toUiItem() }, MapRegionOverlayKind.Work, mapper,
        selectedRegionIds = snapshot.selectedRegionIds, workFillColor = Color.Transparent)
    drawMapRegionOverlays(regions.obstacleRegions.map { it.toUiItem() }, MapRegionOverlayKind.Obstacle, mapper)
    drawMapRegionOverlays(snapshot.temporaryObstacles.map { obstacle ->
        MapPreviewRegionItem(regionId = obstacle.regionId, regionName = obstacle.name,
            points = obstacle.points.map { MapPreviewPointItem(it.x, it.y) }, enabled = true)
    }, MapRegionOverlayKind.Obstacle, mapper)
}

/**
 * 仅绘制启用且被选中的工作区起终点。区域解析层已按 available 将不可用姿态转为 null，
 * 此处直接使用接口 x/y，不从多边形外接矩形或规划路径首尾推导位置。
 *
 * 所有起点画完后再画所有终点，重叠时终点位于上方；绿色/橙色圆点半径固定为屏幕 6dp。
 * 坐标使用与区域完全相同的 taskDisplayPoint 链路，缩放改变位置，不改变圆点半径。
 */
fun DrawScope.drawTaskDisplayAnchors(
    snapshot: TaskMapDisplaySnapshot, geo: MapGeo, bitmapSize: Pair<Int, Int>,
    rotationDeg: Float, viewportScale: Float = 1f, viewportOffset: Offset = Offset.Zero
) {
    val regions = snapshot.regions?.workRegions.orEmpty()
        .filter { it.region.enabled && it.region.regionId in snapshot.selectedRegionIds }
    fun project(x: Float, y: Float) = taskDisplayPoint(MapPreviewPointItem(x, y), geo, bitmapSize,
        rotationDeg, viewportScale, viewportOffset)
    regions.forEach { region -> region.startPose?.let { project(it.x, it.y) }?.let {
        drawCircle(Color(0xFF33C461), 6.dp.toPx(), it)
    } }
    regions.forEach { region -> region.endPose?.let { project(it.x, it.y) }?.let {
        drawCircle(Color(0xFFFF9800), 6.dp.toPx(), it)
    } }
}

/**
 * 统一投影链：世界米坐标 → 原始地图网格 → 实际图片像素 → 居中 Fit/显示旋转 → 手势缩放/平移。
 * headingDeg 在 worldPointToBitmapOffset 中撤销，rotationDeg 是页面显示角，两者不能合并或重复施加。
 * 图片尺寸比已包含在第一阶段；不额外乘 previewScale，也不使用路径响应的 alignment_yaw。
 */
private fun DrawScope.taskDisplayPoint(
    point: MapPreviewPointItem, geo: MapGeo, bitmapSize: Pair<Int, Int>, rotationDeg: Float,
    viewportScale: Float, viewportOffset: Offset
): Offset? = worldPointToBitmapOffset(point, geo, geo.mapWidth to geo.mapHeight, bitmapSize)
    ?.takeIf { it.x.isFinite() && it.y.isFinite() }
    ?.let { taskBitmapPointToFittedPoint(it, size, bitmapSize, rotationDeg) }
    ?.let { it * viewportScale + viewportOffset }
