package com.kail.location.views.routesimulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.Overlay
import com.elvishew.xlog.XLog
import android.graphics.Color as AndroidColor
import androidx.preference.PreferenceManager
import com.kail.location.utils.MapUtils
import com.kail.location.R
import com.kail.location.views.common.DrawerHeader
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MarkerOptions
import android.content.Context

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
    appVersion: String
) {
    var startPoint by remember { mutableStateOf("") }
    var endPoint by remember { mutableStateOf("") }
    var selectingStart by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isSatellite by remember { mutableStateOf(false) }
    val waypoints = remember { mutableStateListOf<LatLng>() }
    var polylineOverlay by remember { mutableStateOf<Overlay?>(null) }
    var dashedOverlay by remember { mutableStateOf<Overlay?>(null) }
    var markingPhase by remember { mutableStateOf(MarkingPhase.Idle) }
    var isDragging by remember { mutableStateOf(false) }
    var currentAnchor by remember { mutableStateOf<LatLng?>(null) }
    var hasCentered by remember { mutableStateOf(false) }
    
    // Marker state
    val waypointMarkers = remember { mutableStateListOf<Overlay>() }
    var currentMarkerOverlay by remember { mutableStateOf<Overlay?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showMapTypeDialog by remember { mutableStateOf(false) }
    var showLocationInputDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mapView) {
        try {
            val map = mapView?.map
            if (map != null) {
                map.isMyLocationEnabled = true
                XLog.i("RoutePlanScreen: Map initialized")
                
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
                        XLog.i("RoutePlanScreen: Finalize waypoint ${waypoints.size} -> $target")
                    }
                })
            } else {
                XLog.e("RoutePlanScreen: MapView.map is null")
            }
        } catch (e: Exception) {
            XLog.e("RoutePlanScreen: Map init error", e)
        }
    }

    LaunchedEffect(mapView, currentLatLng) {
        try {
            val map = mapView?.map
            if (map != null) {
                val ll = currentLatLng
                if (!hasCentered && ll != null && !(ll.latitude == 0.0 && ll.longitude == 0.0)) {
                    map.animateMapStatus(MapStatusUpdateFactory.newLatLng(ll))
                    XLog.i("RoutePlanScreen: Center to current $ll")
                    hasCentered = true
                }
                
                // Update Current Location Marker
                if (ll != null) {
                    currentMarkerOverlay?.remove()
                    val option = MarkerOptions()
                        .position(ll)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding))
                        .zIndex(9)
                        .draggable(false)
                    currentMarkerOverlay = map.addOverlay(option)
                }
            }
        } catch (e: Exception) {
            XLog.e("RoutePlanScreen: Map center/marker error", e)
        }
    }

    // Update Waypoint Markers
    LaunchedEffect(mapView, waypoints.size, waypoints.toList()) {
        val map = mapView?.map
        if (map != null) {
            // Clear old markers
            waypointMarkers.forEach { it.remove() }
            waypointMarkers.clear()
            
            // Add new markers
            waypoints.forEachIndexed { index, latLng ->
                // Use white for intermediate, red for start/end? Or just white/red dots as requested.
                // Assuming Start/End = Red, Intermediate = White
                val isStartOrEnd = index == 0 || index == waypoints.lastIndex
                val color = if (isStartOrEnd) AndroidColor.RED else AndroidColor.WHITE
                
                val descriptor = bitmapDescriptorFromVector(context, R.drawable.ic_position, color)
                if (descriptor != null) {
                    val option = MarkerOptions()
                        .position(latLng)
                        .icon(descriptor)
                        .zIndex(8)
                        .draggable(false)
                    waypointMarkers.add(map.addOverlay(option))
                }
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
                    val wgs84 = MapUtils.wgs2bd09(lng, lat)
                    LatLng(wgs84[1], wgs84[0])
                }
                mapView?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLng(target))
                showLocationInputDialog = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(appVersion)
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_location_simulation)) },
                    icon = { Icon(painterResource(R.drawable.ic_position), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_location_simulation) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_route_simulation)) },
                    icon = { Icon(painterResource(R.drawable.ic_move), contentDescription = null) },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } }
                )
                
                Text(
                    text = stringResource(R.string.nav_menu_settings),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_settings)) },
                    icon = { Icon(painterResource(R.drawable.ic_menu_settings), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_settings) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_dev)) },
                    icon = { Icon(painterResource(R.drawable.ic_menu_dev), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_dev) } }
                )

                Text(
                    text = stringResource(R.string.nav_menu_more),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_upgrade)) },
                    icon = { Icon(painterResource(R.drawable.ic_menu_upgrade), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_update) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_feedback)) },
                    icon = { Icon(painterResource(R.drawable.ic_menu_feedback), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_feedback) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_contact)) },
                    icon = { Icon(painterResource(R.drawable.ic_contact), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_contact) } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
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
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                if (mapView != null) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
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
                            XLog.i("RoutePlanScreen: Undo last waypoint, now size=${waypoints.size}")
                        } catch (e: Exception) {
                            XLog.e("RoutePlanScreen: Undo error", e)
                        }
                    },
                    modifier = Modifier.alpha(if (waypoints.isNotEmpty()) 1f else 0f),
                    containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary) {
                        Icon(painter = painterResource(id = R.drawable.ic_left), contentDescription = null)
                    }
                    SmallFloatingActionButton(
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
                                XLog.i("RoutePlanScreen: Marking phase ${markingPhase}")
                            } catch (e: Exception) {
                                XLog.e("RoutePlanScreen: Toggle mark mode error", e)
                            }
                        },
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(painter = painterResource(id = R.drawable.icon_gcoding), contentDescription = null)
                    }
                    SmallFloatingActionButton(onClick = {
                        try {
                            onLocateClick?.invoke()
                            XLog.i("RoutePlanScreen: Locate clicked")
                        } catch (e: Exception) {
                            XLog.e("RoutePlanScreen: Locate error", e)
                        }
                    }, containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary) {
                        Icon(painter = painterResource(id = R.drawable.ic_position), contentDescription = null)
                    }
                    FloatingActionButton(
                        onClick = {
                            try {
                                if (waypoints.size >= 2) {
                                    val wgsPoints = ArrayList<Double>(waypoints.size * 2)
                                    waypoints.forEach { p ->
                                        val wgs = MapUtils.bd2wgs(p.longitude, p.latitude)
                                        wgsPoints.add(wgs[0])
                                        wgsPoints.add(wgs[1])
                                    }
                                    saveRoute(prefs, wgsPoints)
                                    XLog.i("RoutePlanScreen: Saved route with ${waypoints.size} points")
                                }
                                onConfirmClick()
                            } catch (e: Exception) {
                                XLog.e("RoutePlanScreen: Save route error", e)
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
            title = { Text("选择地图类型") },
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
                        Text("普通地图")
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
                        Text("卫星地图")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMapTypeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 将矢量资源转换为位图描述符
 * 可选地对矢量资源进行着色，返回用于地图标记的 BitmapDescriptor。
 *
 * @param context 上下文
 * @param vectorResId 矢量资源 ID
 * @param tint 可选的着色值（Android 颜色整数），为空则保持原色
 * @return 位图描述符，失败时返回 null
 */
fun bitmapDescriptorFromVector(context: Context, vectorResId: Int, tint: Int? = null): BitmapDescriptor? {
    val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
    vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
    if (tint != null) {
        DrawableCompat.setTint(vectorDrawable, tint)
    }
    val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    vectorDrawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
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
    var latStr by remember { mutableStateOf("") }
    var lngStr by remember { mutableStateOf("") }
    var isBd09 by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入坐标") },
        text = {
            Column {
                OutlinedTextField(
                    value = latStr,
                    onValueChange = { latStr = it },
                    label = { Text("纬度") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lngStr,
                    onValueChange = { lngStr = it },
                    label = { Text("经度") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBd09, onCheckedChange = { isBd09 = it })
                    Text("BD09坐标 (默认WGS84)")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val lat = latStr.toDoubleOrNull()
                val lng = lngStr.toDoubleOrNull()
                if (lat != null && lng != null) {
                    onConfirm(lat, lng, isBd09)
                }
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
