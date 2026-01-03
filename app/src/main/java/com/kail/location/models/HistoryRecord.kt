package com.kail.location.models

data class HistoryRecord(
    val id: Int,
    val name: String,
    val longitudeWgs84: String,
    val latitudeWgs84: String,
    val timestamp: Long,
    val longitudeBd09: String,
    val latitudeBd09: String,
    val displayTime: String,
    val displayWgs84: String,
    val displayBd09: String
)
