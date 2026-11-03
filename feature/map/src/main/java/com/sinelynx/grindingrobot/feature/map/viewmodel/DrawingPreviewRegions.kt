package com.sinelynx.grindingrobot.feature.map.viewmodel

import com.sinelynx.grindingrobot.core.model.state.MapPreviewRegionPayload

/** Replace only the drawing layer; public obstacles keep their existing semantics. */
internal fun drawingPreviewRegions(mapId:String?,regions:List<MapPreviewRegionPayload>):List<MapPreviewRegionItem> {
    val draft=DrawingWorkflowDrafts.get(mapId)?:return regions.map { it.toUiItem() }
    return regions.filter { it.source!="drawing" }.map { it.toUiItem() }+draft.payload.foundationsList.map { p ->
        MapPreviewRegionItem(p.regionId,p.name,p.pointsList.map { MapPreviewPointItem(it.x,it.y) },p.enabled,p.source,p.inflationM,p.drawingActionValue)
    }
}
