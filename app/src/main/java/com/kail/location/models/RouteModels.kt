package com.kail.location.models

data class RouteInfo(
    val id: String,
    val startName: String,
    val endName: String,
    val distance: String = "104米"
)

data class SimulationSettings(
    var speed: Float = 6.5f,
    var mode: TransportMode = TransportMode.Bike,
    var speedFluctuation: Boolean = true,
    var stepFreqSimulation: Boolean = false,
    var stepFreq: Float = 2.5f,
    var isLoop: Boolean = true
)

enum class TransportMode {
    Walk, Run, Bike, Car, Plane
}
