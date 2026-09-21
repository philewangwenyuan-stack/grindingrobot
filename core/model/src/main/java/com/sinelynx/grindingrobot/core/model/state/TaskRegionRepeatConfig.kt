package com.sinelynx.grindingrobot.core.model.state

data class TaskRegionRepeatConfig(
    val regionId: String,
    val repeat: Int
)

data class TaskPolygonPointConfig(
    val x: Float,
    val y: Float
)

data class TaskObstacleRegionConfig(
    val regionId: String,
    val name: String,
    val points: List<TaskPolygonPointConfig>
)
