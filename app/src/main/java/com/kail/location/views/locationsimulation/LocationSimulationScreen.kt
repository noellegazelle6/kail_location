package com.kail.location.views.locationsimulation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
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
import com.kail.location.views.common.DrawerHeader
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.kail.location.views.common.UpdateDialog
import com.kail.location.models.HistoryRecord
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

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
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onNavigate: (Int) -> Unit,
    onAddLocation: () -> Unit,
    appVersion: String,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<HistoryRecord?>(null) }
    var renameText by remember { mutableStateOf("") }


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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSimulating) Color.Red else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text(
                                        if (isSimulating) stringResource(R.string.loc_sim_stop) else stringResource(
                                            R.string.loc_sim_start
                                        )
                                    )
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
                                // Placeholder icon
                                Switch(
                                    checked = isJoystickEnabled,
                                    onCheckedChange = onJoystickToggle,
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

                            if (runMode == "root") {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.route_sim_step_text), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Switch(
                                        checked = stepSimulationEnabled,
                                        onCheckedChange = onStepSimulationToggle,
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
                                        Text("${stepCadenceSpm.toInt()} 步/分钟 · 约 ${((kmh * 10).toInt() / 10f)} km/h", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
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

                // History List Header
                PaddingValues(horizontal = 16.dp, vertical = 8.dp).let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(it)
                    ) {
                        Text(
                            text = stringResource(R.string.loc_sim_history_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = "Search",
                            tint = Color.Gray
                        )
                    }
                }

                if (historyRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_history),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.loc_sim_add_tips),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(historyRecords) { record ->
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = record.name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(text = record.displayTime, fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Row {
                                        IconButton(onClick = { renameTarget = record; renameText = record.name }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onRecordDelete(record.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            com.kail.location.ads.NativeAdCard()
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
            title = { Text("重命名位置") },
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
}
