package com.kail.location.views.routesimulation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import kotlinx.coroutines.launch
import com.kail.location.models.RouteInfo
import com.kail.location.models.SimulationSettings
import com.kail.location.models.TransportMode
import com.kail.location.R
import com.kail.location.viewmodels.RouteSimulationViewModel
import com.kail.location.views.common.DrawerHeader

import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import android.content.Intent
import android.net.Uri
import com.kail.location.views.common.UpdateDialog
import com.kail.location.views.common.AppDrawer


/**
 * 路线模拟主界面
 * 展示目标路线与历史路线列表，并提供导航抽屉、更新提示与设置弹窗。
 *
 * @param viewModel 路线模拟 ViewModel，负责数据与状态管理
 * @param onNavigate 导航回调，依据菜单项跳转到对应页面
 * @param onAddRouteClick 添加路线点击回调，切换到路线规划界面
 * @param appVersion 应用版本号，用于抽屉头部展示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSimulationScreen(
    viewModel: RouteSimulationViewModel,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onDeveloperModeSelected: () -> Unit = {},
    onXposedSettingsSelected: () -> Unit = {},
    onNavigate: (Int) -> Unit,
    onAddRouteClick: () -> Unit,
    onEditRoute: (String) -> Unit = {},
    appVersion: String,
    onStartSimulation: (SimulationSettings) -> Unit,
    onStopSimulation: () -> Unit
) {
    // State
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RouteInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    
    val historyRoutes by viewModel.historyRoutes.collectAsState()
    val selectedId by viewModel.selectedRouteId.collectAsState()
    val pendingName by viewModel.pendingRouteName.collectAsState()
    val noName = stringResource(R.string.route_sim_no_name)
    val currentRoute = if (selectedId != null && selectedId != "pending") {
        historyRoutes.firstOrNull { it.id == selectedId } ?: RouteInfo("-", noName, noName, "")
    } else if (pendingName != null) {
        RouteInfo("pending", pendingName!!, pendingName!!, "")
    } else {
        historyRoutes.firstOrNull() ?: RouteInfo("-", noName, noName, "")
    }
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isSimulating by viewModel.isSimulating.collectAsState()
    val isStarting by viewModel.isStarting.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val runMode by viewModel.runMode.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val installUri by viewModel.installUri.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }
    
    if (updateInfo != null) {
        com.kail.location.views.common.UpdateDownloadDialog(
            info = updateInfo!!,
            downloading = isDownloading,
            progress = downloadProgress,
            onDismiss = { viewModel.dismissUpdateDialog() },
            onStartDownload = { viewModel.startUpdateDownload(context) }
        )
    }
    if (installUri != null) {
        LaunchedEffect(installUri) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(installUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
            viewModel.clearInstallUri()
            viewModel.dismissUpdateDialog()
        }
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
                TopAppBar(
                    title = { Text(stringResource(R.string.route_sim_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.animateTo(DrawerValue.Open, androidx.compose.animation.core.tween(durationMillis = 160)) } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Target Route Card
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                    ) {
                        RouteCard(
                            route = currentRoute,
                            isTarget = true,
                            settings = settings,
                            onSettingsClick = { showSettingsDialog = true },
                            onLoopToggle = { viewModel.updateLoop(it) },
                            onStartSimulation = onStartSimulation,
                            isSimulating = isSimulating,
                            isStarting = isStarting,
                            onStopSimulation = onStopSimulation,
                            isPaused = isPaused,
                            onPauseResume = { if (isPaused) viewModel.resumeSimulation() else viewModel.pauseSimulation() },
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        
                        // FAB overlapping the card
                        FloatingActionButton(
                            onClick = onAddRouteClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                        }
                    }

                    var selectedTab by remember { mutableStateOf(0) }
                    var searchQuery by remember { mutableStateOf("") }
                    var isSearchVisible by remember { mutableStateOf(false) }
                    val favRoutes = historyRoutes.filter { it.isFavorite }
                        .sortedWith(compareBy<RouteInfo> { it.favoriteOrder }.thenByDescending { it.favoriteTime })
                    val allRoutes = historyRoutes.sortedByDescending { it.id.toLongOrNull() ?: 0L }

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
                                text = { Text(stringResource(R.string.route_sim_history), fontSize = 14.sp) }
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
                                        val cardHeightPx = 72.dp.toPx()
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
                                                    viewModel.setFavoriteOrder(localFavList.map { it.id })
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
                                        RouteHistoryCard(
                                            route = route,
                                            isFav = true,
                                            showMoveButtons = false,
                                            onSelect = { viewModel.clearPendingRoute(); viewModel.selectRoute(route.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(route.id) },
                                            onEdit = { onEditRoute(route.id) },
                                            onRename = { renameTarget = route; renameText = route.startName },
                                            onDelete = { viewModel.deleteRoute(route.id) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    } else {
                        val src = if (searchQuery.isBlank()) allRoutes
                            else allRoutes.filter { it.startName.contains(searchQuery, ignoreCase = true) || it.endName.contains(searchQuery, ignoreCase = true) }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(src, key = { "all_${it.id}" }) { route ->
                                RouteHistoryCard(
                                    route = route,
                                    isFav = route.isFavorite,
                                    showMoveButtons = false,
                                    onSelect = { viewModel.clearPendingRoute(); viewModel.selectRoute(route.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(route.id) },
                                    onEdit = { onEditRoute(route.id) },
                                    onRename = { renameTarget = route; renameText = route.startName },
                                    onDelete = { viewModel.deleteRoute(route.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            settings = settings,
            runMode = runMode,
            onDismiss = { showSettingsDialog = false },
            onSettingsChange = {
                if (settings.speed != it.speed) viewModel.updateSpeed(it.speed)
                if (settings.isLoop != it.isLoop) viewModel.updateLoop(it.isLoop)
                if (settings.speedFluctuation != it.speedFluctuation) viewModel.updateSpeedFluctuation(it.speedFluctuation)
                if (settings.stepFreqSimulation != it.stepFreqSimulation) viewModel.updateStepFreqSimulation(it.stepFreqSimulation)
                if (settings.stepCadenceSpm != it.stepCadenceSpm) viewModel.updateStepCadenceSpm(it.stepCadenceSpm)
                if (settings.mode != it.mode) viewModel.updateMode(it.mode)
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
                TextButton(onClick = { viewModel.renameRoute(renameTarget!!.id, renameText); renameTarget = null }) { Text(stringResource(R.string.route_sim_rename_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.route_sim_rename_cancel)) }
            }
        )
    }
}

/**
 * 路线卡片组件
 * 展示单条路线的起止信息，作为目标路线时支持启动模拟、打开设置与循环开关。
 *
 * @param route 路线信息数据
 * @param isTarget 是否为目标路线卡片；为 true 时显示更多操作
 * @param settings 当为目标卡片时的模拟设置；为空则不显示设置与循环开关
 * @param onSettingsClick 设置点击回调
 * @param onLoopToggle 循环开关变更回调
 */
