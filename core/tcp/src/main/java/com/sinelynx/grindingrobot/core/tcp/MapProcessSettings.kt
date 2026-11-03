package com.sinelynx.grindingrobot.core.tcp

import sl_link.SlLink

internal fun buildMapProcessWriteRequest(process: SlLink.MapProcessSettings, mapId: String?, regionId: String) =
    SlLink.SettingsWriteRequest.newBuilder()
        .setProcess(process).setProcessMapId(mapId.orEmpty()).setProcessRegionId(regionId).build()

internal fun mapProcessReply(response: SlLink.SettingsWriteResponse): CommandReply =
    if (response.result == SlLink.ResultCode.RESULT_SUCCESS && (!response.hasProcess() || response.processRegionId.isBlank())) {
        CommandReply(-1, "板端未确认区域工艺保存，请同步升级板端")
    } else CommandReply(response.resultValue, response.message)
