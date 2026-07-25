package com.kail.location.views.joystick

import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TabRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.kail.location.R
import com.kail.location.viewmodels.SettingsViewModel
import com.kail.location.views.history.HistoryActivity
import com.kail.location.views.locationpicker.LocationPickerActivity
import com.kail.location.viewmodels.LocationPickerViewModel
import androidx.preference.PreferenceManager

/**
 * 历史记录浮窗的组合函数。
 * 在悬浮窗中显示历史记录列表。
 *
 * @param historyRecords 要展示的历史记录列表。
 * @param onClose 点击关闭按钮的回调。
 * @param onWindowDrag 悬浮窗拖动回调（dx, dy）。
 * @param onSelectRecord 选中某条历史记录时的回调。
 * @param onSearch 搜索关键字变化时的回调。
 */
@Composable
fun JoyStickHistoryOverlay(
    historyRecords: List<Map<String, Any>>,
    onClose: () -> Unit,
    onWindowDrag: (Float, Float) -> Unit,
    onSelectRecord: (Map<String, Any>) -> Unit,
    onSearch: (String) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onMoveFavUp: (String) -> Unit = {},
    onMoveFavDown: (String) -> Unit = {},
    onSetFavoriteOrder: (List<String>) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    val winW = remember {
        (prefs.getString(SettingsViewModel.KEY_FLOATING_WINDOW_WIDTH, "300") ?: "300").toIntOrNull() ?: 300
    }.dp
    val winH = remember {
        (prefs.getString(SettingsViewModel.KEY_FLOATING_WINDOW_HEIGHT, "500") ?: "500").toIntOrNull() ?: 500
    }.dp
    val scaleFactor = (winW / 300.dp).coerceIn(0.4f, 2f)

    val filteredRecords = remember(historyRecords, searchQuery) {
        if (searchQuery.isBlank()) historyRecords
        else historyRecords.filter { r ->
            val name = (r[HistoryActivity.KEY_LOCATION] as? String) ?: ""
            val time = (r[HistoryActivity.KEY_TIME] as? String) ?: ""
            name.contains(searchQuery, ignoreCase = true) || time.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .width(winW)
            .height(winH)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header (draggable area)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onWindowDrag(dragAmount.x, dragAmount.y)
                        }
                    }
            ) {
                Text(
                    text = stringResource(R.string.joystick_history_tips),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(30.dp).padding(2.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_pin),
                            contentDescription = "Pin",
                            tint = if (isPinned) Color(0xFFFFB300) else Color.White.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp).padding(2.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

        // Search
        val searchTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
        BasicTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearch(it)
            },
            singleLine = true,
            textStyle = searchTextStyle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp)
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
                }
            }
        )

            // Tabs: 收藏 / 历史记录
            var selectedTab by remember { mutableStateOf(0) }
            val favRecords = filteredRecords.filter { (it["isFavorite"] as? Boolean) == true }
                .sortedWith(compareBy<Map<String, Any>> { (it["favoriteOrder"] as? Int) ?: 0 }.thenByDescending { (it["favoriteTime"] as? Long) ?: 0L })

            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.joystick_history_favorites), fontSize = 12.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.joystick_history_normal), fontSize = 12.sp) })
            }

            if (selectedTab == 0) {
                var draggedId by remember { mutableStateOf<String?>(null) }
                var dragOffset by remember { mutableStateOf(0f) }
                val localFavList = remember { mutableStateListOf<Map<String, Any>>() }

                LaunchedEffect(favRecords) {
                    if (draggedId == null) {
                        localFavList.clear()
                        localFavList.addAll(favRecords)
                    }
                }

                if (localFavList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.history_idle), color = Color.Gray)
                    }
                } else {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .pointerInput(Unit) {
                                val cardHeightPx = 56.dp.toPx()
                                val gapPx = 4.dp.toPx()
                                val itemUnitPx = cardHeightPx + gapPx

                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val contentY = offset.y + scrollState.value
                                        val idx = (contentY / itemUnitPx).toInt().coerceIn(0, localFavList.lastIndex)
                                        localFavList.clear()
                                        localFavList.addAll(favRecords)
                                        draggedId = localFavList.getOrNull(idx)?.let { it[HistoryActivity.KEY_ID] as? String }
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (draggedId == null) return@detectDragGesturesAfterLongPress
                                        dragOffset += dragAmount.y
                                        val curIdx = localFavList.indexOfFirst { (it[HistoryActivity.KEY_ID] as? String) == draggedId }
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
                                            onSetFavoriteOrder(localFavList.mapNotNull { it[HistoryActivity.KEY_ID] as? String })
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
                    ) {
                        localFavList.forEachIndexed { _, record ->
                            val isDragged = draggedId == (record[HistoryActivity.KEY_ID] as? String)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragged) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragged) dragOffset else 0f
                                        shadowElevation = if (isDragged) 16f else 0f
                                    }
                            ) {
                                historyListItem(
                                    record = record,
                                    isFav = true,
                                    showMoveButtons = false,
                                    onSelectRecord = onSelectRecord,
                                    onToggleFavorite = onToggleFavorite,
                                    onRename = { id -> renameTarget = id; renameText = (record[HistoryActivity.KEY_LOCATION] as? String) ?: "" },
                                    onDelete = onDelete,
                                    scaleFactor = scaleFactor
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }
            } else {
                // History tab
                if (filteredRecords.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.history_idle), color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(filteredRecords.sortedByDescending { (it["rawTimestamp"] as? Long) ?: 0L }, key = { "all_${it[HistoryActivity.KEY_ID]}" }) { record ->
                            historyListItem(record = record, isFav = (record["isFavorite"] as? Boolean) == true, showMoveButtons = false, onSelectRecord = onSelectRecord, onToggleFavorite = onToggleFavorite, onRename = { id -> renameTarget = id; renameText = (record[HistoryActivity.KEY_LOCATION] as? String) ?: "" }, onDelete = onDelete, scaleFactor = scaleFactor)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        if (renameTarget != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { renameTarget = null },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(min = 240.dp, max = 300.dp)
                        .clickable { },
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.location_rename_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { renameTarget = null }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { onRename(renameTarget!!, renameText); renameTarget = null }) {
                                Text(stringResource(R.string.common_ok))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun historyListItem(
    record: Map<String, Any>,
    isFav: Boolean,
    showMoveButtons: Boolean = false,
    onSelectRecord: (Map<String, Any>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    scaleFactor: Float = 1f
) {
    val id = (record[HistoryActivity.KEY_ID] as? String) ?: ""
    val iconSize = (24 * scaleFactor).dp
    val btnSize = (40 * scaleFactor).dp
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelectRecord(record) }.padding(start = 4.dp * scaleFactor, end = 4.dp * scaleFactor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showMoveButtons) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 6.dp * scaleFactor)) {
                Text("▲", modifier = Modifier.clickable(onClick = onMoveUp).padding((2 * scaleFactor).dp), fontSize = (10 * scaleFactor).sp, color = Color.Gray)
                Text("▼", modifier = Modifier.clickable(onClick = onMoveDown).padding((2 * scaleFactor).dp), fontSize = (10 * scaleFactor).sp, color = Color.Gray)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            HistoryItem(record = record, onClick = { onSelectRecord(record) }, scaleFactor = scaleFactor)
        }
        IconButton(onClick = { onToggleFavorite(id) }, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.Star, contentDescription = "Favorite", tint = if (isFav) Color(0xFFFFB300) else Color.Gray, modifier = Modifier.size(iconSize).graphicsLayer(alpha = if (isFav) 1f else 0.4f))
        }
        IconButton(onClick = { onRename(id) }, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
        }
        val ctx = LocalContext.current
        val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
        val showDeleteConfirm = remember { mutableStateOf(false) }
        var dontRemind by remember { mutableStateOf(false) }
        IconButton(onClick = {
            if (System.currentTimeMillis() < prefs.getLong("delete_dont_remind_until", 0L)) {
                onDelete(id)
            } else {
                showDeleteConfirm.value = true
                dontRemind = false
            }
        }, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(iconSize))
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
                        showDeleteConfirm.value = false; onDelete(id)
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

@Composable
fun HistoryItem(
    record: Map<String, Any>,
    onClick: () -> Unit,
    scaleFactor: Float = 1f
) {
    val name = (record[HistoryActivity.KEY_LOCATION] as? String) 
            ?: (record[LocationPickerViewModel.POI_NAME] as? String) 
            ?: "Unknown"
            
    val address = (record[HistoryActivity.KEY_TIME] as? String) 
        ?: (record[LocationPickerViewModel.POI_ADDRESS] as? String) 
        ?: ""
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding((8 * scaleFactor).dp)
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        if (address.isNotEmpty()) {
            Text(text = address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

/**
 * 地图浮窗的组合函数。
 * 在悬浮窗中显示地图与搜索/传送等控制。
 */
@Composable
fun JoyStickMapOverlay(
    mapView: MapView,
    currentLocation: LatLng = LatLng(0.0, 0.0),
    onClose: () -> Unit,
    onWindowDrag: (Float, Float) -> Unit,
    onGo: () -> Unit,
    onBackToCurrent: () -> Unit,
    onSearch: (String) -> Unit,
    searchResults: List<Map<String, Any>>?,
    onSelectSearchResult: (Map<String, Any>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    val winW = remember {
        (prefs.getString(SettingsViewModel.KEY_FLOATING_WINDOW_WIDTH, "300") ?: "300").toIntOrNull() ?: 300
    }.dp
    val winH = remember {
        (prefs.getString(SettingsViewModel.KEY_FLOATING_WINDOW_HEIGHT, "500") ?: "500").toIntOrNull() ?: 500
    }.dp

    fun initMapLocation(loc: LatLng) {
        try {
            mapView.map.clear()
            mapView.map.addOverlay(
                MarkerOptions().position(loc).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_position))
            )
            mapView.map.setMyLocationData(MyLocationData.Builder().latitude(loc.latitude).longitude(loc.longitude).build())
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        if (currentLocation.latitude != 0.0 || currentLocation.longitude != 0.0) {
            mapView.map.setMapStatus(MapStatusUpdateFactory.newLatLng(currentLocation))
            initMapLocation(currentLocation)
        }
    }

    Column(
        modifier = Modifier
            .width(winW)
            .height(winH)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
    ) {
        // Header (draggable area)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onWindowDrag(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Text(
                text = stringResource(R.string.joystick_map_tips),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Search
        val searchTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
        BasicTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearch(it)
                showSearchResults = it.isNotEmpty()
            },
            singleLine = true,
            textStyle = searchTextStyle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp)
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
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // Map
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = onBackToCurrent,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_home_position), contentDescription = "Back to Current", modifier = Modifier.size(24.dp))
                }
                
                FloatingActionButton(
                    onClick = onGo,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_position), contentDescription = "Go", modifier = Modifier.size(28.dp))
                }
            }
            
            // Search Results Overlay
            if (showSearchResults && !searchResults.isNullOrEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .heightIn(max = 250.dp)
                        .align(Alignment.TopCenter),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    LazyColumn {
                        items(searchResults) { item ->
                            HistoryItem(record = item, onClick = {
                                onSelectSearchResult(item)
                                showSearchResults = false
                                searchQuery = ""
                            })
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}
