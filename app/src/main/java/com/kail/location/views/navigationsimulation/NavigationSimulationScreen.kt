package com.kail.location.views.navigationsimulation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kail.location.R
import com.kail.location.viewmodels.NavigationSimulationViewModel
import androidx.compose.material.icons.filled.Place
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.kail.location.views.locationpicker.LocationPickerActivity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.kail.location.views.common.AppDrawer
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MarkerOptions
import com.kail.location.models.RouteInfo


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSimulationScreen(
    viewModel: NavigationSimulationViewModel = viewModel(),
    onNavigate: (Int) -> Unit,
    appVersion: String,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onDeveloperModeSelected: () -> Unit = {},
    onXposedSettingsSelected: () -> Unit = {}
) {
    val startPoint by viewModel.startPoint.collectAsState()
    val endPoint by viewModel.endPoint.collectAsState()
    val isMultiRoute by viewModel.isMultiRoute.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val isSimulating by viewModel.isSimulating.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val candidateRoutes by viewModel.candidateRoutes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentLatLng by viewModel.currentLatLng.collectAsState()
    
    // Search State
    var isSearchingStart by remember { mutableStateOf(false) }
    var isSearchingEnd by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    
    // Speed Settings State
    var showSpeedDialog by remember { mutableStateOf(false) }
    var speedStr by remember { mutableStateOf("60") }

    // Rename State
    var renameTarget by remember { mutableStateOf<RouteInfo?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Map Selection State
    var pickingType by remember { mutableStateOf("none") } // "start" or "end"

    val context = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val lat = data?.getDoubleExtra(LocationPickerActivity.RESULT_LAT, 0.0) ?: 0.0
            val lng = data?.getDoubleExtra(LocationPickerActivity.RESULT_LNG, 0.0) ?: 0.0
            val name = data?.getStringExtra(LocationPickerActivity.RESULT_NAME) ?: "Unknown"
            
            if (pickingType == "start") {
                viewModel.selectStartPoint(name, lat, lng)
            } else if (pickingType == "end") {
                viewModel.selectEndPoint(name, lat, lng)
            }
        }
        pickingType = "none"
    }

    fun pickStart() {
        pickingType = "start"
        launcher.launch(Intent(context, LocationPickerActivity::class.java).apply {
            putExtra(LocationPickerActivity.EXTRA_PICK_MODE, true)
        })
    }
    fun pickEnd() {
        pickingType = "end"
        launcher.launch(Intent(context, LocationPickerActivity::class.java).apply {
            putExtra(LocationPickerActivity.EXTRA_PICK_MODE, true)
        })
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(R.string.nav_sim_speed_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = speedStr,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) speedStr = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    label = { Text(stringResource(R.string.nav_sim_speed)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    val speed = speedStr.toDoubleOrNull() ?: 60.0
                    viewModel.setSpeed(speed)
                    showSpeedDialog = false 
                }) {
                    Text(stringResource(R.string.nav_sim_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.nav_sim_cancel))
                }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.route_sim_rename_title)) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it })
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameHistory(renameTarget!!.id.toLongOrNull() ?: return@TextButton, renameText)
                    renameTarget = null
                }) { Text(stringResource(R.string.route_sim_rename_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.route_sim_rename_cancel)) }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "NavigationSimulation",
                onNavigate = onNavigate,
                appVersion = appVersion,
                runMode = runMode,
                onRunModeChange = onRunModeChange,
                onDeveloperModeSelected = onDeveloperModeSelected,
                onXposedSettingsSelected = onXposedSettingsSelected
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_sim_title), color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.animateTo(DrawerValue.Open, androidx.compose.animation.core.tween(durationMillis = 160)) } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                if (!isSearchingStart && !isSearchingEnd) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Start Point
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.nav_sim_start_point),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(60.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { pickStart() }
                                ) {
                                    Text(
                                        text = if (startPoint.isEmpty()) stringResource(R.string.nav_sim_select_start) else startPoint,
                                        color = if (startPoint.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { pickStart() }) {
                                    Icon(Icons.Default.Place, contentDescription = "Select on Map", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            HorizontalDivider()

                            // End Point
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.nav_sim_end_point),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(60.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { pickEnd() }
                                ) {
                                    Text(
                                        text = if (endPoint.isEmpty()) stringResource(R.string.nav_sim_select_end) else endPoint,
                                        color = if (endPoint.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { pickEnd() }) {
                                    Icon(Icons.Default.Place, contentDescription = "Select on Map", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            if (!isSimulating) {
                                Button(
                                    onClick = { viewModel.startSimulation() },
                                    enabled = !isLoading && startPoint.isNotEmpty() && endPoint.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.primary,
                                        disabledContentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.nav_sim_planning), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Text(stringResource(R.string.nav_sim_start), color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            } else {
                                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { if (isPaused) viewModel.resumeSimulation() else viewModel.pauseSimulation() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (isPaused) stringResource(R.string.nav_sim_resume) else stringResource(R.string.nav_sim_pause)) }
                                    Button(
                                        onClick = { viewModel.stopSimulation() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Red,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.weight(1f)
                                    ) { Text(stringResource(R.string.nav_sim_stop)) }
                                }
                            }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isMultiRoute,
                                        onCheckedChange = { viewModel.setMultiRoute(it) }
                                    )
                                    Text(stringResource(R.string.nav_sim_multi_route), fontSize = 14.sp)
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                IconButton(onClick = { showSpeedDialog = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    var selectedTab by remember { mutableStateOf(0) }
                    var searchQuery by remember { mutableStateOf("") }
                    var isSearchVisible by remember { mutableStateOf(false) }
                    val favOrders by viewModel.favOrders.collectAsState()
                    val favRoutes = historyList.filter { it.isFavorite }
                        .sortedBy { viewModel.getFavoriteOrder(it.id.toLongOrNull() ?: 0L) }
                        .sortedBy { it.id.toLongOrNull()?.let { id -> favOrders[id] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE }
                    val allRoutes = historyList

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.weight(1f)
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(stringResource(R.string.joystick_history_favorites), fontSize = 14.sp) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(stringResource(R.string.joystick_history_normal), fontSize = 14.sp) }
                            )
                        }
                        IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }

                    if (isSearchVisible) {
                        val searchTextStyle = MaterialTheme.typography.bodySmall
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = searchTextStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(32.dp)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            decorationBox = { innerTextField ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight()) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(stringResource(R.string.app_search_tips), style = searchTextStyle, color = Color.Gray)
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = ""; isSearchVisible = false }, modifier = Modifier.size(18.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        )
                    }

                    val filteredFavRoutes = if (searchQuery.isBlank()) favRoutes
                        else favRoutes.filter { it.startName.contains(searchQuery, ignoreCase = true) || it.endName.contains(searchQuery, ignoreCase = true) }

                    if (selectedTab == 0) {
                        var draggedId by remember { mutableStateOf<String?>(null) }
                        var dragOffset by remember { mutableStateOf(0f) }
                        val localFavList = remember { mutableStateListOf<RouteInfo>() }

                        LaunchedEffect(filteredFavRoutes) {
                            if (draggedId == null) {
                                localFavList.clear()
                                localFavList.addAll(filteredFavRoutes)
                            }
                        }

                        if (localFavList.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.history_idle), color = Color.Gray)
                            }
                        } else {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .pointerInput(Unit) {
                                        val cardHeightPx = 80.dp.toPx()
                                        val gapPx = 8.dp.toPx()
                                        val itemUnitPx = cardHeightPx + gapPx

                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                val contentY = offset.y + scrollState.value
                                                val idx = (contentY / itemUnitPx).toInt().coerceIn(0, localFavList.lastIndex)
                                                localFavList.clear()
                                                localFavList.addAll(filteredFavRoutes)
                                                draggedId = localFavList.getOrNull(idx)?.id
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (draggedId == null) return@detectDragGesturesAfterLongPress
                                                dragOffset += dragAmount.y
                                                val curIdx = localFavList.indexOfFirst { it.id == draggedId }
                                                if (curIdx < 0) return@detectDragGesturesAfterLongPress
                                                val thresholdPx = cardHeightPx * 0.92f
                                                if (abs(dragOffset) >= thresholdPx) {
                                                    val dir = if (dragOffset > 0) 1 else -1
                                                    val targetIdx = (curIdx + dir).coerceIn(0, localFavList.lastIndex)
                                                    if (targetIdx != curIdx) {
                                                        val temp = localFavList[curIdx]
                                                        localFavList[curIdx] = localFavList[targetIdx]
                                                        localFavList[targetIdx] = temp
                                                    }
                                                    dragOffset -= dir * thresholdPx
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggedId != null) {
                                                    viewModel.setFavoriteOrder(localFavList.mapNotNull { it.id.toLongOrNull() })
                                                }
                                                draggedId = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedId = null
                                                dragOffset = 0f
                                            }
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                localFavList.forEachIndexed { _, route ->
                                    val isDragged = draggedId == route.id
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .zIndex(if (isDragged) 1f else 0f)
                                            .graphicsLayer {
                                                translationY = if (isDragged) dragOffset else 0f
                                                shadowElevation = if (isDragged) 16f else 0f
                                            }
                                    ) {
                                        NavigationHistoryCard(
                                            route = route,
                                            isFav = true,
                                            showMoveButtons = false,
                                            onSelect = { viewModel.selectHistoryRoute(route) },
                                            onToggleFavorite = {
                                                route.id.toLongOrNull()?.let { viewModel.toggleFavorite(it) }
                                            },
                                            onRename = { renameTarget = route; renameText = route.startName },
                                            onDelete = { route.id.toLongOrNull()?.let { viewModel.deleteHistory(it) } }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    } else {
                        val src = if (searchQuery.isBlank()) allRoutes
                            else allRoutes.filter { it.startName.contains(searchQuery, ignoreCase = true) || it.endName.contains(searchQuery, ignoreCase = true) }
                        if (src.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.history_idle), color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(src, key = { "all_${it.id}" }) { route ->
                                    NavigationHistoryCard(
                                        route = route,
                                        isFav = route.isFavorite,
                                        showMoveButtons = false,
                                        onSelect = { viewModel.selectHistoryRoute(route) },
                                        onToggleFavorite = {
                                            route.id.toLongOrNull()?.let { viewModel.toggleFavorite(it) }
                                        },
                                        onRename = { renameTarget = route; renameText = route.startName },
                                        onDelete = { route.id.toLongOrNull()?.let { viewModel.deleteHistory(it) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (candidateRoutes.isNotEmpty()) {
                var selectedIndex by remember { mutableStateOf(0) }
                Dialog(
                    onDismissRequest = { },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val mapView = remember { com.baidu.mapapi.map.MapView(context) }
                            DisposableEffect(Unit) {
                                onDispose { mapView.onDestroy() }
                            }
                            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) { view ->
                                val map = view.map
                                map.clear()
                                val routes = candidateRoutes
                                routes.forEachIndexed { i, route ->
                                    val color = if (i == selectedIndex) android.graphics.Color.GREEN else android.graphics.Color.GRAY
                                    val opt = com.baidu.mapapi.map.PolylineOptions()
                                        .width(8)
                                        .color(color)
                                        .points(route)
                                    map.addOverlay(opt)
                                }
                                val route = routes.getOrNull(selectedIndex)
                                if (!route.isNullOrEmpty()) {
                                    val start = route.first()
                                    val end = route.last()
                                    val startMarker = MarkerOptions()
                                        .position(start)
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding))
                                        .zIndex(9)
                                    map.addOverlay(startMarker)
                                    
                                    val endMarker = MarkerOptions()
                                        .position(end)
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding))
                                        .zIndex(9)
                                    map.addOverlay(endMarker)

                                    val builder = com.baidu.mapapi.model.LatLngBounds.Builder()
                                    route.forEach { builder.include(it) }
                                    val bounds = builder.build()
                                    val update = com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngBounds(bounds, 50, 50, 50, 50)
                                    map.setMapStatus(update)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { selectedIndex = (selectedIndex + 1) % candidateRoutes.size },
                                    shape = RoundedCornerShape(24.dp)
                                ) { Text(stringResource(R.string.nav_sim_switch_route)) }
                                Button(
                                    onClick = { viewModel.chooseCandidate(selectedIndex) },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) { Text(stringResource(R.string.nav_sim_choose_route)) }
                            }
                        }
                    }
                }


            }
        }
    }
}

