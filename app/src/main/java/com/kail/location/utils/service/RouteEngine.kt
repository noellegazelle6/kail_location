package com.kail.location.utils.service

import android.os.SystemClock
import com.baidu.mapapi.model.LatLng
import com.kail.location.geo.GeoMath
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils

/**
 * 负责路线点管理、沿路线前进、距离计算与进度汇报。
 */
class RouteEngine {

    private companion object {
        const val TAG = "RouteEngine"
    }

    private val routePoints: MutableList<Pair<Double, Double>> = mutableListOf()
    private val routeCumulativeDistances: MutableList<Double> = mutableListOf()
    /**
     * 每个途经点的等待时长（秒），与 routePoints 下标一一对应；0 表示不停留。
     */
    private val routeWaitTimes: MutableList<Double> = mutableListOf()
    private var totalDistance: Double = 0.0
    private var routeIndex = 0
    private var routeLoop = false
    private var segmentProgressMeters = 0.0
    /**
     * True once a non-looping route has reached its end. The engine then parks
     * at the final point (currentLat/Lng stay at the destination) instead of
     * clearing — so location keeps reporting the last simulated spot rather
     * than snapping back to the route's origin.
     */
    private var routeFinished = false

    /**
     * 是否正在某个途经点原地等待（等待期间位置保持在该点）。
     */
    var isWaiting: Boolean = false
        private set
    private var waitRemainingMs: Long = 0L
    private var waitStartedAtMs: Long = 0L

    var currentLng: Double = 0.0
    var currentLat: Double = 0.0
    var currentBea: Float = 0.0f

    val isActive: Boolean get() = routePoints.size >= 2
    /** True only while the route is still progressing (not yet at its end). */
    val isProgressing: Boolean get() = routePoints.size >= 2 && !routeFinished
    val progressRatio: Float
        get() {
            if (routeFinished) return 1f
            if (totalDistance <= 0) return 0f
            val currentDist = if (routeIndex < routeCumulativeDistances.size)
                routeCumulativeDistances[routeIndex] + segmentProgressMeters
            else totalDistance
            return (currentDist / totalDistance).toFloat().coerceIn(0f, 1f)
        }

    fun setupFromArray(routeArray: DoubleArray, coordType: String, waitTimes: DoubleArray? = null) {
        routePoints.clear()
        routeCumulativeDistances.clear()
        routeWaitTimes.clear()
        var i = 0
        while (i + 1 < routeArray.size) {
            val lng = routeArray[i]
            val lat = routeArray[i + 1]
            when (coordType) {
                ServiceConstants.COORD_WGS84 -> routePoints.add(Pair(lng, lat))
                ServiceConstants.COORD_GCJ02 -> {
                    val wgs = MapUtils.gcj02towgs84(lng, lat)
                    routePoints.add(Pair(wgs[0], wgs[1]))
                }
                else -> {
                    val wgs = MapUtils.bd2wgs(lng, lat)
                    routePoints.add(Pair(wgs[0], wgs[1]))
                }
            }
            routeWaitTimes.add(waitTimes?.getOrNull(routePoints.size - 1) ?: 0.0)
            i += 2
        }
        routeIndex = 0
        segmentProgressMeters = 0.0
        routeFinished = false
        isWaiting = false
        waitRemainingMs = 0L
        waitStartedAtMs = 0L
        calculateRouteDistances()
        if (routePoints.isNotEmpty()) {
            val first = routePoints.first()
            currentLng = first.first
            currentLat = first.second
            currentBea = if (routePoints.size >= 2) {
                val second = routePoints[1]
                GeoMath.bearingDegrees(first.first, first.second, second.first, second.second)
            } else {
                0.0f
            }
        }
        KailLog.i(null, TAG, "setupFromArray: ${routePoints.size} points, coordType=$coordType, totalDistance=${"%.1f".format(totalDistance)}m")
    }

    fun setLoop(loop: Boolean) {
        routeLoop = loop
    }

    fun clear() {
        routePoints.clear()
        routeCumulativeDistances.clear()
        routeWaitTimes.clear()
        totalDistance = 0.0
        routeIndex = 0
        segmentProgressMeters = 0.0
        routeFinished = false
        isWaiting = false
        waitRemainingMs = 0L
        waitStartedAtMs = 0L
    }

