package com.kail.location.views.cellsimulation

import com.kail.location.utils.GoUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kail.location.R
import com.kail.location.models.CellInfo
import com.kail.location.viewmodels.CellSimulationViewModel
import com.kail.location.views.common.AppDrawer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellSimulationScreen(
    viewModel: CellSimulationViewModel,
    onNavigate: (Int) -> Unit,
    appVersion: String
) {
    val cellList by viewModel.cellList.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSimulating by viewModel.isSimulating.collectAsState()
    val activeList = cellList.filter { it.id in selectedIds }
    val isScanning by viewModel.isScanningCell.collectAsState()
    val scannedCells by viewModel.scannedCells.collectAsState()
    val isFetchingNetwork by viewModel.isFetchingNetwork.collectAsState()
    val networkError by viewModel.networkFetchError.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCell by remember { mutableStateOf<CellInfo?>(null) }
    var showScanResultDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Reflect & persist the shared run mode so the side menu tracks the current
    // mode instead of being hardcoded to "root".
    val cellContext = androidx.compose.ui.platform.LocalContext.current
    val appPrefs = remember {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(cellContext)
    }
    var runMode by remember {
        mutableStateOf(appPrefs.getString("setting_run_mode", "developer") ?: "developer")
    }

    LaunchedEffect(networkError) {
        networkError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNetworkFetchError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "CellSimulation",
                onNavigate = onNavigate,
                appVersion = appVersion,
                runMode = runMode,
                onRunModeChange = { mode ->
                    runMode = mode
                    appPrefs.edit().putString("setting_run_mode", mode).apply()
                },
                onDeveloperModeSelected = {
                    if (GoUtils.isAllowMockLocation(cellContext)) {
                        runMode = "developer"
                        appPrefs.edit().putString("setting_run_mode", "developer").apply()
                    } else {
                        GoUtils.openMockLocationSettings(cellContext)
                    }
                },
                scope = scope
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.cell_sim_title)) },
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
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cell_sim_add))
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            // Target Cell Card
            if (activeList.isNotEmpty()) {
                CellTargetCard(
                    activeList = activeList,
                    isSimulating = isSimulating,
                    onStartSimulating = { viewModel.setSimulating(true) },
                    onStopSimulating = { viewModel.setSimulating(false) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            } else {
                // Empty state card
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
                            text = stringResource(R.string.cell_sim_empty_text),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.cell_sim_scan_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Scan & controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.scanCurrentCellInfo()
                            showScanResultDialog = true
                        },
                        enabled = !isSimulating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.cell_sim_scan_current), fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = {
                            viewModel.fetchCellsFromNetwork()
                            showScanResultDialog = true
                        },
                        enabled = !isFetchingNetwork && !isSimulating
                    ) {
                        if (isFetchingNetwork) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.cell_sim_fetch_network), fontSize = 12.sp)
                    }
                }
                if (cellList.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            if (selectedIds.size == cellList.size) viewModel.deselectAll()
                            else viewModel.selectAll()
                        },
                        enabled = !isSimulating
                    ) {
                        Text(
                            if (selectedIds.size == cellList.size) stringResource(R.string.cell_sim_deselect_all) else stringResource(R.string.cell_sim_select_all),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            if (cellList.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.cell_sim_history),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp, bottom = 4.dp)
                )
            }

            if (cellList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cell_sim_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(cellList) { cell ->
                        val isSelected = cell.id in selectedIds
                        CellHistoryCard(
                            cell = cell,
                            isSelected = isSelected,
                            isSimulating = isSimulating,
                            onToggleSelect = { viewModel.toggleSelection(cell.id) },
                            onEdit = { editingCell = cell },
                            onDelete = { viewModel.deleteCell(cell.id) }
                        )
                    }
                }
            }
        }
    }
}

    if (showAddDialog) {
        CellEditDialog(
            cell = null,
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.addCell(it)
                showAddDialog = false
            }
        )
    }

    if (editingCell != null) {
        CellEditDialog(
            cell = editingCell,
            onDismiss = { editingCell = null },
            onSave = {
                viewModel.updateCell(it)
                editingCell = null
            }
        )
    }

    // Scan result dialog
    if (showScanResultDialog) {
        CellScanResultDialog(
            scannedCells = scannedCells,
            isScanning = isScanning || isFetchingNetwork,
            onDismiss = {
                showScanResultDialog = false
                viewModel.clearScannedCells()
            },
            onAddAll = {
                viewModel.addScannedCells()
                showScanResultDialog = false
            }
        )
    }
}