@Composable
fun NavigationHistoryCard(
    route: RouteInfo,
    isFav: Boolean,
    showMoveButtons: Boolean = false,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showMoveButtons) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text("▲", modifier = Modifier.clickable(onClick = onMoveUp).padding(2.dp), fontSize = 12.sp, color = Color.Gray)
                    Text("▼", modifier = Modifier.clickable(onClick = onMoveDown).padding(2.dp), fontSize = 12.sp, color = Color.Gray)
                }
            }
            Text(
                text = "${route.startName} -> ${route.endName}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
            }
            val context = LocalContext.current
            val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
            val showDeleteConfirm = remember { mutableStateOf(false) }
            var dontRemind by remember { mutableStateOf(false) }
            IconButton(onClick = {
                if (System.currentTimeMillis() < prefs.getLong("delete_dont_remind_until", 0L)) {
                    onDelete()
                } else {
                    showDeleteConfirm.value = true
                    dontRemind = false
                }
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
            if (showDeleteConfirm.value) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm.value = false },
                    title = { Text(stringResource(R.string.common_warning)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.common_delete_item_confirm))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = dontRemind, onCheckedChange = { dontRemind = it })
                                Text(stringResource(R.string.delete_dont_remind_10min), fontSize = 14.sp)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (dontRemind) {
                                prefs.edit().putLong("delete_dont_remind_until", System.currentTimeMillis() + 10 * 60 * 1000).apply()
                            }
                            showDeleteConfirm.value = false; onDelete()
                        }) {
                            Text(stringResource(R.string.common_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm.value = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = if (isFav) Color(0xFFFFB300) else Color.Gray,
                    modifier = Modifier.graphicsLayer(alpha = if (isFav) 1f else 0.4f)
                )
            }
        }
    }
}
