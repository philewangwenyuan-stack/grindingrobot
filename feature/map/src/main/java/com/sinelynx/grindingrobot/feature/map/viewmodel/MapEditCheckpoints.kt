package com.sinelynx.grindingrobot.feature.map.viewmodel

import android.content.Context
import android.util.AtomicFile
import androidx.compose.ui.geometry.Offset
import com.sinelynx.grindingrobot.core.model.state.MapEditSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val editCheckpointJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Local unsubmitted operations complement device-persisted edits. Never reuse across base revisions. */
@Singleton
class MapEditCheckpoints @Inject constructor(@ApplicationContext private val context: Context) {
    private val written = mutableMapOf<String, String>()
    private fun file(mapId: String?, step: String): AtomicFile? {
        val binding = MapEditSession.forMap(mapId) ?: return null
        val key = "${binding.mapId}:${binding.baseRevision}:$step"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(context.filesDir, "map-edit-checkpoints/$digest.json"))
    }
    @Synchronized fun read(mapId: String?, step: String): String? {
        val file = file(mapId, step) ?: return null
        return try { file.openRead().bufferedReader().use { it.readText() } }
        catch (_: java.io.FileNotFoundException) { null }
    }
    @Synchronized fun write(mapId: String?, step: String, value: String) {
        val file = file(mapId, step) ?: return
        if (written[file.baseFile.path] == value) return
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try { stream.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(stream); written[file.baseFile.path] = value }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}

@Serializable
internal data class SavedErase(
    val id: Long, val left: Float, val top: Float, val right: Float, val bottom: Float,
    val confirmed: Boolean, val corners: List<Pair<Float, Float>>, val polygons: List<List<Pair<Float, Float>>>
) {
    fun restore() = EraseRectArea(id, left, top, right, bottom, confirmed,
        corners.map { Offset(it.first, it.second) }, polygons.map { p -> p.map { Offset(it.first, it.second) } })
}
internal fun EraseRectArea.saved() = SavedErase(id, left, top, right, bottom, confirmed,
    alignedCorners.map { it.x to it.y }, bitmapPolygons.map { p -> p.map { it.x to it.y } })

@Serializable
internal data class Step2Checkpoint(
    val batchId: String, val nextId: Long, val angle: Float,
    val areas: List<SavedErase>, val undo: List<SavedErase>, val redo: List<SavedErase>,
    val acknowledged: List<Pair<Long, Int>>
)

@Serializable
internal data class Step3Checkpoint(
    val draft: WorkspaceDraft, val panelMode: WorkspacePanelMode,
    val editingRegion: String?, val pendingRegion: String?, val pendingObstacles: List<String>,
    val direction: String, val nextId: Long, val nextProtocolId: Long
)