    /**
     * Append extra points to the end of the route while a simulation is
     * running. This lets the user extend a route after (or before) it reaches
     * its original end: the engine keeps whatever progress it already made,
     * and once it runs out of the original segments it continues straight into
     * the newly appended ones. If the route had already finished and parked at
     * the destination, progression is re-activated.
     *
     * @param newPoints additional points (WGS84) to append after the current last point
     * @param newWaitTimes per-point wait seconds (seconds) parallel to newPoints; missing = 0
     */
    fun appendPoints(newPoints: List<Pair<Double, Double>>, newWaitTimes: DoubleArray? = null) {
        if (newPoints.isEmpty()) return
        val wasFinished = routeFinished
        routePoints.addAll(newPoints)
        newPoints.indices.forEach { idx ->
            routeWaitTimes.add(newWaitTimes?.getOrNull(idx) ?: 0.0)
        }
        calculateRouteDistances()
        routeFinished = false
        if (wasFinished) segmentProgressMeters = 0.0
        if (routeIndex >= routePoints.size - 1) {
            routeIndex = (routePoints.size - newPoints.size - 1).coerceAtLeast(0)
        }
        val idx = routeIndex.coerceIn(0, (routePoints.size - 1).coerceAtLeast(0))
        if (idx + 1 < routePoints.size) {
            val a = routePoints[idx]
            val b = routePoints[idx + 1]
            currentBea = GeoMath.bearingDegrees(a.first, a.second, b.first, b.second)
        }
        KailLog.i(null, TAG, "appendPoints: +${newPoints.size} points, total=${routePoints.size}, totalDistance=${"%.1f".format(totalDistance)}m, finished=$wasFinished")
    }

    fun seekToRatio(ratio: Float) {
        if (routePoints.size < 2 || routeCumulativeDistances.isEmpty()) return
        // Seeking back into the route re-activates progression.
        routeFinished = false
        isWaiting = false
        waitRemainingMs = 0L
        waitStartedAtMs = 0L
        val targetDist = totalDistance * ratio.coerceIn(0f, 1f)
        var idx = 0
        for (i in 0 until routeCumulativeDistances.size - 1) {
            if (targetDist >= routeCumulativeDistances[i] && targetDist < routeCumulativeDistances[i + 1]) {
                idx = i
                break
            }
        }
        if (targetDist >= totalDistance) {
            idx = routePoints.size - 2
        }
        routeIndex = idx
        segmentProgressMeters = targetDist - routeCumulativeDistances[idx]

        val a = routePoints[routeIndex]
        val b = routePoints[(routeIndex + 1).coerceAtMost(routePoints.size - 1)]
        val segLen = segmentLengthMeters(a, b)
        val f = if (segLen > 0) (segmentProgressMeters / segLen) else 0.0
        val dLngDeg = b.first - a.first
        val dLatDeg = b.second - a.second
        currentLng = a.first + dLngDeg * f
        currentLat = a.second + dLatDeg * f
        currentBea = GeoMath.bearingDegrees(a.first, a.second, b.first, b.second)
        KailLog.d(null, TAG, "seekToRatio: ratio=${"%.3f".format(ratio)} -> index=$routeIndex lat=$currentLat lng=$currentLng")
    }

    fun advance(distanceMeters: Double) {
        // Once a non-looping route has finished, stay parked at the final point
        // (currentLat/Lng already hold the destination). Do not advance or
        // reset — the location must remain at the last simulated spot.
        if (routeFinished) return

        // While waiting at a waypoint, hold the position and only count down
        // the wall-clock wait timer. Once elapsed, resume moving this tick.
        if (isWaiting) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - waitStartedAtMs
            if (elapsed >= waitRemainingMs) {
                isWaiting = false
                waitRemainingMs = 0L
                waitStartedAtMs = 0L
                KailLog.i(null, TAG, "advance: wait finished at waypoint #$routeIndex, resuming")
            } else {
                waitRemainingMs -= elapsed
                waitStartedAtMs = now
                return
            }
        }

