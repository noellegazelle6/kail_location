package com.kail.location.views.wifisimulation

import com.kail.location.utils.GoUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.wifi.ScanResult
import com.kail.location.R
import com.kail.location.models.WifiInfo
import com.kail.location.viewmodels.WifiSimulationViewModel
import com.kail.location.views.common.AppDrawer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSimulationScreen(
    viewModel: WifiSimulationViewModel,
    onNavigate: (Int) -> Unit,
    appVersion: String,
    onAddClick: () -> Unit
) {
    val isSimulating by viewModel.isSimulating.collectAsState()
    val wifiList by viewModel.wifiList.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val activeList = wifiList.filter { it.id in selectedIds }
    val isScanning by viewModel.isScanning.collectAsState()
    val nearbyResults by viewModel.nearbyResults.collectAsState()

    var renameTarget by remember { mutableStateOf<WifiInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showScanDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Reflect & persist the shared run mode so the side menu tracks the current
    // mode instead of being hardcoded to "root".
    val context = androidx.compose.ui.platform.LocalContext.current
    val appPrefs = remember {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }
    var runMode by remember {
        mutableStateOf(appPrefs.getString("setting_run_mode", "developer") ?: "developer")
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "WifiSimulation",
                onNavigate = onNavigate,
                appVersion = appVersion,
                runMode = runMode,
                onRunModeChange = { mode ->
                    runMode = mode
                    appPrefs.edit().putString("setting_run_mode", mode).apply()
                },
                onDeveloperModeSelected = {
                    if (GoUtils.isAllowMockLocation(context)) {
                        runMode = "developer"
                        appPrefs.edit().putString("setting_run_mode", "developer").apply()
                    } else {
                        GoUtils.openMockLocationSettings(context)
                    }
                },
                scope = scope
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.wifi_sim_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                        navigationIconContentColor = Color.White
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.wifi_sim_add))
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            // Target WiFi Card (like RouteCard in RouteSimulationScreen)
            if (activeList.isNotEmpty()) {
                WifiTargetCard(
                    activeList = activeList,
                    isSimulating = isSimulating,
                    onStartSimulating = {
                        viewModel.setSimulating(true)
                    },
                    onStopSimulating = { viewModel.setSimulating(false) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            } else {
                // Empty state card with scan button
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.wifi_sim_empty_text),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.wifi_sim_scan_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.scanNearbyWifi()
                                showScanDialog = true
                            },
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isScanning) stringResource(R.string.wifi_sim_scanning) else stringResource(R.string.wifi_sim_scan_nearby),
                                fontSize = 14.sp
                            )
                        }

                    }
                }
            }

            // Scan & History controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        viewModel.scanNearbyWifi()
                        showScanDialog = true
                    },
                    enabled = !isSimulating
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.wifi_sim_scan_nearby), fontSize = 12.sp)
                }
                if (wifiList.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            if (selectedIds.size == wifiList.size) viewModel.deselectAll()
                            else viewModel.selectAll()
                        },
                        enabled = !isSimulating
                    ) {
                        Text(
                            if (selectedIds.size == wifiList.size) stringResource(R.string.wifi_sim_deselect_all) else stringResource(R.string.wifi_sim_select_all),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            if (wifiList.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.wifi_sim_history),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp, bottom = 4.dp)
                )
            }

            if (wifiList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_wifi),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.wifi_sim_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(wifiList) { wifi ->
                        val isSelected = wifi.id in selectedIds
                        WifiHistoryCard(
                            wifi = wifi,
                            isSelected = isSelected,
                            isSimulating = isSimulating,
                            onToggleSelect = { viewModel.toggleSelection(wifi.id) },
                            onRename = {
                                renameTarget = wifi
                                renameText = wifi.name.ifBlank { wifi.ssid }
                            },
                            onDelete = { viewModel.deleteWifi(wifi.id) }
                        )
                    }
                }
            }
        }
    }
}

    // Rename Dialog
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.wifi_sim_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.wifi_sim_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameWifi(renameTarget!!.id, renameText)
                        renameTarget = null
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Scan result dialog
    if (showScanDialog) {
        WifiScanResultDialog(
            nearbyResults = nearbyResults,
            isScanning = isScanning,
            onDismiss = { showScanDialog = false },
            onAdd = { result ->
                viewModel.addFromScanResult(result)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScanResultDialog(
    nearbyResults: List<ScanResult>,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onAdd: (ScanResult) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wifi_sim_scan_result_title)) },
        text = {
            if (isScanning) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.wifi_sim_scanning))
                    }
                }
            } else if (nearbyResults.isEmpty()) {
                Text(
                    text = stringResource(R.string.wifi_sim_scan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.wifi_sim_scan_count_fmt, nearbyResults.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    nearbyResults.forEach { result ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.SSID.ifBlank { "Hidden Network" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${result.BSSID} · ${result.level}dBm · ${result.frequency}MHz",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                TextButton(
                                    onClick = { onAdd(result) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.wifi_sim_add_label), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        }
    )
}

@Composable
fun WifiTargetCard(
    activeList: List<WifiInfo>,
    isSimulating: Boolean,
    onStartSimulating: () -> Unit,
    onStopSimulating: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSimulating) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSimulating) stringResource(R.string.wifi_sim_running_title) else stringResource(R.string.wifi_sim_title_short),
                    color = if (isSimulating) MaterialTheme.colorScheme.primary else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // WiFi list preview
            activeList.take(3).forEach { wifi ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = wifi.name.ifBlank { wifi.ssid },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            if (activeList.size > 3) {
                Text(
                    text = stringResource(R.string.cell_sim_more_fmt, activeList.size - 3),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start/Stop buttons (like RouteCard)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSimulating) {
                    Button(
                        onClick = onStartSimulating,
                        enabled = activeList.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.wifi_sim_start), fontSize = 14.sp)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onStopSimulating,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.wifi_sim_stop), fontSize = 14.sp)
                        }
                    }
                }

                if (isSimulating) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wifi_sim_status_running),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WifiHistoryCard(
    wifi: WifiInfo,
    isSelected: Boolean,
    isSimulating: Boolean,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                enabled = !isSimulating
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isSimulating) { onToggleSelect() }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_wifi),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = wifi.name.ifBlank { wifi.ssid },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                    Text(
                        text = "${wifi.ssid} · ${wifi.bssid}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            IconButton(onClick = onRename, enabled = !isSimulating) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.wifi_sim_rename),
                    tint = if (isSimulating) Color.Gray else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, enabled = !isSimulating) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.wifi_sim_delete),
                    tint = if (isSimulating) Color.Gray else Color.Red,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

fun Modifier.scale(scale: Float): Modifier = this.then(
    graphicsLayer(scaleX = scale, scaleY = scale)
)
