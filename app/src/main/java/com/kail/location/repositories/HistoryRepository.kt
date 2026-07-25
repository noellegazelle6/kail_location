package com.kail.location.repositories

import com.kail.location.data.local.HistoryDao
import com.kail.location.data.local.HistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val recentRoutes: Flow<List<HistoryEntity>> = historyDao.getRecentRoutes()

    suspend fun addRoute(startName: String, endName: String, startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
        val entity = HistoryEntity(
            startName = startName,
            endName = endName,
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng
        )
        historyDao.insertRoute(entity)
    }

    suspend fun updateFavorite(id: Long, isFavorite: Boolean) {
        historyDao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteRoute(id: Long) {
        historyDao.deleteRoute(id)
    }

    suspend fun updateName(id: Long, startName: String, endName: String) {
        historyDao.updateName(id, startName, endName)
    }

    suspend fun updateTimestamp(id: Long, timestamp: Long) {
        historyDao.updateTimestamp(id, timestamp)
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }
}
