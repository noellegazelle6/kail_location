package com.kail.location.utils.service

/**
 * ServiceGoRoot 与 ServiceGoDeveloper 共享的 Intent extra 与 action 常量。
 */
object ServiceConstants {
    const val DEFAULT_LAT = 36.667662
    const val DEFAULT_LNG = 117.027707
    const val DEFAULT_ALT = 55.0
    const val DEFAULT_BEA = 0.0f

    const val EXTRA_ROUTE_POINTS = "EXTRA_ROUTE_POINTS"
    const val EXTRA_ROUTE_LOOP = "EXTRA_ROUTE_LOOP"
    const val EXTRA_JOYSTICK_ENABLED = "EXTRA_JOYSTICK_ENABLED"
    const val EXTRA_ROUTE_SPEED = "EXTRA_ROUTE_SPEED"
    const val EXTRA_COORD_TYPE = "EXTRA_COORD_TYPE"
    const val EXTRA_CONTROL_ACTION = "EXTRA_CONTROL_ACTION"
    const val EXTRA_SPEED_FLUCTUATION = "EXTRA_SPEED_FLUCTUATION"
    const val EXTRA_NATURAL_JITTER = "EXTRA_NATURAL_JITTER"
    const val EXTRA_SEEK_RATIO = "EXTRA_SEEK_RATIO"

    const val CONTROL_PAUSE = "pause"
    const val CONTROL_RESUME = "resume"
    const val CONTROL_STOP = "stop"
    const val CONTROL_SEEK = "seek"
    const val CONTROL_SET_SPEED = "set_speed"
    const val CONTROL_SET_SPEED_FLUCTUATION = "set_speed_fluctuation"

    const val COORD_WGS84 = "WGS84"
    const val COORD_BD09 = "BD09"
    const val COORD_GCJ02 = "GCJ02"

    const val ACTION_STATUS_CHANGED = "com.kail.location.service.STATUS_CHANGED"
    const val EXTRA_IS_SIMULATING = "is_simulating"
    const val EXTRA_IS_PAUSED = "is_paused"
}
