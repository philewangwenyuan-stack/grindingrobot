package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.MapPreviewPointPayload
import com.sinelynx.grindingrobot.core.model.state.MapPreviewRegionPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject

internal data class MapPreviewOverlayRegions(
    val workRegions: List<MapPreviewRegionPayload> = emptyList(),
    val obstacleRegions: List<MapPreviewRegionPayload> = emptyList(),
    val eraseRegions: List<MapPreviewRegionPayload> = emptyList()
)

internal object MapPreviewOverlayParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(overlayJson: String): MapPreviewOverlayRegions {
        if (overlayJson.isBlank()) return MapPreviewOverlayRegions()
        return runCatching { parseOrThrow(overlayJson) }.getOrDefault(MapPreviewOverlayRegions())
    }

    internal fun parseOrThrow(overlayJson: String): MapPreviewOverlayRegions {
        val root = json.parseToJsonElement(overlayJson).jsonObject
        val regions = root["regions"] as? JsonObject ?: root
        return MapPreviewOverlayRegions(
            workRegions = parseRegions(regions, "work_regions", "workRegions", "work"),
            obstacleRegions = parseRegions(
                regions,
                "obstacle_regions",
                "obstacleRegions",
                "obstacle"
            ),
            eraseRegions = parseRegions(regions, "erase_regions", "eraseRegions", "erase")
        )
    }

    private fun parseRegions(
        regions: JsonObject,
        snakeCaseKey: String,
        camelCaseKey: String,
        fallbackPrefix: String
    ): List<MapPreviewRegionPayload> {
        val items = regions[snakeCaseKey] as? JsonArray
            ?: regions[camelCaseKey] as? JsonArray
            ?: JsonArray(emptyList())
        return buildList {
            for (index in items.indices) {
                val item = items[index] as? JsonObject ?: continue
                if ((item["enabled"] as? JsonPrimitive)?.booleanOrNull == false) continue
                val points = parsePoints(item["points"] as? JsonArray ?: JsonArray(emptyList()))
                if (points.size < 3) continue
                val name = item.stringValue("name", "regionName")
                val id = item.stringValue("region_id", "regionId")
                    .ifBlank { name }
                    .ifBlank { "${fallbackPrefix}_$index" }
                add(
                    MapPreviewRegionPayload(
                        regionId = id,
                        regionName = name,
                        points = points,
                        enabled = true
                    )
                )
            }
        }
    }

    private fun parsePoints(points: JsonArray): List<MapPreviewPointPayload> {
        return buildList {
            for (index in points.indices) {
                val point = points[index] as? JsonObject ?: continue
                val x = (point["x"] as? JsonPrimitive)?.floatOrNull ?: continue
                val y = (point["y"] as? JsonPrimitive)?.floatOrNull ?: continue
                if (!x.isFinite() || !y.isFinite()) continue
                add(MapPreviewPointPayload(x = x, y = y))
            }
        }
    }

    private fun JsonObject.stringValue(primaryKey: String, fallbackKey: String): String {
        return (this[primaryKey] as? JsonPrimitive)?.contentOrNull
            ?: (this[fallbackKey] as? JsonPrimitive)?.contentOrNull
            ?: ""
    }
}
