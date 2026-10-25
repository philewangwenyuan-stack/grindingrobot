from pathlib import Path

p=Path('feature/device/src/main/java/com/sinelynx/grindingrobot/feature/device/ui/DeviceStatusScreen.kt')
s=p.read_text(encoding='utf-8')
s=s.replace('import com.sinelynx.grindingrobot.feature.map.ui.grindingDiscWorldCenter\n','')
s=s.replace('import com.sinelynx.grindingrobot.feature.map.ui.GRINDING_DISC_DIAMETER_M\n','')
s=s.replace('import com.sinelynx.grindingrobot.feature.device.viewmodel.TaskPoseUiItem\n',
            'import com.sinelynx.grindingrobot.feature.device.viewmodel.TaskPoseUiItem\nimport com.sinelynx.grindingrobot.feature.device.viewmodel.TaskTrajectorySnapshot\n')
for name,next_name,label in [('ZoomableTaskTrajectoryOverlay','TaskTrajectoryOverlay','Box'),
                             ('TaskTrajectoryOverlay','TaskTrajectoryDrawPoint','BoxWithConstraints')]:
    start=s.index('private fun '+name+'(')
    end=s.index('private '+('fun ' if next_name=='TaskTrajectoryOverlay' else 'data class ')+next_name,start)
    block=s[start:end]
    block=block.replace('trajectory: List<TaskPoseUiItem>','trajectory: TaskTrajectorySnapshot')
    block=block.replace('trajectory.isEmpty() || ','')
    a=block.index('    val bitmapTrajectory = remember(')
    b=block.index('    val density = LocalDensity.current',a)
    block=block[:a]+'''    val drawing = remember { TaskTrajectoryDrawingCache() }
    drawing.update(trajectory, mapTransform, bitmapWidth, bitmapHeight)
    val robotPose = trajectory.latestPose
    val robotOffset = robotPose?.toTaskBitmapPoint(mapTransform, bitmapWidth, bitmapHeight)
'''+block[b:]
    a=block.index('        Canvas(modifier = Modifier.fillMaxSize()) {')
    if name=='ZoomableTaskTrajectoryOverlay':
        b=block.index('        if (viewportSize.width <= 0',a)
        block=block[:a]+'''        Canvas(modifier = Modifier.fillMaxSize()) {
            drawing.draw(this, mapTransform, bitmapWidth, bitmapHeight, zoom, pan)
        }

'''+block[b:]
        block=block.replace('if (viewportSize.width <= 0 || viewportSize.height <= 0 || mapBounds == null)',
                            'if (viewportSize.width <= 0 || viewportSize.height <= 0 || mapBounds == null || robotPose == null || robotOffset == null)')
    else:
        b=block.index('        val robotCenter = robotPoint.offset',a)
        block=block[:a]+'''        Canvas(modifier = Modifier.fillMaxSize()) {
            drawing.draw(this, mapTransform, bitmapWidth, bitmapHeight)
        }

        if (robotPose == null || robotOffset == null) return@BoxWithConstraints
'''+block[b:]
    block=block.replace('robotPoint.offset','robotOffset').replace('robotPoint.headingDeg','robotPose.headingDeg')
    assert 'bitmapTrajectory' not in block and 'robotPoint' not in block
    s=s[:start]+block+s[end:]
a=s.index('private data class TaskTrajectoryDrawPoint(')
b=s.index('\n)',a)+2
s=s[:a]+s[b:]
p.write_text(s,encoding='utf-8')
print('Replaced both trajectory overlays; preserved independent robot marker sizing and projection helpers')
