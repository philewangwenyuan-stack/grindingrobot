package com.sinelynx.grindingrobot.feature.map.viewmodel

/** Last acknowledged erase overlay for the active frozen session, displayed before Step3's query. */
internal object MapWorkflowErasePreview {
    private var sessionId: Long? = null
    private var regions: List<MapPreviewRegionItem> = emptyList()
    fun store(session: Long, value: List<MapPreviewRegionItem>) { sessionId = session; regions = value }
    fun forSession(session: Long): List<MapPreviewRegionItem> = if (sessionId == session) regions else emptyList()
}
