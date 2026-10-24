package com.sinelynx.grindingrobot.core.tcp

import sl_link.SlLink

/** ChassisSettings uses proto3 scalars without presence: every write is a full snapshot. */
internal fun buildDiscSettingsUpdate(
    current: SlLink.ChassisSettings?, speedRpm: Int, enabled: Boolean,
): SlLink.ChassisSettings? {
    if (current == null || speedRpm < 0) return null
    return current.toBuilder().setDiscSpeedRpm(speedRpm).setDiscEnabled(enabled).build()
}

internal fun buildTurnRatioSettingsUpdate(
    current: SlLink.ChassisSettings?, ratio: Float,
): SlLink.ChassisSettings? {
    if (current == null || !ratio.isFinite() || ratio !in 0.01f..1f) return null
    return current.toBuilder().setMaxTurnSpeedRatio(ratio).build()
}
