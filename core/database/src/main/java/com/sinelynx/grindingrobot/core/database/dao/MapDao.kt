package com.sinelynx.grindingrobot.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sinelynx.grindingrobot.core.database.entity.MapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MapDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(map: MapEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(maps: List<MapEntity>)

    @Update
    suspend fun update(map: MapEntity)

    @Delete
    suspend fun delete(map: MapEntity)

    @Query("DELETE FROM mapTask WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mapTask WHERE mapId = :mapId")
    suspend fun deleteByMapId(mapId: String)

    @Query("DELETE FROM mapTask")
    suspend fun deleteAll()

    @Query("SELECT * FROM mapTask ORDER BY mapTime DESC")
    fun getAllMaps(): Flow<List<MapEntity>>

    @Query("SELECT COUNT(*) FROM mapTask")
    fun getMapCount(): Flow<Int>

    @Query("SELECT * FROM mapTask WHERE id = :id LIMIT 1")
    suspend fun getMapById(id: Long): MapEntity?

    @Query("SELECT * FROM mapTask WHERE mapId = :mapId LIMIT 1")
    suspend fun getMapByMapId(mapId: String): MapEntity?
}
