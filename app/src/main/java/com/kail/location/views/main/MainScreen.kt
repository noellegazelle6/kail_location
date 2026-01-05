package com.kail.location.views.main

import com.kail.location.views.main.MainActivity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
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
import com.kail.location.viewmodels.MainViewModel.PoiInfo
import com.kail.location.viewmodels.MainViewModel.UpdateInfo
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ListItem
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView


/**
 * 主屏幕 Composable
 * 整合了地图视图、抽屉导航、浮动按钮、缩放控件以及 POI 信息卡片。
 *
 * @param mapView 百度地图 View 实例
 * @param isMocking 是否正在模拟位置
 * @param onToggleMock 切换模拟状态的回调
 * @param onZoomIn 放大地图回调
 * @param onZoomOut 缩小地图回调
 * @param onLocate 定位到当前位置回调
 * @param onLocationInputConfirm 输入经纬度确认回调
 * @param onMapTypeChange 切换地图类型回调
 * @param onNavigate 导航菜单点击回调
 * @param appVersion 应用版本号
 * @param selectedPoi 当前选中的 POI 信息
 * @param onPoiClose 关闭 POI 卡片回调
 * @param onPoiSave 保存 POI 回调
 * @param onPoiCopy 复制 POI 信息回调
 * @param onPoiShare 分享 POI 信息回调
 * @param onPoiFly "飞行"（模拟位置）到 POI 回调
 * @param updateInfo 更新信息（若有）
 * @param onUpdateDismiss 关闭更新对话框回调
 * @param onUpdateConfirm 确认更新（下载）回调
 * @param searchResults 搜索结果列表
 * @param onSearch 发起搜索回调
 * @param onClearSearchResults 清除搜索结果回调
 * @param onSelectSearchResult 选中搜索结果回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mapView: MapView?,
    isMocking: Boolean,
    onToggleMock: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onLocate: () -> Unit,
    onLocationInputConfirm: (Double, Double, Boolean) -> Unit,
    onMapTypeChange: (Int) -> Unit,
    onNavigate: (Int) -> Unit,
    appVersion: String,
    selectedPoi: PoiInfo?,
    onPoiClose: () -> Unit,
    onPoiSave: (PoiInfo) -> Unit,
    onPoiCopy: (PoiInfo) -> Unit,
    onPoiShare: (PoiInfo) -> Unit,
    onPoiFly: (PoiInfo) -> Unit,
    updateInfo: UpdateInfo?,
    onUpdateDismiss: () -> Unit,
    onUpdateConfirm: (String) -> Unit,
    searchResults: List<Map<String, Any>>,
    onSearch: (String) -> Unit,
    onClearSearchResults: () -> Unit,
    onSelectSearchResult: (Map<String, Any>) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Map Type Dialog State
    var showMapTypeDialog by remember { mutableStateOf(false) }
    
    // Location Input Dialog State
    var showLocationInputDialog by remember { mutableStateOf(false) }

    // Search State
    var isSearching by remember { mutableStateOf(false) }
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
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_route_simulation) } }
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
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onToggleMock,
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    if (isMocking) {
                        Icon(painterResource(R.drawable.ic_stop_black_24dp), contentDescription = "Stop", tint = Color.White)
                    } else {
                        Icon(painterResource(R.drawable.ic_play_arrow_black_24dp), contentDescription = "Start", tint = Color.White)
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                // Map View
                if (mapView != null) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("地图加载失败，请检查权限或网络", color = Color.Red)
                    }
                }

                // Map Controls Overlay
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

                // Zoom and Location Controls
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
                        onClick = onLocate
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MapControlButton(
                        iconRes = R.drawable.ic_zoom_in,
                        onClick = onZoomIn
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MapControlButton(
                        iconRes = R.drawable.ic_zoom_out,
                        onClick = onZoomOut
                    )
                }

                // POI Info Card
                if (selectedPoi != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        PoiInfoCard(
                            poi = selectedPoi,
                            onClose = onPoiClose,
                            onSave = { onPoiSave(selectedPoi) },
                            onCopy = { onPoiCopy(selectedPoi) },
                            onShare = { onPoiShare(selectedPoi) },
                            onFly = { onPoiFly(selectedPoi) }
                        )
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
                        modifier = Modifier.fillMaxWidth().clickable { onMapTypeChange(1); showMapTypeDialog = false }
                    ) {
                        RadioButton(selected = mapView?.map?.mapType == 1, onClick = { onMapTypeChange(1); showMapTypeDialog = false })
                        Text("普通地图")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onMapTypeChange(2); showMapTypeDialog = false }
                    ) {
                        RadioButton(selected = mapView?.map?.mapType == 2, onClick = { onMapTypeChange(2); showMapTypeDialog = false })
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

    if (updateInfo != null) {
        UpdateDialog(
            info = updateInfo,
            onDismiss = onUpdateDismiss,
            onConfirm = { onUpdateConfirm(updateInfo.downloadUrl) }
        )
    }
}

/**
 * 更新提示对话框
 * 显示新版本信息并提供下载选项。
 *
 * @param info 更新信息对象
 * @param onDismiss 取消/关闭回调
 * @param onConfirm 确认下载回调
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本: ${info.version}") },
        text = {
             Text(info.content)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * POI 信息展示卡片
 * 显示选中地点的经纬度、地址，并提供操作按钮（飞行、保存、复制、分享）。
 *
 * @param poi POI 信息对象
 * @param onClose 关闭卡片回调
 * @param onSave 保存回调
 * @param onCopy 复制回调
 * @param onShare 分享回调
 * @param onFly 飞行（模拟位置）回调
 */
@Composable
fun PoiInfoCard(
    poi: PoiInfo,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onFly: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFly) {
                    Icon(painterResource(R.drawable.ic_fly), contentDescription = "Fly", tint = MaterialTheme.colorScheme.secondary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Lng: ${poi.longitude}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Lat: ${poi.latitude}", style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = poi.address, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onSave) {
                    Icon(painterResource(R.drawable.ic_save), contentDescription = "Save", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = onCopy) {
                    Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = onShare) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = "Share", tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}


@Composable
fun DrawerHeader(version: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp)
    ) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    setImageResource(R.mipmap.ic_launcher_round)
                }
            },
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = version,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_msg), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.nav_app_tips),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
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

/**
 * 经纬度输入对话框
 * 允许用户手动输入经纬度并选择坐标系（百度/GPS）。
 *
 * @param onDismiss 取消/关闭回调
 * @param onConfirm 确认回调，参数为：(纬度, 经度, 是否为百度坐标)
 */
@Composable
fun LocationInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Boolean) -> Unit
) {
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var isBd09 by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.input_position_ok)) },
        text = {
            Column {
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it },
                    label = { Text(stringResource(R.string.label_longitude)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text(stringResource(R.string.label_latitude)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isBd09, onClick = { isBd09 = true })
                    Text(stringResource(R.string.input_position_baidu))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !isBd09, onClick = { isBd09 = false })
                    Text(stringResource(R.string.input_position_gps))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val latVal = lat.toDoubleOrNull()
                val lngVal = lng.toDoubleOrNull()
                if (latVal != null && lngVal != null) {
                    onConfirm(latVal, lngVal, isBd09)
                }
            }) {
                Text(stringResource(R.string.input_position_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.input_position_cancel))
            }
        }
    )
}