@Composable
fun CellTargetCard(
    activeList: List<CellInfo>,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSimulating) stringResource(R.string.cell_sim_running_title) else stringResource(R.string.cell_sim_title_short),
                    color = if (isSimulating) MaterialTheme.colorScheme.primary else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            activeList.take(3).forEach { cell ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${cell.networkType} - CID: ${cell.cid}",
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
                        Text(stringResource(R.string.cell_sim_start), fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = onStopSimulating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.cell_sim_stop), fontSize = 14.sp)
                    }
                }

                if (isSimulating) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cell_sim_status_running),
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
fun CellHistoryCard(
    cell: CellInfo,
    isSelected: Boolean,
    isSimulating: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
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
                Column {
                    Text(
                        text = "${cell.networkType} - ${cell.cid}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                    Text(
                        text = "MCC: ${cell.mcc} MNC: ${cell.mnc} LAC: ${cell.lac} PSC: ${cell.psc}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            IconButton(onClick = onEdit, enabled = !isSimulating) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.cell_sim_edit),
                    tint = if (isSimulating) Color.Gray else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, enabled = !isSimulating) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cell_sim_delete),
                    tint = if (isSimulating) Color.Gray else Color.Red,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellScanResultDialog(
    scannedCells: List<CellInfo>,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onAddAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cell_sim_scan_result_title)) },
        text = {
            if (isScanning) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.cell_sim_scanning))
                    }
                }
            } else if (scannedCells.isEmpty()) {
                Text(
                    text = stringResource(R.string.cell_sim_scan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.cell_sim_scan_count_fmt, scannedCells.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    scannedCells.forEach { cell ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${cell.networkType} - CID: ${cell.cid}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "MCC: ${cell.mcc}  MNC: ${cell.mnc}  LAC: ${cell.lac}  PSC: ${cell.psc}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = stringResource(R.string.cell_sim_location_fmt, String.format("%.6f, %.6f", cell.latitude, cell.longitude)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAddAll,
                enabled = !isScanning && scannedCells.isNotEmpty()
            ) {
                Text(stringResource(R.string.cell_sim_scan_add_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellEditDialog(
    cell: CellInfo?,
    onDismiss: () -> Unit,
    onSave: (CellInfo) -> Unit
) {
    val networkTypes = listOf("GSM", "CDMA", "LTE", "WCDMA")
    var networkType by remember { mutableStateOf(cell?.networkType ?: "LTE") }
    var mcc by remember { mutableStateOf(cell?.mcc?.toString() ?: "460") }
    var mnc by remember { mutableStateOf(cell?.mnc?.toString() ?: "0") }
    var lac by remember { mutableStateOf(cell?.lac?.toString() ?: "0") }
    var cid by remember { mutableStateOf(cell?.cid?.toString() ?: "0") }
    var psc by remember { mutableStateOf(cell?.psc?.toString() ?: "0") }
    var latitude by remember { mutableStateOf(cell?.latitude?.toString() ?: "0.0") }
    var longitude by remember { mutableStateOf(cell?.longitude?.toString() ?: "0.0") }
    var radius by remember { mutableStateOf(cell?.radius?.toString() ?: "1000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (cell == null) stringResource(R.string.cell_sim_add) else stringResource(R.string.cell_sim_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.cell_sim_type), style = MaterialTheme.typography.bodySmall)
                Row {
                    networkTypes.forEach { type ->
                        FilterChip(
                            selected = networkType == type,
                            onClick = { networkType = type },
                            label = { Text(type) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = mcc,
                    onValueChange = { mcc = it },
                    label = { Text(stringResource(R.string.cell_sim_mcc)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mnc,
                    onValueChange = { mnc = it },
                    label = { Text(stringResource(R.string.cell_sim_mnc)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lac,
                    onValueChange = { lac = it },
                    label = { Text(stringResource(R.string.cell_sim_lac)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cid,
                    onValueChange = { cid = it },
                    label = { Text(stringResource(R.string.cell_sim_cid)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = psc,
                    onValueChange = { psc = it },
                    label = { Text(stringResource(R.string.cell_sim_psc)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text(stringResource(R.string.cell_sim_lat)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text(stringResource(R.string.cell_sim_lon)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = radius,
                    onValueChange = { radius = it },
                    label = { Text(stringResource(R.string.cell_sim_radius)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val info = CellInfo(
                        id = cell?.id ?: "",
                        networkType = networkType,
                        mcc = mcc.toIntOrNull() ?: 460,
                        mnc = mnc.toIntOrNull() ?: 0,
                        lac = lac.toIntOrNull() ?: 0,
                        cid = cid.toLongOrNull() ?: 0,
                        psc = psc.toIntOrNull() ?: 0,
                        latitude = latitude.toDoubleOrNull() ?: 0.0,
                        longitude = longitude.toDoubleOrNull() ?: 0.0,
                        radius = radius.toFloatOrNull() ?: 1000f
                    )
                    onSave(info)
                }
            ) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
