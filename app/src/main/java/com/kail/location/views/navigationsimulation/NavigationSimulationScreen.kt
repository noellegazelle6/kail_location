package com.kail.location.views.navigationsimulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.IntOffset
import com.kail.location.views.locationpicker.LocationPickerActivity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.kail.location.views.common.AppDrawer
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MarkerOptions


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSimulationScreen(
    viewModel: NavigationSimulationViewModel = viewModel(),
    onNavigate: (Int) -> Unit,
    appVersion: String,
    runMode: String,
    onRunModeChange: (String) -> Unit
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
                onRunModeChange = onRunModeChange
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.nav_sim_history_title),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { viewModel.clearHistory() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(stringResource(R.string.nav_sim_clear), fontSize = 12.sp, color = Color.Red)
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(historyList) { route ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { viewModel.selectHistoryRoute(route) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${route.startName} -> ${route.endName}",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        item {
                            com.kail.location.ads.NativeAdCard()
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