@Composable
fun RouteCard(
    route: RouteInfo,
    isTarget: Boolean,
    settings: SimulationSettings? = null,
    onSettingsClick: (() -> Unit)? = null,
    onLoopToggle: ((Boolean) -> Unit)? = null,
    onStartSimulation: ((SimulationSettings) -> Unit)? = null,
    isSimulating: Boolean = false,
    isStarting: Boolean = false,
    onStopSimulation: (() -> Unit)? = null,
    isPaused: Boolean = false,
    onPauseResume: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isTarget) {
                Text(
                    text = stringResource(R.string.route_sim_target),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                // Route Visuals (Icons and Line)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    // Start Icon
                    Icon(
                        painter = painterResource(id = R.drawable.ic_home_position),
                        contentDescription = "Start",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    // Dotted Line (Simulated with Box)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                    
                    // End Icon
                    Icon(
                        painter = painterResource(id = R.drawable.ic_position),
                        contentDescription = "End",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Route Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.startName + route.distance,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = route.endName,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isTarget && settings != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isStarting) {
                        Button(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                disabledContentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sim_starting), fontSize = 14.sp)
                        }
                    } else if (!isSimulating) {
                        Button(
                            onClick = { onStartSimulation?.invoke(settings!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) { Text(stringResource(R.string.route_sim_start), fontSize = 14.sp) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onPauseResume?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                            ) { Text(if (isPaused) stringResource(R.string.route_sim_resume) else stringResource(R.string.route_sim_pause), fontSize = 14.sp) }
                            Button(
                                onClick = { onStopSimulation?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                            ) { Text(stringResource(R.string.route_sim_stop), fontSize = 14.sp) }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.route_sim_speed_btn),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onSettingsClick?.invoke() }
                                .padding(end = 4.dp)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bike), // Using existing icon
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onSettingsClick?.invoke() }
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = stringResource(R.string.route_sim_loop),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Switch(
                            checked = settings.isLoop,
                            onCheckedChange = onLoopToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 组件缩放扩展
 * 为任意 Modifier 增加按比例缩放的效果。
 *
 * @param scale 缩放比例，1.0 为原始大小
 * @return 新的 Modifier，包含缩放效果
 */
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

/**
 * 模拟设置弹窗
 * 允许用户调整运动速度、交通方式、速度浮动与步频相关设置。
 *
 * @param settings 当前模拟设置状态
 * @param onDismiss 关闭弹窗回调
 * @param onSettingsChange 设置变更回调
 */
@Composable
fun SettingsDialog(
    settings: SimulationSettings,
    onDismiss: () -> Unit,
    onSettingsChange: (SimulationSettings) -> Unit,
    runMode: String = "developer"
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val canUseStepFreq = runMode == "root" || runMode == "xposed" || runMode == "sandbox"
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Speed Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.route_sim_speed_text), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    Text("${settings.speed} km/h", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }

                // Speed Slider
                Slider(
                    value = settings.speed,
                    onValueChange = { onSettingsChange(settings.copy(speed = (it * 10).toInt() / 10f)) },
                    valueRange = 0f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Transport Modes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TransportMode.values().forEach { mode ->
                        Icon(
                            painter = painterResource(id = getModeIcon(mode)),
                            contentDescription = mode.name,
                            tint = if (settings.mode == mode) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onSettingsChange(settings.copy(mode = mode)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speed Fluctuation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.route_sim_speed_fluctuation), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = settings.speedFluctuation,
                        onCheckedChange = { onSettingsChange(settings.copy(speedFluctuation = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.route_sim_step_text), fontSize = 14.sp, color = if (canUseStepFreq) MaterialTheme.colorScheme.onSurface else Color.Gray)
                    Switch(
                        checked = settings.stepFreqSimulation,
                        onCheckedChange = { 
                            if (!canUseStepFreq) {
                                android.widget.Toast.makeText(
                                    context, 
                                    context.getString(R.string.vm_step_root_required),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onSettingsChange(settings.copy(stepFreqSimulation = it))
                            }
                        },
                        enabled = canUseStepFreq,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }

                if (settings.stepFreqSimulation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val stride = when (settings.mode) {
                        TransportMode.Walk -> 0.7f
                        TransportMode.Run -> 1.0f
                        else -> 0.8f
                    }
                    val stepsPerSecond = (settings.stepCadenceSpm / 60f)
                    val kmh = (stepsPerSecond * stride * 3.6f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.route_sim_cadence_text), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.route_sim_cadence_format, settings.stepCadenceSpm.toInt(), ((kmh * 10).toInt() / 10f)), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = settings.stepCadenceSpm,
                        onValueChange = { onSettingsChange(settings.copy(stepCadenceSpm = (it + 0.5f).toInt().toFloat())) },
                        valueRange = 60f..180f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

/**
 * 根据交通方式返回对应图标资源
 *
 * @param mode 交通方式
 * @return 对应的 drawable 资源 ID
 */
fun getModeIcon(mode: TransportMode): Int {
    return when (mode) {
        TransportMode.Walk -> R.drawable.ic_walk
        TransportMode.Run -> R.drawable.ic_run
        TransportMode.Bike -> R.drawable.ic_bike
        TransportMode.Car -> R.drawable.ic_move // Placeholder if ic_car doesn't exist
        TransportMode.Plane -> R.drawable.ic_fly
    }
}

@Composable
fun RouteHistoryCard(
    route: RouteInfo,
    isFav: Boolean,
    showMoveButtons: Boolean = false,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showMoveButtons) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("▲", modifier = Modifier.clickable(onClick = onMoveUp).padding(2.dp), fontSize = 12.sp, color = Color.Gray)
                    Text("▼", modifier = Modifier.clickable(onClick = onMoveDown).padding(2.dp), fontSize = 12.sp, color = Color.Gray)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = route.startName, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = route.endName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Row {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = if (isFav) Color(0xFFFFB300) else Color.Gray,
                        modifier = Modifier.graphicsLayer(alpha = if (isFav) 1f else 0.4f)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Place, contentDescription = "Edit Route", tint = MaterialTheme.colorScheme.primary)
                }
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
            }
        }
    }
}
