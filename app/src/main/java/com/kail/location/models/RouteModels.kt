package com.kail.location.models

/**
 * 路线信息的数据类。
 *
 * @property id 路线唯一标识。
 * @property startName 起点名称。
 * @property endName 终点名称。
 * @property distance 距离描述（例如 "104米"）。
 */
data class RouteInfo(
    val id: String,
    val startName: String,
    val endName: String,
    val distance: String = "104米"
)

/**
 * 路线模拟设置的数据类。
 *
 * @property speed 模拟速度（km/h）。
 * @property mode 交通方式（如步行、骑行）。
 * @property speedFluctuation 是否模拟速度波动。
 * @property stepFreqSimulation 是否模拟步频。
 * @property stepFreq 步频数值。
 * @property isLoop 是否循环模拟。
 */
data class SimulationSettings(
    var speed: Float = 6.5f,
    var mode: TransportMode = TransportMode.Bike,
    var speedFluctuation: Boolean = true,
    var stepFreqSimulation: Boolean = false,
    var stepFreq: Float = 2.5f,
    var isLoop: Boolean = true
)

/**
 * 模拟使用的交通方式枚举。
 */
enum class TransportMode {
    Walk, Run, Bike, Car, Plane
}