        var remaining = distanceMeters
        while (remaining > 0 && routePoints.size >= 2) {
            val startIdx = routeIndex
            val endIdx = if (startIdx + 1 < routePoints.size) startIdx + 1 else -1
            if (endIdx == -1) {
                if (routeLoop) {
                    routeIndex = 0
                    segmentProgressMeters = 0.0
                    continue
                } else {
                    parkAtDestination()
                    break
                }
            }
            val a = routePoints[startIdx]
            val b = routePoints[endIdx]
            val segLen = segmentLengthMeters(a, b)
            if (segLen <= 0.0) {
                routeIndex++
                segmentProgressMeters = 0.0
                if (maybeEnterWait(routeIndex)) return
                if (routeIndex >= routePoints.size - 1) {
                    if (routeLoop) {
                        routeIndex = 0
                    } else {
                        parkAtDestination()
                        break
                    }
                }
                continue
            }
            val available = segLen - segmentProgressMeters
            if (remaining >= available) {
                currentLng = b.first
                currentLat = b.second
                currentBea = GeoMath.bearingDegrees(a.first, a.second, b.first, b.second)
                remaining -= available
                routeIndex++
                segmentProgressMeters = 0.0
                // Arrived at waypoint routeIndex; hold there if it has a wait time.
                if (maybeEnterWait(routeIndex)) return
                if (routeIndex >= routePoints.size - 1) {
                    if (routeLoop) {
                        routeIndex = 0
                    } else {
                        parkAtDestination()
                        break
                    }
                }
            } else {
                segmentProgressMeters += remaining
                val f = segmentProgressMeters / segLen
                val dLngDeg = b.first - a.first
                val dLatDeg = b.second - a.second
                currentLng = a.first + dLngDeg * f
                currentLat = a.second + dLatDeg * f
                currentBea = GeoMath.bearingDegrees(a.first, a.second, b.first, b.second)
                remaining = 0.0
            }
        }
    }

    /**
     * 到达某个途经点后若配置了等待时长，则原地等待（位置保持在该点）。
     *
     * @return true 表示已进入等待，调用方应停止本次推进
     */
    private fun maybeEnterWait(index: Int): Boolean {
        val waitSeconds = waitSecondsAt(index)
        if (waitSeconds <= 0.0) return false
        isWaiting = true
        waitRemainingMs = (waitSeconds * 1000).toLong().coerceAtLeast(1)
        waitStartedAtMs = SystemClock.elapsedRealtime()
        KailLog.i(null, TAG, "advance: waiting ${waitRemainingMs}ms at waypoint #$index lat=$currentLat lng=$currentLng")
        return true
    }

    private fun waitSecondsAt(index: Int): Double {
        return routeWaitTimes.getOrNull(index) ?: 0.0
    }

    /**
     * Park the engine at the route's final point. Keeps routePoints intact (so
     * isActive stays true and the service keeps reporting this fixed spot) and
     * pins current* to the destination so playback ends at the last simulated
     * location instead of snapping back to the origin.
     */
    private fun parkAtDestination() {
        routeFinished = true
        segmentProgressMeters = 0.0
        if (routePoints.isNotEmpty()) {
            val last = routePoints.last()
            currentLng = last.first
            currentLat = last.second
            routeIndex = (routePoints.size - 1).coerceAtLeast(0)
        }
        KailLog.i(null, TAG, "advance: route finished (no loop), parked at destination lat=$currentLat lng=$currentLng")
    }

    fun buildStatusString(): Pair<String, LatLng>? {
        if (routePoints.isEmpty()) return null
        val currentDist = if (routeFinished) totalDistance
            else if (routeIndex < routeCumulativeDistances.size)
                routeCumulativeDistances[routeIndex] + segmentProgressMeters
            else totalDistance

        val distStr = if (currentDist > 1000) String.format("%.2fkm", currentDist / 1000) else String.format("%.0fm", currentDist)
        val totalDistStr = if (totalDistance > 1000) String.format("%.2fkm", totalDistance / 1000) else String.format("%.0fm", totalDistance)
        val bd = MapUtils.wgs2bd(currentLng, currentLat)
        return "$distStr / $totalDistStr" to LatLng(bd[1], bd[0])
    }

    private fun calculateRouteDistances() {
        routeCumulativeDistances.clear()
        routeCumulativeDistances.add(0.0)
        var total = 0.0
        for (i in 0 until routePoints.size - 1) {
            val a = routePoints[i]
            val b = routePoints[i + 1]
            total += segmentLengthMeters(a, b)
            routeCumulativeDistances.add(total)
        }
        totalDistance = total
    }

    private fun segmentLengthMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val midLat = (a.second + b.second) / 2.0
        val dLatDeg = b.second - a.second
        val dLngDeg = b.first - a.first
        val metersPerDegLat = GeoMath.metersPerDegLat(midLat)
        val metersPerDegLng = GeoMath.metersPerDegLng(midLat)
        return kotlin.math.sqrt(
            (dLatDeg * metersPerDegLat) * (dLatDeg * metersPerDegLat) +
            (dLngDeg * metersPerDegLng) * (dLngDeg * metersPerDegLng)
        )
    }
}
