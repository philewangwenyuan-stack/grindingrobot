package com.sinelynx.grindingrobot.core.database.datasource

import com.sinelynx.grindingrobot.core.database.dao.MapDao
import com.sinelynx.grindingrobot.core.database.entity.MapEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class MapDataSource @Inject constructor(
    private val mapDao: MapDao
) {

    suspend fun insert(map: MapEntity) {
        mapDao.insert(map)
    }

    suspend fun insertAll(maps: List<MapEntity>) {
        mapDao.insertAll(maps)
    }

    suspend fun update(map: MapEntity) {
        mapDao.update(map)
    }

    suspend fun delete(map: MapEntity) {
        mapDao.delete(map)
    }

    suspend fun deleteById(id: Long) {
        mapDao.deleteById(id)
    }

    suspend fun deleteByMapId(mapId: String) {
        mapDao.deleteByMapId(mapId)
    }

    suspend fun deleteAll() {
        mapDao.deleteAll()
    }

    fun getAllMaps(): Flow<List<MapEntity>> = mapDao.getAllMaps()

    fun getMapCount(): Flow<Int> = mapDao.getMapCount()

    suspend fun getMapById(id: Long): MapEntity? = mapDao.getMapById(id)

    suspend fun getMapByMapId(mapId: String): MapEntity? = mapDao.getMapByMapId(mapId)
}
