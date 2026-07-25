package com.kail.location.views.routesimulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.ColorFilter
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.Overlay
import com.kail.location.utils.KailLog
import android.graphics.Color as AndroidColor
import androidx.preference.PreferenceManager
import com.kail.location.utils.MapUtils
import com.kail.location.R
import com.kail.location.views.common.AppDrawer

import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MarkerOptions
import android.content.Context
import com.kail.location.viewmodels.RouteSimulationViewModel
import com.kail.location.models.HistoryRecord
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

/**
 * 标点阶段枚举
 * Idle 空闲；Preview 预览起点；Active 正在拖拽添加途经点并绘制折线。
 */
private enum class MarkingPhase { Idle, Preview, Active }

/**
 * 路线规划界面
 * 在百度地图上进行途经点标注与路线绘制，支持撤销、定位、地图类型切换与坐标输入。
 *
 * @param mapView 地图视图实例，用于承载地图渲染
 * @param onBackClick 返回点击回调（当前界面内不直接使用）
 * @param onConfirmClick 确认并返回回调，用于保存路线后返回列表
 * @param onLocateClick 定位点击回调，请求设备当前位置
 * @param currentLatLng 当前坐标（BD09），用于进入页面后居中与标记
 * @param onNavigate 导航抽屉跳转回调
 * @param appVersion 应用版本展示文本
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlanScreen(
    mapView: MapView?,
    onBackClick: () -> Unit,
    onConfirmClick: () -> Unit = {},
    onLocateClick: (() -> Unit)? = null,
    currentLatLng: LatLng? = null,
    onNavigate: (Int) -> Unit,
    appVersion: String,
    viewModel: RouteSimulationViewModel,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onDeveloperModeSelected: () -> Unit = {},
    onXposedSettingsSelected: () -> Unit = {},
    editingRouteId: String? = null,
    initialWaypoints: List<LatLng> = emptyList()
) {
    var startPoint by remember { mutableStateOf(initialWaypoints.firstOrNull()?.let { "${it.latitude},${it.longitude}" } ?: "") }
    var endPoint by remember { mutableStateOf(initialWaypoints.lastOrNull()?.let { "${it.latitude},${it.longitude}" } ?: "") }
    var selectingStart by remember { mutableStateOf(initialWaypoints.isEmpty()) }
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isSatellite by remember { mutableStateOf(false) }
    val waypoints = remember { mutableStateListOf<LatLng>().apply { addAll(initialWaypoints) } }
    var polylineOverlay by remember { mutableStateOf<Overlay?>(null) }
    var dashedOverlay by remember { mutableStateOf<Overlay?>(null) }
    var markingPhase by remember { mutableStateOf(if (initialWaypoints.isNotEmpty()) MarkingPhase.Active else MarkingPhase.Idle) }
    var isDragging by remember { mutableStateOf(false) }
    var currentAnchor by remember { mutableStateOf(initialWaypoints.lastOrNull()) }
    var hasCentered by remember { mutableStateOf(false) }
    
    // Marker state
    var currentMarkerOverlay by remember { mutableStateOf<Overlay?>(null) }
    var startMarkerOverlay by remember { mutableStateOf<Overlay?>(null) }
    var endMarkerOverlay by remember { mutableStateOf<Overlay?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showMapTypeDialog by remember { mutableStateOf(false) }
    var showLocationInputDialog by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val searchMarker by viewModel.searchMarker.collectAsState()

    // Search Marker Overlay
    var searchMarkerOverlay by remember { mutableStateOf<Overlay?>(null) }

    // History picker state
    var showHistoryPicker by remember { mutableStateOf(false) }
    val locationHistory by viewModel.locationHistoryRecords.collectAsState()

    val addWaypointFromRecord: (HistoryRecord) -> Unit = { record ->
        val lat = record.latitudeBd09.toDoubleOrNull()
        val lng = record.longitudeBd09.toDoubleOrNull()
        if (lat != null && lng != null) {
            val point = LatLng(lat, lng)
            if (waypoints.isEmpty()) {
                startPoint = "${lat},${lng}"
                selectingStart = false
            }
            waypoints.add(point)
            endPoint = "${lat},${lng}"
            currentAnchor = point
            if (markingPhase == MarkingPhase.Idle) {
                markingPhase = MarkingPhase.Active
            }
            mapView?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLng(point))
        }
    }

    LaunchedEffect(mapView) {
        try {
            val map = mapView?.map
            if (map != null) {
                map.clear()
                map.isMyLocationEnabled = true
                map.setMyLocationConfiguration(
                    com.baidu.mapapi.map.MyLocationConfiguration(
                        com.baidu.mapapi.map.MyLocationConfiguration.LocationMode.NORMAL,
                        true,
                        null
                    )
                )
                KailLog.i(context, "RoutePlanScreen", "Map initialized")
                map.setMapStatus(MapStatusUpdateFactory.zoomTo(15f))
                
                map.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
                    override fun onMapStatusChangeStart(status: MapStatus) {
                        if (markingPhase != MarkingPhase.Active) return
                        isDragging = true
                        val last = waypoints.lastOrNull() ?: currentAnchor ?: status.target
                        val target = status.target
                        dashedOverlay?.remove()
                        val opt = PolylineOptions()
                            .width(8)
                            .color(AndroidColor.BLUE)
                            .points(listOf(last, target))
                        try {
                            val method = PolylineOptions::class.java.getMethod("dottedLine", Boolean::class.javaPrimitiveType)
                            method.invoke(opt, true)
                        } catch (_: Exception) {}
                        dashedOverlay = map.addOverlay(opt)
                    }
                    override fun onMapStatusChangeStart(status: MapStatus, reason: Int) {
                        onMapStatusChangeStart(status)
                    }

                    override fun onMapStatusChange(status: MapStatus) {
                        if (markingPhase != MarkingPhase.Active || !isDragging) return
                        val last = waypoints.lastOrNull() ?: currentAnchor ?: status.target
                        val target = status.target
                        dashedOverlay?.remove()
                        val opt = PolylineOptions()
                            .width(8)
                            .color(AndroidColor.BLUE)
                            .points(listOf(last, target))
                        try {
                            val method = PolylineOptions::class.java.getMethod("dottedLine", Boolean::class.javaPrimitiveType)
                            method.invoke(opt, true)
                        } catch (_: Exception) {}
                        dashedOverlay = map.addOverlay(opt)
                    }

                    override fun onMapStatusChangeFinish(status: MapStatus) {
                        if (markingPhase != MarkingPhase.Active) return
                        isDragging = false
                        dashedOverlay?.remove()
                        dashedOverlay = null
                        val target = status.target
                        val anchor = waypoints.lastOrNull() ?: currentAnchor ?: target
                        
                        if (waypoints.isEmpty() && anchor != target) {
                             waypoints.add(anchor)
                             if (selectingStart) {
                                 startPoint = "${anchor.latitude},${anchor.longitude}"
                                 selectingStart = false
                             }
                        }
                        
                        waypoints.add(target)
                        polylineOverlay?.remove()
                        if (waypoints.size >= 2) {
                            val polyOpt = PolylineOptions().width(8).color(AndroidColor.BLUE).points(waypoints)
                            polylineOverlay = map.addOverlay(polyOpt)
                        }
                        
                        endPoint = "${target.latitude},${target.longitude}"
                        currentAnchor = target
                        KailLog.i(context, "RoutePlanScreen", "Finalize waypoint ${waypoints.size} -> $target")
                    }
                })
            } else {
                KailLog.e(context, "RoutePlanScreen", "MapView.map is null")
            }
        } catch (e: Exception) {
            KailLog.e(context, "RoutePlanScreen", "Map init error: ${e.message}")
        }
    }

    LaunchedEffect(mapView, currentLatLng, markingPhase) {
        try {
            val map = mapView?.map
            if (map != null) {
                val center = if (initialWaypoints.isNotEmpty()) initialWaypoints.last() else currentLatLng
                val ll = currentLatLng
                if (!hasCentered && center != null && !(center.latitude == 0.0 && center.longitude == 0.0)) {
                    map.animateMapStatus(MapStatusUpdateFactory.newLatLng(center))
                    KailLog.i(context, "RoutePlanScreen", "Center to $center")
                    hasCentered = true
                }
                
                // Update Current Location Marker
                if (markingPhase == MarkingPhase.Idle && ll != null) {
                    currentMarkerOverlay?.remove()
                    val option = MarkerOptions()
                        .position(ll)
                        .icon(MapUtils.bitmapDescriptorFromVector(context, R.drawable.ic_position))
                        .zIndex(9)
                        .draggable(false)
                    currentMarkerOverlay = map.addOverlay(option)
                } else {
                    currentMarkerOverlay?.remove()
                    currentMarkerOverlay = null
                }
            }
        } catch (e: Exception) {
            KailLog.e(context, "RoutePlanScreen", "Map center/marker error: ${e.message}")
        }
    }

    LaunchedEffect(mapView, waypoints.size, waypoints.toList()) {
        val map = mapView?.map
        if (map != null) {
            startMarkerOverlay?.remove()
            endMarkerOverlay?.remove()
            startMarkerOverlay = null
            endMarkerOverlay = null
            polylineOverlay?.remove()
            polylineOverlay = null

            if (waypoints.isNotEmpty()) {
                val start = waypoints.first()
                val startDesc = MapUtils.bitmapDescriptorFromVector(context, R.drawable.icon_gcoding, AndroidColor.GREEN)
                if (startDesc != null) {
                    startMarkerOverlay = map.addOverlay(
                        MarkerOptions().position(start).icon(startDesc).zIndex(8).draggable(false)
                    )
                }
                if (waypoints.size > 1) {
                    val end = waypoints.last()
                    val endDesc = MapUtils.bitmapDescriptorFromVector(context, R.drawable.icon_gcoding, AndroidColor.RED)
                    if (endDesc != null) {
                        endMarkerOverlay = map.addOverlay(
                            MarkerOptions().position(end).icon(endDesc).zIndex(8).draggable(false)
                        )
                    }
                }
                if (waypoints.size >= 2) {
                    val polyOpt = PolylineOptions().width(8).color(AndroidColor.BLUE).points(waypoints.toList())
                    polylineOverlay = map.addOverlay(polyOpt)
                }
            }
        }
    }

    LaunchedEffect(searchMarker, mapView) {
        val map = mapView?.map
        if (map != null) {
            searchMarkerOverlay?.remove()
            searchMarkerOverlay = null
            if (searchMarker != null) {
                val option = MarkerOptions()
                    .position(searchMarker)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding)) // Reusing icon
                    .zIndex(10)
                    .draggable(false)
                searchMarkerOverlay = map.addOverlay(option)
                map.animateMapStatus(MapStatusUpdateFactory.newLatLng(searchMarker))
            }
        }
    }

    if (showLocationInputDialog) {
        LocationInputDialog(
            onDismiss = { showLocationInputDialog = false },
            onConfirm = { lat, lng, isBd09 ->
                val target = if (isBd09) {
                    LatLng(lat, lng)
                } else {
                    val wgs84 = MapUtils.wgs2bd(lng, lat)
                    LatLng(wgs84[1], wgs84[0])
                }
                mapView?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLng(target))
                showLocationInputDialog = false
            }
        )
    }

    if (showHistoryPicker) {
        HistoryRecordPickerDialog(
            records = locationHistory,
            onDismiss = { showHistoryPicker = false },
            onSelect = { record ->
                showHistoryPicker = false
                addWaypointFromRecord(record)
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "RouteSimulation",
                onNavigate = onNavigate,
                appVersion = appVersion,
                runMode = runMode,
                onRunModeChange = onRunModeChange,
                onDeveloperModeSelected = onDeveloperModeSelected,
                onXposedSettingsSelected = onXposedSettingsSelected,
                scope = scope
            )
        }
    ) {
        Scaffold(
            topBar = {
                if (isSearchActive) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { 
                            searchQuery = it
                            viewModel.search(it, null)
                        },
                        onSearch = { viewModel.search(it, null) },
                        active = true,
                        onActiveChange = { isSearchActive = it },
                        placeholder = { Text(stringResource(R.string.route_plan_search_hint)) },
                        leadingIcon = {
                            IconButton(onClick = { 
                                isSearchActive = false
                                searchQuery = ""
                                viewModel.clearSearchResults()
                                viewModel.clearSearchMarker()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { 
                                    searchQuery = ""
                                    viewModel.search("", null)
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    ) {
                        if (searchResults.isNotEmpty()) {
                            LazyColumn {
                                items(searchResults.size) { index ->
                                    val item = searchResults[index]
                                    val name = item[RouteSimulationViewModel.POI_NAME].toString()
                                    val address = item[RouteSimulationViewModel.POI_ADDRESS].toString()
                                    ListItem(
                                        headlineContent = { Text(name) },
                                        supportingContent = { Text(address) },
                                        modifier = Modifier.clickable {
                                            val lat = item[RouteSimulationViewModel.POI_LATITUDE] as Double
                                            val lng = item[RouteSimulationViewModel.POI_LONGITUDE] as Double
                                            viewModel.selectSearchResult(lat, lng)
                                            
                                            isSearchActive = false
                                            searchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                if (mapView != null) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                }

                // Center Reference Marker Overlay (fixed to screen center)
                when (markingPhase) {
                    MarkingPhase.Preview -> {
                        Image(
                            painter = painterResource(id = R.drawable.icon_gcoding),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp),
                            colorFilter = ColorFilter.tint(Color(0xFF4CAF50))
                        )
                    }
                    MarkingPhase.Active -> {
                        Image(
                            painter = painterResource(id = R.drawable.icon_gcoding),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                    }
                    else -> { /* No reference marker in Idle */ }
                }

                // Map Controls Overlay (Right Side)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                ) {
                    MapControlButton(
                        iconRes = R.drawable.ic_map,
                        onClick = { showMapTypeDialog = true }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MapControlButton(
                        iconRes = R.drawable.ic_history,
                        onClick = { showHistoryPicker = true }
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    MapControlButton(
                        iconRes = R.drawable.ic_input,
                        onClick = { showLocationInputDialog = true }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MapControlButton(
                        iconRes = R.drawable.ic_home_position,
                        onClick = { onLocateClick?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MapControlButton(
                        iconRes = R.drawable.ic_zoom_in,
                        onClick = { mapView?.map?.setMapStatus(MapStatusUpdateFactory.zoomIn()) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MapControlButton(
                        iconRes = R.drawable.ic_zoom_out,
                        onClick = { mapView?.map?.setMapStatus(MapStatusUpdateFactory.zoomOut()) }
                    )
                }

                // Route Plan Bottom Buttons
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 32.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SmallFloatingActionButton(onClick = {
                        try {
                            if (markingPhase == MarkingPhase.Active) {
                                dashedOverlay?.remove()
                                dashedOverlay = null
                                isDragging = false
                            }
                            if (waypoints.isNotEmpty()) {
                                waypoints.removeAt(waypoints.lastIndex)
                                polylineOverlay?.remove()
                                polylineOverlay = null
                                val map = mapView?.map
                                if (map != null && waypoints.size >= 2) {
                                    val polyOpt = PolylineOptions().width(8).color(AndroidColor.BLUE).points(waypoints)
                                    polylineOverlay = map.addOverlay(polyOpt)
                                }
                                if (waypoints.isNotEmpty()) {
                                    val last = waypoints.last()
                                    endPoint = "${last.latitude},${last.longitude}"
                                    currentAnchor = last
                                } else {
                                    startPoint = ""
                                    endPoint = ""
                                    selectingStart = true
                                    currentAnchor = null
                                }
                            }
                            KailLog.i(context, "RoutePlanScreen", "Undo last waypoint, now size=${waypoints.size}")
                        } catch (e: Exception) {
                            KailLog.e(context, "RoutePlanScreen", "Undo error: ${e.message}")
                        }
                    },
                    modifier = Modifier.alpha(if (waypoints.isNotEmpty()) 1f else 0f),
                    containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary) {
                        Icon(painter = painterResource(id = R.drawable.ic_left), contentDescription = null)
                    }

                    // Switch Mark Mode
                    FloatingActionButton(
                        onClick = {
                            try {
                                val map = mapView?.map
                                val center = map?.mapStatus?.target
                                when (markingPhase) {
                                    MarkingPhase.Idle -> {
                                        currentAnchor = center
                                        markingPhase = MarkingPhase.Preview
                                    }
                                    MarkingPhase.Preview -> {
                                        currentAnchor = center
                                        if (center != null && waypoints.isEmpty()) {
                                            waypoints.add(center)
                                            startPoint = "${center.latitude},${center.longitude}"
                                            selectingStart = false
                                        }
                                        markingPhase = MarkingPhase.Active
                                    }
                                    MarkingPhase.Active -> {
                                        dashedOverlay?.remove()
                                        dashedOverlay = null
                                        isDragging = false
                                        currentAnchor = null
                                        markingPhase = MarkingPhase.Idle
                                    }
                                }
                                KailLog.i(context, "RoutePlanScreen", "Marking phase ${markingPhase}")
                            } catch (e: Exception) {
                                KailLog.e(context, "RoutePlanScreen", "Toggle mark mode error: ${e.message}")
                            }
                        },
                        containerColor = if (markingPhase != MarkingPhase.Idle) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_position),
                            contentDescription = "Mark Mode",
                            tint = if (markingPhase != MarkingPhase.Idle) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Confirm and Save
                    FloatingActionButton(
                        onClick = {
                            try {
                                if (waypoints.size >= 2) {
                                    if (editingRouteId != null) {
                                        viewModel.updateRoute(editingRouteId, waypoints.toList())
                                        KailLog.i(context, "RoutePlanScreen", "Updated route ${editingRouteId} with ${waypoints.size} points")
                                    } else {
                                        viewModel.setPendingRoutePoints(waypoints.toList())
                                        KailLog.i(context, "RoutePlanScreen", "Set pending route with ${waypoints.size} points")
                                    }
                                }
                                onConfirmClick()
                            } catch (e: Exception) {
                                KailLog.e(context, "RoutePlanScreen", "Save route error: ${e.message}")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }

    if (showMapTypeDialog) {
        AlertDialog(
            onDismissRequest = { showMapTypeDialog = false },
            title = { Text(stringResource(R.string.route_plan_map_type)) },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { 
                            mapView?.map?.mapType = BaiduMap.MAP_TYPE_NORMAL
                            showMapTypeDialog = false 
                        }
                    ) {
                        RadioButton(selected = mapView?.map?.mapType == BaiduMap.MAP_TYPE_NORMAL, onClick = { 
                            mapView?.map?.mapType = BaiduMap.MAP_TYPE_NORMAL
                            showMapTypeDialog = false 
                        })
                        Text(stringResource(R.string.route_plan_normal))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { 
                            mapView?.map?.mapType = BaiduMap.MAP_TYPE_SATELLITE
                            showMapTypeDialog = false 
                        }
                    ) {
                        RadioButton(selected = mapView?.map?.mapType == BaiduMap.MAP_TYPE_SATELLITE, onClick = { 
                            mapView?.map?.mapType = BaiduMap.MAP_TYPE_SATELLITE
                            showMapTypeDialog = false 
                        })
                        Text(stringResource(R.string.route_plan_satellite))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMapTypeDialog = false }) {
                    Text(stringResource(R.string.route_plan_cancel))
                }
            }
        )
    }
}


/**
 * 地图控制按钮
 * 圆形按钮样式，承载地图相关操作（如切换类型、缩放等）。
 *
 * @param iconRes 图标资源 ID
 * @param onClick 点击回调
 */
@Composable
fun MapControlButton(iconRes: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 保存规划路线点数据到偏好存储
 * 将点序列按逗号拼接为字符串并写入 SharedPreferences。
 *
 * @param prefs 偏好存储实例
 * @param points 路线点坐标序列（WGS84，经纬度交替）
 */
fun saveRoute(prefs: android.content.SharedPreferences, points: List<Double>) {
    val sb = StringBuilder()
    for (i in points.indices) {
        sb.append(points[i])
        if (i < points.size - 1) sb.append(",")
    }
    prefs.edit().putString("route_data", sb.toString()).apply()
}

/**
 * 坐标输入对话框
 * 支持输入纬度与经度，并选择是否为 BD09 坐标；确认后回传坐标值。
 *
 * @param onDismiss 关闭对话框回调
 * @param onConfirm 确认并回传坐标的回调 参数为纬度、经度与是否 BD09
 */
@Composable
fun LocationInputDialog(onDismiss: () -> Unit, onConfirm: (Double, Double, Boolean) -> Unit) {
    var combinedStr by remember { mutableStateOf("") }
    var latStr by remember { mutableStateOf("") }
    var lngStr by remember { mutableStateOf("") }
    var isBd09 by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coordErrorStr = stringResource(R.string.route_plan_coord_error)

    fun parseCombined(input: String) {
        if (input.contains(",") || input.contains("，")) {
            val parts = input.split(",|，".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null) {
                    latStr = parts[0]
                    lngStr = parts[1]
                    errorMsg = null
                    return
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_plan_coord_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = combinedStr,
                    onValueChange = {
                        combinedStr = it
                        parseCombined(it)
                    },
                    label = { Text(stringResource(R.string.route_plan_coord_combined)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = latStr,
                    onValueChange = {
                        latStr = it
                        combinedStr = ""
                    },
                    label = { Text(stringResource(R.string.route_plan_latitude)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = lngStr,
                    onValueChange = {
                        lngStr = it
                        combinedStr = ""
                    },
                    label = { Text(stringResource(R.string.route_plan_longitude)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBd09, onCheckedChange = { isBd09 = it })
                    Text(stringResource(R.string.route_plan_coord_type))
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val lat = latStr.toDoubleOrNull()
                val lng = lngStr.toDoubleOrNull()
                if (lat != null && lng != null) {
                    onConfirm(lat, lng, isBd09)
                } else {
                    errorMsg = coordErrorStr
                }
            }) {
                Text(stringResource(R.string.route_plan_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.route_plan_cancel))
            }
        }
    )
}

@Composable
fun HistoryRecordPickerDialog(
    records: List<HistoryRecord>,
    onDismiss: () -> Unit,
    onSelect: (HistoryRecord) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val favRecords = records.filter { it.isFavorite }
        .sortedWith(compareBy<HistoryRecord> { it.favoriteOrder }.thenByDescending { it.favoriteTime })
    val allRecords = records.sortedByDescending { it.timestamp }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_plan_history_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (records.isEmpty()) {
                    Text(stringResource(R.string.history_idle), color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.joystick_history_favorites), fontSize = 14.sp) })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.joystick_history_normal), fontSize = 14.sp) })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayList = if (selectedTab == 0) favRecords else allRecords

                    if (displayList.isEmpty()) {
                        Text(stringResource(R.string.history_idle), color = Color.Gray, modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            items(displayList, key = { "picker_${it.id}" }) { record ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onSelect(record) },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (record.isFavorite) Color(0xFFFFB300) else Color.Gray,
                                            modifier = Modifier.size(16.dp).graphicsLayer(alpha = if (record.isFavorite) 1f else 0.3f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = record.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(text = record.displayTime, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.route_plan_cancel))
            }
        }
    )
}
