package com.kail.location.views.navigationsimulation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Overlay
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.kail.location.R
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils
import com.kail.location.viewmodels.NavigationSimulationViewModel
import com.kail.location.views.routesimulation.WaypointWaitDialog
import com.kail.location.views.routesimulation.buildWaitBadgeBitmap
import android.graphics.Color as AndroidColor

/**
 * 导航模拟的规划页：
 * - 中心准星常驻，点一次落起点、再点一次落终点；
 * - 已规划（蓝色路线线出现）后，再点则把等待点吸附到蓝色线上最近的点，
 *   并通过"等待"按钮为该点设置停留秒数（模拟到该处会等待）。
 *
 * @param mapView 地图视图
 * @param onBackClick 返回回调
 * @param onConfirmClick 确认回调，参数为起点、终点（BD09）与等待点（路线下标 → 秒）
 * @param initialStart 已存在的起点（BD09），为空则无预填
 * @param initialEnd 已存在的终点（BD09），为空则无预填
 * @param plannedRoutePoints 已规划好的路线途经点（BD09），用于画蓝色规划线
 * @param routeWaits 已存在的等待点（路线下标 → 秒），用于预填
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationPlanScreen(
    mapView: MapView?,
    onBackClick: () -> Unit,
    onConfirmClick: (LatLng, LatLng, Map<Int, Int>) -> Unit,
    viewModel: NavigationSimulationViewModel,
    onLocateClick: (() -> Unit)? = null,
    initialStart: LatLng? = null,
    initialEnd: LatLng? = null,
    plannedRoutePoints: List<LatLng>? = null,
    routeWaits: Map<Int, Int> = emptyMap()
) {
    val context = LocalContext.current
    val route = plannedRoutePoints
    val initialPoints = remember { listOfNotNull(initialStart, initialEnd) }
    val waypoints = remember { mutableStateListOf<LatLng>().apply { addAll(initialPoints) } }
    val waitPoints = remember { mutableStateListOf<LatLng>() }
    val waitSecs = remember { mutableStateListOf<Int>() }
    var startMarkerOverlay by remember { mutableStateOf<Overlay?>(null) }
    var endMarkerOverlay by remember { mutableStateOf<Overlay?>(null) }
    var plannedLineOverlay by remember { mutableStateOf<Overlay?>(null) }
    val waitMarkersOverlays = remember { mutableListOf<Overlay>() }

    var showWaitDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()

    fun nearestIndex(target: LatLng): Int {
        if (route == null || route.isEmpty()) return -1
        var best = 0
        var bestDist = Double.MAX_VALUE
        route.forEachIndexed { i, p ->
            val dlat = p.latitude - target.latitude
            val dlng = p.longitude - target.longitude
            val d = dlat * dlat + dlng * dlng
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    fun redraw() {
        val map = mapView?.map ?: return
        startMarkerOverlay?.remove()
        startMarkerOverlay = null
        endMarkerOverlay?.remove()
        endMarkerOverlay = null
        plannedLineOverlay?.remove()
        plannedLineOverlay = null
        waitMarkersOverlays.forEach { runCatching { it.remove() } }
        waitMarkersOverlays.clear()

        // 蓝色规划线（百度 API 规划的路线）
        if (route != null && route.size >= 2) {
            plannedLineOverlay = map.addOverlay(
                PolylineOptions().width(8).color(AndroidColor.BLUE).points(route)
            )
        }
        if (waypoints.isNotEmpty()) {
            val sd = MapUtils.bitmapDescriptorFromVector(context, R.drawable.icon_gcoding, AndroidColor.GREEN)
            if (sd != null) {
                startMarkerOverlay = map.addOverlay(MarkerOptions().position(waypoints[0]).icon(sd).zIndex(8).draggable(false))
            }
        }
        if (waypoints.size >= 2) {
            val ed = MapUtils.bitmapDescriptorFromVector(context, R.drawable.icon_gcoding, AndroidColor.RED)
            if (ed != null) {
                endMarkerOverlay = map.addOverlay(MarkerOptions().position(waypoints[1]).icon(ed).zIndex(8).draggable(false))
            }
        }
        // 等待点：蓝色秒数气泡（吸附在蓝色线上），标出等待秒数
        waitPoints.forEachIndexed { i, p ->
            val badge = buildWaitBadgeBitmap(context, waitSecs[i])
            waitMarkersOverlays.add(
                map.addOverlay(
                    MarkerOptions()
                        .position(p)
                        .icon(BitmapDescriptorFactory.fromBitmap(badge))
                        .anchor(0.5f, 1f)
                        .zIndex(9)
                        .draggable(false)
                )
            )
        }
    }

    // 预填已存在的等待点
    LaunchedEffect(Unit) {
        routeWaits.forEach { (idx, sec) ->
            route?.getOrNull(idx)?.let {
                waitPoints.add(it)
                waitSecs.add(sec)
            }
        }
        redraw()
    }

    LaunchedEffect(mapView) {
        try {
            val map = mapView?.map ?: return@LaunchedEffect
            map.clear()
            map.isMyLocationEnabled = true
            map.setMyLocationConfiguration(
                com.baidu.mapapi.map.MyLocationConfiguration(
                    com.baidu.mapapi.map.MyLocationConfiguration.LocationMode.NORMAL,
                    true,
                    null
                )
            )
            map.setMapStatus(MapStatusUpdateFactory.zoomTo(15f))
        } catch (e: Exception) {
            KailLog.e(context, "NavigationPlanScreen", "map init error: ${e.message}")
        }
    }

    LaunchedEffect(mapView, waypoints.toList(), waitPoints.toList(), waitSecs.toList()) {
        redraw()
    }

    if (showWaitDialog && waitPoints.isNotEmpty()) {
        WaypointWaitDialog(
            waypointIndex = waitPoints.size,
            currentWaitSeconds = waitSecs.last(),
            onDismiss = { showWaitDialog = false },
            onConfirm = { seconds ->
                waitSecs[waitSecs.lastIndex] = seconds
                showWaitDialog = false
                redraw()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_sim_plan_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            viewModel.clearSearchResults()
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (mapView != null) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            }

            // Center crosshair: always visible, marks where the next point will drop
            Image(
                painter = painterResource(id = R.drawable.icon_gcoding),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                colorFilter = ColorFilter.tint(
                    if (waypoints.isEmpty()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
            )

            // Right control column
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                NavPlanMapButton(R.drawable.ic_home_position) { onLocateClick?.invoke() }
                Spacer(modifier = Modifier.height(16.dp))
                NavPlanMapButton(R.drawable.ic_zoom_in) { mapView?.map?.setMapStatus(MapStatusUpdateFactory.zoomIn()) }
                Spacer(modifier = Modifier.height(16.dp))
                NavPlanMapButton(R.drawable.ic_zoom_out) { mapView?.map?.setMapStatus(MapStatusUpdateFactory.zoomOut()) }
            }

            // Bottom-right FABs
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 32.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Undo: 先撤等待点，再撤起点/终点
                SmallFloatingActionButton(
                    onClick = {
                        when {
                            waitPoints.isNotEmpty() -> {
                                waitPoints.removeAt(waitPoints.lastIndex)
                                waitSecs.removeAt(waitSecs.lastIndex)
                                redraw()
                            }
                            waypoints.isNotEmpty() -> {
                                waypoints.removeAt(waypoints.lastIndex)
                                redraw()
                            }
                        }
                    },
                    modifier = Modifier.alpha(if (waypoints.isNotEmpty() || waitPoints.isNotEmpty()) 1f else 0f),
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_left), contentDescription = null)
                }

                // Wait time for the latest wait point
                FloatingActionButton(
                    onClick = { if (waitPoints.isNotEmpty()) showWaitDialog = true },
                    containerColor = if (showWaitDialog) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    modifier = Modifier.alpha(if (waitPoints.isNotEmpty()) 1f else 0f)
                ) {
                    Text(
                        text = stringResource(R.string.route_plan_wait_btn),
                        color = if (showWaitDialog) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Drop point: 1st tap = start, 2nd = end, then wait points snapped to the line
                FloatingActionButton(
                    onClick = {
                        val center = mapView?.map?.mapStatus?.target ?: return@FloatingActionButton
                        when {
                            waypoints.isEmpty() -> {
                                waypoints.add(center)
                                redraw()
                                KailLog.i(context, "NavigationPlanScreen", "start -> $center")
                            }
                            waypoints.size == 1 -> {
                                waypoints.add(center)
                                redraw()
                                KailLog.i(context, "NavigationPlanScreen", "end -> $center")
                            }
                            route != null && route.size >= 2 -> {
                                val idx = nearestIndex(center)
                                if (idx >= 0) {
                                    waitPoints.add(route[idx])
                                    waitSecs.add(0)
                                    redraw()
                                    KailLog.i(context, "NavigationPlanScreen", "wait point snapped to route[$idx]")
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_position),
                        contentDescription = "Drop Point",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Confirm
                FloatingActionButton(
                    onClick = {
                        if (waypoints.size >= 2) {
                            val waitsMap = mutableMapOf<Int, Int>()
                            waitPoints.forEachIndexed { i, p ->
                                val idx = nearestIndex(p)
                                if (idx >= 0) waitsMap[idx] = waitSecs[i]
                            }
                            onConfirmClick(waypoints[0], waypoints[1], waitsMap)
                        }
                    },
                    containerColor = if (waypoints.size >= 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.alpha(if (waypoints.size >= 2) 1f else 0.35f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (waypoints.size >= 2) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Search panel
            if (isSearchActive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.search(it)
                            },
                            placeholder = { Text(stringResource(R.string.route_plan_search_hint)) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        viewModel.clearSearchResults()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (searchResults.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                                items(searchResults.size) { index ->
                                    val item = searchResults[index]
                                    val name = item[NavigationSimulationViewModel.POI_NAME].toString()
                                    val address = item[NavigationSimulationViewModel.POI_ADDRESS].toString()
                                    ListItem(
                                        headlineContent = { Text(name) },
                                        supportingContent = { Text(address) },
                                        modifier = Modifier.clickable {
                                            val lat = item[NavigationSimulationViewModel.POI_LATITUDE] as Double
                                            val lng = item[NavigationSimulationViewModel.POI_LONGITUDE] as Double
                                            if (waypoints.size < 2) {
                                                waypoints.add(LatLng(lat, lng))
                                                redraw()
                                                mapView?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLng(LatLng(lat, lng)))
                                            }
                                            isSearchActive = false
                                            searchQuery = ""
                                            viewModel.clearSearchResults()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 地图控制按钮（圆形）。
 */
@Composable
private fun NavPlanMapButton(iconRes: Int, onClick: () -> Unit) {
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
