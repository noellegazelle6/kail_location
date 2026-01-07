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
    viewModel: LocationSimulationViewModel,
    onNavigate: (Int) -> Unit,
    onAddLocation: () -> Unit,
    appVersion: String
) {
    val context = LocalContext.current
    val locationInfo =
        viewModel.locationInfo.collectAsState(initial = LocationSimulationViewModel.LocationInfo()).value
    val isSimulating = viewModel.isSimulating.collectAsState(initial = false).value
    val isJoystickEnabled = viewModel.isJoystickEnabled.collectAsState(initial = false).value
    val updateInfo by viewModel.updateInfo.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()
    var renameTarget by remember { mutableStateOf<HistoryRecord?>(null) }
    var renameText by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    if (updateInfo != null) {
        UpdateDialog(
            info = updateInfo!!,
            onDismiss = { viewModel.dismissUpdateDialog() },
            onConfirm = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.downloadUrl))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                viewModel.dismissUpdateDialog()
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
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_position),
                            contentDescription = null
                        )
                    },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } }
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
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_settings),
                            contentDescription = null
                        )
                    },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_settings) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_dev)) },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_dev),
                            contentDescription = null
                        )
                    },
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
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_upgrade),
                            contentDescription = null
                        )
                    },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); viewModel.checkUpdate(context) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_feedback)) },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_feedback),
                            contentDescription = null
                        )
                    },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_feedback) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_contact)) },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_contact),
                            contentDescription = null
                        )
                    },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_contact) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_sponsor)) },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_user),
                            contentDescription = null
                        )
                    },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_sponsor) } }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_github)) },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_dev),
                            contentDescription = null
                        )
                    },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onNavigate(R.id.nav_source_code) } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.loc_sim_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                    .background(Color(0xFFF5F5F5)) // Light gray background
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
                            .padding(top = 16.dp), // Space for the + button overlap
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                                    onClick = { viewModel.toggleSimulation() },
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
                                    onCheckedChange = { viewModel.setJoystickEnabled(it) },
                                    modifier = Modifier.scale(0.8f)
                                )
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
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectRecord(record) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = record.name, fontSize = 16.sp, color = Color.Black)
                                        Text(text = record.displayTime, fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Row {
                                        IconButton(onClick = { renameTarget = record; renameText = record.name }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { viewModel.deleteRecord(record.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
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
                TextButton(onClick = { viewModel.renameRecord(renameTarget!!.id, renameText); renameTarget = null }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
}
