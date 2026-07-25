package com.kail.location.views.locationsimulation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
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
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.kail.location.R
import com.kail.location.viewmodels.LocationSimulationViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import com.kail.location.views.common.DrawerHeader
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import android.content.Intent
import android.net.Uri
import com.kail.location.views.common.UpdateDialog
import com.kail.location.models.HistoryRecord
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings

import com.kail.location.views.common.AppDrawer

/**
 * 位置模拟主界面组合项。
 *
 * 该界面提供了位置模拟的核心功能，包括：
 * 1. 显示当前选定的模拟目标位置信息（名称、地址、经纬度）。
 * 2. 提供开始/停止模拟的控制按钮。
 * 3. 提供摇杆功能的开关控制。
 * 4. 展示历史记录列表（当前为占位符状态）。
 * 5. 集成侧边栏导航，支持跳转到其他功能模块（如路线模拟、设置等）。
 *
 * @param viewModel 位置模拟的 ViewModel，用于管理位置信息、模拟状态和更新检查。
 * @param onNavigate 导航回调，用于处理侧边栏菜单点击事件，跳转到指定 ID 的目标界面。
 * @param onAddLocation 添加位置回调，当用户点击添加按钮时触发。
 * @param appVersion 当前应用版本号，显示在侧边栏头部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSimulationScreen(
    locationInfo: LocationSimulationViewModel.LocationInfo,
    isSimulating: Boolean,
    isStarting: Boolean,
    isJoystickEnabled: Boolean,
    stepSimulationEnabled: Boolean,
    stepCadenceSpm: Float,
    historyRecords: List<HistoryRecord>,
    selectedRecordId: Int?,
    onToggleSimulation: () -> Unit,
    onJoystickToggle: (Boolean) -> Unit,
    onStepSimulationToggle: (Boolean) -> Unit,
    onStepCadenceChange: (Float) -> Unit,
    onRecordSelect: (HistoryRecord) -> Unit,
    onRecordDelete: (Int) -> Unit,
    onRecordRename: (Int, String) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onDeveloperModeSelected: () -> Unit = {},
    onXposedSettingsSelected: () -> Unit = {},
    onNavigate: (Int) -> Unit,
    onAddLocation: () -> Unit,
    appVersion: String,
    onCheckUpdate: () -> Unit,
    onMoveFavUp: (Int) -> Unit = {},
    onMoveFavDown: (Int) -> Unit = {},
    onSetFavoriteOrder: (List<Int>) -> Unit = {}
) {
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<HistoryRecord?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Refresh history when the screen is displayed
    LaunchedEffect(Unit) {
        // This effect will run when the composition is first created.
        // However, if we want to refresh every time we navigate back to this screen,
        // we might need a signal from the ViewModel or rely on Activity's onResume.
        // For now, let's rely on the ViewModel being scoped to the Activity/Fragment
        // and we might need to trigger a reload if the data is stale.
        // But since this is a Composable function, it might not be the best place for lifecycle events.
        // Let's assume the ViewModel handles data loading, or the Activity calls it.
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
                onXposedSettingsSelected = onXposedSettingsSelected,
                scope = scope
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.loc_sim_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.animateTo(DrawerValue.Open, androidx.compose.animation.core.tween(durationMillis = 160)) } }) {
                            Icon(
                                Icons.Default.Menu,
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
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                // Target Location Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.loc_sim_target),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = locationInfo.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = locationInfo.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.loc_sim_lat_lng,
                                    String.format("%.2f", locationInfo.longitude),
                                    String.format("%.2f", locationInfo.latitude)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onToggleSimulation,
                                    enabled = !isStarting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSimulating) Color.Red else MaterialTheme.colorScheme.primary,
                                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                        disabledContentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    if (isStarting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.sim_starting))
                                    } else {
                                        Text(
                                            if (isSimulating) stringResource(R.string.loc_sim_stop) else stringResource(
                                                R.string.loc_sim_start
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))

                                // Joystick Toggle
                                Text(
                                    text = stringResource(R.string.loc_sim_joystick),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = isJoystickEnabled,
                                    onCheckedChange = onJoystickToggle,
                                    modifier = Modifier.scale(0.8f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { showSettingsDialog = true }) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Plus Button
                    FloatingActionButton(
                        onClick = onAddLocation,
                        containerColor = MaterialTheme.colorScheme.secondary, // Greenish color
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = 0.dp) // Adjust position to overlap
                            .size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }

                val favRecords = historyRecords.filter { it.isFavorite }
                    .sortedWith(compareBy<com.kail.location.models.HistoryRecord> { it.favoriteOrder }.thenByDescending { it.favoriteTime })
                var selectedTab by remember { mutableStateOf(0) }
                var searchQuery by remember { mutableStateOf("") }
                var isSearchVisible by remember { mutableStateOf(false) }

                val filteredFavRecords = if (searchQuery.isBlank()) favRecords
                    else favRecords.filter { it.name.contains(searchQuery, ignoreCase = true) || it.displayTime.contains(searchQuery, ignoreCase = true) }

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TabRow(selectedTabIndex = selectedTab, modifier = Modifier.weight(1f)) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.joystick_history_favorites), fontSize = 14.sp) })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.joystick_history_normal), fontSize = 14.sp) })
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

                if (selectedTab == 0) {
                    var draggedId by remember { mutableStateOf<Int?>(null) }
                    var dragOffset by remember { mutableStateOf(0f) }
                    val localFavList = remember { mutableStateListOf<HistoryRecord>() }

                    LaunchedEffect(filteredFavRecords) {
                        if (draggedId == null) {
                            localFavList.clear()
                            localFavList.addAll(filteredFavRecords)
                        }
                    }

                    if (localFavList.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                                            localFavList.addAll(filteredFavRecords)
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
                                                onSetFavoriteOrder(localFavList.map { it.id })
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
                            localFavList.forEachIndexed { _, record ->
                                val isDragged = draggedId == record.id
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragged) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDragged) dragOffset else 0f
                                            shadowElevation = if (isDragged) 16f else 0f
                                        }
                                ) {
                                    historyRecordCard(
                                        record = record,
                                        isFav = true,
                                        showMoveButtons = false,
                                        onToggleFavorite = { onToggleFavorite(record.id) },
                                        onRename = { renameTarget = record; renameText = record.name },
                                        onRecordSelect = onRecordSelect,
                                        onRecordDelete = { onRecordDelete(record.id) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                } else {
                    val src = if (searchQuery.isBlank()) historyRecords
                        else historyRecords.filter { it.name.contains(searchQuery, ignoreCase = true) || it.displayTime.contains(searchQuery, ignoreCase = true) }
                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(src.sortedByDescending { it.timestamp }, key = { "all_${it.id}" }) { record ->
                            historyRecordCard(record = record, isFav = record.isFavorite, showMoveButtons = false, onToggleFavorite = onToggleFavorite, onRename = { renameTarget = it; renameText = it.name }, onRecordSelect = onRecordSelect, onRecordDelete = onRecordDelete)
                        }
                    }
                }

                // Bottom Disclaimer
                Text(
                    text = stringResource(R.string.app_statement),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.location_rename_title)) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it })
            },
            confirmButton = {
                TextButton(onClick = { onRecordRename(renameTarget!!.id, renameText); renameTarget = null }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showSettingsDialog) {
        LocationSettingsDialog(
            stepSimulationEnabled = stepSimulationEnabled,
            stepCadenceSpm = stepCadenceSpm,
            runMode = runMode,
            onDismiss = { showSettingsDialog = false },
            onStepSimulationToggle = onStepSimulationToggle,
            onStepCadenceChange = onStepCadenceChange
        )
    }
}

@Composable
fun historyRecordCard(
    record: com.kail.location.models.HistoryRecord,
    isFav: Boolean,
    showMoveButtons: Boolean = false,
    onToggleFavorite: (Int) -> Unit,
    onRename: (com.kail.location.models.HistoryRecord) -> Unit,
    onRecordSelect: (com.kail.location.models.HistoryRecord) -> Unit,
    onRecordDelete: (Int) -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { onRecordSelect(record) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showMoveButtons) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 8.dp)) {
                    Text("▲", modifier = Modifier.clickable(onClick = onMoveUp).padding(2.dp), fontSize = 12.sp, color = Color.Gray)
                    Text("▼", modifier = Modifier.clickable(onClick = onMoveDown).padding(2.dp), fontSize = 12.sp, color = Color.Gray)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = record.name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = record.displayTime, fontSize = 12.sp, color = Color.Gray)
            }
            Row {
                IconButton(onClick = { onToggleFavorite(record.id) }) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = if (isFav) Color(0xFFFFB300) else Color.Gray,
                        modifier = Modifier.graphicsLayer(alpha = if (isFav) 1f else 0.4f)
                    )
                }
                IconButton(onClick = { onRename(record) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                }
                val context = LocalContext.current
                val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                val showDeleteConfirm = remember { mutableStateOf(false) }
                var dontRemind by remember { mutableStateOf(false) }
                IconButton(onClick = {
                    if (System.currentTimeMillis() < prefs.getLong("delete_dont_remind_until", 0L)) {
                        onRecordDelete(record.id)
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
                                showDeleteConfirm.value = false; onRecordDelete(record.id)
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

@Composable
fun LocationSettingsDialog(
    stepSimulationEnabled: Boolean,
    stepCadenceSpm: Float,
    runMode: String,
    onDismiss: () -> Unit,
    onStepSimulationToggle: (Boolean) -> Unit,
    onStepCadenceChange: (Float) -> Unit
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
                Text(
                    text = stringResource(R.string.route_sim_speed_btn),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.route_sim_step_text),
                        fontSize = 14.sp,
                        color = if (canUseStepFreq) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                    Switch(
                        checked = stepSimulationEnabled,
                        onCheckedChange = {
                            if (!canUseStepFreq) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.vm_step_root_required),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onStepSimulationToggle(it)
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

                if (stepSimulationEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val stepsPerSecond = (stepCadenceSpm / 60f)
                    val kmh = (stepsPerSecond * 0.7f * 3.6f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.route_sim_cadence_text), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.route_sim_cadence_format, stepCadenceSpm.toInt(), ((kmh * 10).toInt() / 10f)), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = stepCadenceSpm,
                        onValueChange = { onStepCadenceChange((it + 0.5f).toInt().toFloat()) },
                        valueRange = 60f..180f,
                        steps = 11,
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
