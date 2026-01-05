package com.kail.location.views.joystick

import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.MapView
import com.kail.location.R
import com.kail.location.views.history.HistoryActivity
import com.kail.location.views.main.MainActivity
import com.kail.location.viewmodels.MainViewModel

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
    onSearch: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .width(300.dp)
            .height(500.dp)
            .background(Color.White, RoundedCornerShape(8.dp)) // Assuming border_window is white with corners
            // Add border if needed
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) // Header bg
        ) {
            Text(
                text = stringResource(R.string.joystick_history_tips),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                onSearch(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text(stringResource(R.string.app_search_tips)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        // List
        if (historyRecords.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_idle))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(historyRecords) { record ->
                    HistoryItem(record = record, onClick = { onSelectRecord(record) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * 单条历史记录项的组合函数。
 *
 * @param record 历史记录数据。
 * @param onClick 点击项时的回调。
 */
@Composable
fun HistoryItem(
    record: Map<String, Any>,
    onClick: () -> Unit
) {
    // Determine keys based on available data
    val name = (record[HistoryActivity.KEY_LOCATION] as? String) 
            ?: (record[MainViewModel.POI_NAME] as? String) 
            ?: "Unknown"
            
        val address = (record[HistoryActivity.KEY_TIME] as? String) 
            ?: (record[MainViewModel.POI_ADDRESS] as? String) 
            ?: ""
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
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
 *
 * @param mapView 要展示的 MapView 实例。
 * @param onClose 点击关闭按钮的回调。
 * @param onWindowDrag 悬浮窗拖动回调（dx, dy）。
 * @param onGo 点击“GO”时的回调。
 * @param onBackToCurrent 返回当前位置的回调。
 * @param onSearch 搜索关键字变化时的回调。
 * @param searchResults 搜索结果列表。
 * @param onSelectSearchResult 选中搜索结果时的回调。
 */
@Composable
fun JoyStickMapOverlay(
    mapView: MapView, // Pass initialized MapView
    onClose: () -> Unit,
    onWindowDrag: (Float, Float) -> Unit,
    onGo: () -> Unit,
    onBackToCurrent: () -> Unit,
    onSearch: (String) -> Unit, // Implement search logic
    searchResults: List<Map<String, Any>>?,
    onSelectSearchResult: (Map<String, Any>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(300.dp)
            .height(500.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Text(
                text = stringResource(R.string.joystick_map_tips),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearch(it)
                showSearchResults = it.isNotEmpty()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text(stringResource(R.string.app_search_tips)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
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
                    Icon(painterResource(R.drawable.ic_home_position), contentDescription = "Back to Current")
                }
                
                FloatingActionButton(
                    onClick = onGo,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(painterResource(R.drawable.ic_position), contentDescription = "Go")
                }
            }
            
            // Search Results Overlay
            if (showSearchResults && !searchResults.isNullOrEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(Color.White)
                        .align(Alignment.TopCenter)
                ) {
                    items(searchResults) { item ->
                        HistoryItem(record = item, onClick = {
                            onSelectSearchResult(item)
                            showSearchResults = false
                            searchQuery = "" // Clear search?
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
