package com.kail.location.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_routes")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startName: String,
    val endName: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
