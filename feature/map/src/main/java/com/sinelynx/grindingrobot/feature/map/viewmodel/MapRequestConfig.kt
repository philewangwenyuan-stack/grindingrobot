package com.sinelynx.grindingrobot.feature.map.viewmodel

internal const val MAP_TCP_REQUEST_TIMEOUT_MS = 180_000L
// Leave time for the managed ROS launch, the 60 s post-launch health budget,
// and owned-launch cleanup before reporting a map-mode timeout.
internal const val MAP_MODE_REQUEST_TIMEOUT_MS = 150_000L
