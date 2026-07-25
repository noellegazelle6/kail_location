package com.kail.location.views.joystick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng
import com.kail.location.viewmodels.JoystickViewModel

/**
 * Root Composable for all joystick-related floating windows.
 * Switches between different overlays based on the ViewModel state.
 */
@Composable
fun JoystickRoot(
    viewModel: JoystickViewModel,
    mapView: MapView?,
    actionListener: JoystickViewModel.ActionListener,
    onMoveInfo: (Boolean, Double, Double) -> Unit,
    onWindowDrag: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onFocusModeChanged: (Boolean) -> Unit = {}
) {
    val windowType by viewModel.windowType.collectAsState()

    LaunchedEffect(windowType) {
        val needsFocus = windowType == JoystickViewModel.WindowType.HISTORY || windowType == JoystickViewModel.WindowType.MAP
        onFocusModeChanged(needsFocus)
    }
    val isPaused by viewModel.isRoutePaused.collectAsState()
    val routeSpeed by viewModel.routeSpeed.collectAsState()
    val routeProgress by viewModel.routeProgress.collectAsState()
    val routeTotalDistance by viewModel.routeTotalDistance.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    when (windowType) {
        JoystickViewModel.WindowType.JOYSTICK -> {
            JoyStickOverlay(
                viewModel = viewModel,
                onMoveInfo = onMoveInfo,
                onWindowDrag = onWindowDrag
            )
        }
        JoystickViewModel.WindowType.MAP -> {
            if (mapView != null) {
                val currentLoc by viewModel.currentLocation.collectAsState()
                JoyStickMapOverlay(
                    mapView = mapView,
                    currentLocation = currentLoc,
                    onClose = { viewModel.setWindowType(JoystickViewModel.WindowType.JOYSTICK) },
                    onWindowDrag = onWindowDrag,
                    onGo = { viewModel.confirmTeleport(actionListener) },
                    onBackToCurrent = {
                        mapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLng(currentLoc))
                    },
                    onSearch = { query -> viewModel.search(query, null) },
                    searchResults = searchResults,
                    onSelectSearchResult = { item ->
                        val lat = item[com.kail.location.viewmodels.LocationPickerViewModel.POI_LATITUDE].toString().toDouble()
                        val lng = item[com.kail.location.viewmodels.LocationPickerViewModel.POI_LONGITUDE].toString().toDouble()
                        viewModel.updateMarkLocation(LatLng(lat, lng))
                        mapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLng(LatLng(lat, lng)))
                    }
                )
            }
        }
        JoystickViewModel.WindowType.HISTORY -> {
            val isPinned by viewModel.isHistoryPinned.collectAsState()
            JoyStickHistoryOverlay(
                historyRecords = historyRecords,
                onClose = { viewModel.setWindowType(JoystickViewModel.WindowType.JOYSTICK) },
                onWindowDrag = onWindowDrag,
                onSelectRecord = { record -> viewModel.selectHistoryRecord(record, actionListener) },
                onSearch = { },
                onToggleFavorite = { id -> viewModel.toggleHistoryFavorite(id) },
                onRename = { id, name -> viewModel.renameHistoryRecord(id, name) },
                onDelete = { id -> viewModel.deleteHistoryRecord(id) },
                isPinned = isPinned,
                onTogglePin = { viewModel.toggleHistoryPin() },
                onMoveFavUp = { id -> viewModel.moveFavorite(id, up = true) },
                onMoveFavDown = { id -> viewModel.moveFavorite(id, up = false) },
                onSetFavoriteOrder = { ids -> viewModel.setFavoriteOrder(ids) }
            )
        }
        JoystickViewModel.WindowType.ROUTE_CONTROL -> {
            FloatingNavigationControlOverlay(
                mapView = mapView,
                isPaused = isPaused,
                speed = routeSpeed,
                progress = routeProgress,
                totalDistance = routeTotalDistance,
                onPauseResume = { 
                    val newState = !isPaused
                    viewModel.setRoutePauseState(newState)
                    actionListener.onRouteControl(if (newState) "pause" else "resume")
                },
                onStop = { actionListener.onRouteControl("stop") },
                onRestart = { actionListener.onRouteControl("restart") },
                onSeek = { progress -> actionListener.onRouteSeek(progress) },
                onSpeedChange = { speed ->
                    viewModel.setRouteSpeed(speed)
                    actionListener.onRouteSpeedChange(speed)
                },
                onWindowDrag = onWindowDrag,
                onClose = onClose
            )
        }
    }
}
