package com.kail.location.models

/**
 * 历史定位记录的数据类。
 *
 * @property id 记录唯一标识。
 * @property name 位置名称或描述。
 * @property longitudeWgs84 WGS84 经度。
 * @property latitudeWgs84 WGS84 纬度。
 * @property timestamp 创建或模拟的时间戳。
 * @property longitudeBd09 BD-09 经度。
 * @property latitudeBd09 BD-09 纬度。
 * @property displayTime 展示用时间字符串。
 * @property displayWgs84 展示用 WGS84 坐标字符串。
 * @property displayBd09 展示用 BD-09 坐标字符串。
 */
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
