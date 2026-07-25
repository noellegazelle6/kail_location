package com.kail.location.views.locationpicker

import com.kail.location.views.locationpicker.LocationPickerActivity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.MapView
import com.kail.location.R
import com.kail.location.viewmodels.LocationPickerViewModel.PoiInfo
import com.kail.location.viewmodels.LocationPickerViewModel.UpdateInfo
import com.kail.location.viewmodels.LocationPickerViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ListItem
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import android.widget.ImageView
import com.kail.location.views.common.AppDrawer
import com.baidu.mapapi.map.BaiduMap

import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    mapView: MapView?,
    isMocking: Boolean,
    isStarting: Boolean = false,
    targetLocation: com.baidu.mapapi.model.LatLng,
    mapType: Int,
    currentCity: String?,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onDeveloperModeSelected: () -> Unit = {},
    onXposedSettingsSelected: () -> Unit = {},
    onToggleMock: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onLocate: () -> Unit,
    onLocationInputConfirm: (Double, Double, Boolean) -> Unit,
    onMapTypeChange: (Int) -> Unit,
    onNavigate: (Int) -> Unit,
    appVersion: String,
    searchResults: List<Map<String, Any>>?,
    onSearch: (String) -> Unit,
    onClearSearchResults: () -> Unit,
    onSelectSearchResult: (Map<String, Any>) -> Unit,
    onNavigateUp: () -> Unit,
    isPickMode: Boolean = false,
    onConfirmSelection: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Map Type Dialog State
    var showMapTypeDialog by remember { mutableStateOf(false) }
    
    // Location Input Dialog State
    var showLocationInputDialog by remember { mutableStateOf(false) }

    // Search State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    if (showLocationInputDialog) {
        LocationInputDialog(
            onDismiss = { showLocationInputDialog = false },
            onConfirm = { lat, lng, isBd09 ->
                onLocationInputConfirm(lat, lng, isBd09)
                showLocationInputDialog = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "LocationSimulation",
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
                if (isSearchActive) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { 
                            searchQuery = it
                            onSearch(it)
                        },
                        onSearch = { onSearch(it) },
                        active = true,
                        onActiveChange = { isSearchActive = it },
                        placeholder = { Text(stringResource(R.string.route_plan_search_hint)) },
                        leadingIcon = {
                            IconButton(onClick = { 
                                isSearchActive = false
                                searchQuery = ""
                                onClearSearchResults()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { 
                                    searchQuery = ""
                                    onSearch("")
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    ) {
                        if (searchResults != null) {
                            LazyColumn {
                                items(searchResults.size) { index ->
                                    val item = searchResults[index]
                                    val name = item[LocationPickerViewModel.POI_NAME].toString()
                                    val address = item[LocationPickerViewModel.POI_ADDRESS].toString()
                                    ListItem(
                                        headlineContent = { Text(name) },
                                        supportingContent = { Text(address) },
                                        modifier = Modifier.clickable {
                                            onSelectSearchResult(item)
                                            isSearchActive = false
                                            searchQuery = ""
                                            onClearSearchResults()
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
                            if (isPickMode) {
                                IconButton(onClick = onNavigateUp) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.animateTo(DrawerValue.Open, androidx.compose.animation.core.tween(durationMillis = 160)) } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                                }
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
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = {
                            if (isStarting) return@FloatingActionButton
                            if (isPickMode) {
                                onConfirmSelection()
                            } else {
                                onToggleMock()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        if (isStarting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else if (isPickMode) {
                            Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color.White)
                        } else {
                            if (isMocking) {
                                Icon(painterResource(R.drawable.ic_stop_black_24dp), contentDescription = "Stop", tint = Color.White)
                            } else {
                                Icon(painterResource(R.drawable.ic_play_arrow_black_24dp), contentDescription = "Start", tint = Color.White)
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                if (mapView != null) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                }

                // Map Controls Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MapControlButton(
                        iconRes = R.drawable.ic_map,
                        onClick = { showMapTypeDialog = true }
                    )
                    MapControlButton(
                        iconRes = R.drawable.ic_input,
                        onClick = { showLocationInputDialog = true }
                    )
                    MapControlButton(
                        iconRes = R.drawable.ic_home_position,
                        onClick = onLocate
                    )
                    MapControlButton(
                        iconRes = R.drawable.ic_zoom_in,
                        onClick = onZoomIn
                    )
                    MapControlButton(
                        iconRes = R.drawable.ic_zoom_out,
                        onClick = onZoomOut
                    )
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
                            onMapTypeChange(BaiduMap.MAP_TYPE_NORMAL)
                            showMapTypeDialog = false 
                        }
                    ) {
                        RadioButton(selected = mapType == BaiduMap.MAP_TYPE_NORMAL, onClick = { 
                            onMapTypeChange(BaiduMap.MAP_TYPE_NORMAL)
                            showMapTypeDialog = false 
                        })
                        Text(stringResource(R.string.route_plan_normal))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { 
                            onMapTypeChange(BaiduMap.MAP_TYPE_SATELLITE)
                            showMapTypeDialog = false 
                        }
                    ) {
                        RadioButton(selected = mapType == BaiduMap.MAP_TYPE_SATELLITE, onClick = { 
                            onMapTypeChange(BaiduMap.MAP_TYPE_SATELLITE)
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

    // 移除选点页的更新检测与弹窗
}

/**
 * 更新提示对话框
 * 显示新版本信息并提供下载选项。
 *
 * @param info 更新信息对象
 * @param onDismiss 取消/关闭回调
 * @param onConfirm 确认下载回调
 * @param onSearch 搜索关键字变化时的回调。
 * @param searchResults 搜索结果列表。
 * @param onSelectSearchResult 选中搜索结果时的回调。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_found_title, info.version)) },
        text = {
             Text(info.content)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.update_download))
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
