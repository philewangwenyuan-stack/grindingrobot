package com.sinelynx.grindingrobot.core.model.state

/** Device-backed draft identity, scoped to an explicit map rather than the global live map. */
data class MapEditBinding(val mapId: String, val baseRevision: String, val editVersion: Int)

object MapEditSession {
    @Volatile var binding: MapEditBinding? = null
        private set
    @Synchronized fun adopt(value: MapEditBinding?) { binding = value }
    @Synchronized fun edited(mapId: String, baseRevision: String, version: Int) {
        binding = binding?.let {
            if (it.mapId == mapId && it.baseRevision == baseRevision && version >= it.editVersion)
                it.copy(editVersion = version) else it
        }
    }
    fun forMap(mapId: String?): MapEditBinding? = binding?.takeIf { it.mapId == mapId }
}
