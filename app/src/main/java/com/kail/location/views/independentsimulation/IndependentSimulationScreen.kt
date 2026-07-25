package com.kail.location.views.independentsimulation

import com.kail.location.utils.GoUtils
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kail.location.R
import com.kail.location.viewmodels.IndependentSimulationViewModel
import com.kail.location.views.common.AppDrawer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndependentSimulationScreen(
    viewModel: IndependentSimulationViewModel,
    onBackClick: () -> Unit,
    onNavigate: (Int) -> Unit = {},
    appVersion: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Reflect & persist the shared run mode
    val appPrefs = remember {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }
    var runMode by remember {
        mutableStateOf(appPrefs.getString("setting_run_mode", "developer") ?: "developer")
    }

    val isEnabled by viewModel.isEnabled.collectAsState()
    val targetPackages by viewModel.targetPackages.collectAsState()

    var showAppPicker by remember { mutableStateOf(false) }
    var selectedPackages by remember {
        mutableStateOf(targetPackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "IndependentSimulation",
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
                    title = { Text(stringResource(R.string.ind_sim_title)) },
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
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnabled) stringResource(R.string.ind_sim_running) else stringResource(R.string.ind_sim_stopped),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.ind_sim_description),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Target Apps
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showAppPicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ind_sim_target_apps),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (selectedPackages.isEmpty())
                                stringResource(R.string.ind_sim_target_apps_hint)
                            else
                                stringResource(R.string.ind_sim_selected_count, selectedPackages.size),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (selectedPackages.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start/Stop Button
            Button(
                onClick = {
                    if (!isEnabled && selectedPackages.isEmpty()) return@Button
                    viewModel.setEnabled(!isEnabled)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                enabled = isEnabled || selectedPackages.isNotEmpty()
            ) {
                Text(
                    text = if (isEnabled) stringResource(R.string.ind_sim_stop_btn) else stringResource(R.string.ind_sim_start_btn)
                )
            }

            if (!isEnabled && selectedPackages.isEmpty()) {
                Text(
                    text = stringResource(R.string.ind_sim_no_apps),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (showAppPicker) {
                AppPickerDialog(
                    selectedPackages = selectedPackages,
                    onPackagesChanged = {
                        selectedPackages = it
                        viewModel.setTargetPackages(it.joinToString(","))
                    },
                    onDismiss = { showAppPicker = false }
                )
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    selectedPackages: Set<String>,
    onPackagesChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var filteredApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var currentSelected by remember { mutableStateOf(selectedPackages) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        // Load all installed apps including system apps that can be launched
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                // Include non-system apps
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) return@filter true
                // Include updated system apps
                if ((appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return@filter true
                // Include some important system apps that user might want to mock
                val pkg = appInfo.packageName
                pkg.startsWith("com.tencent.") ||
                pkg.startsWith("com.alibaba.") ||
                pkg.startsWith("com.baidu.") ||
                pkg.startsWith("com.autonavi.") ||
                pkg.startsWith("com.amap.") ||
                pkg.startsWith("com.sankuai.") ||
                pkg.startsWith("com.dianping.") ||
                pkg.startsWith("com.sina.") ||
                pkg.startsWith("com.netease.") ||
                pkg.startsWith("com.xiaomi.") ||
                pkg.startsWith("com.huawei.") ||
                pkg.startsWith("com.oppo.") ||
                pkg.startsWith("com.vivo.") ||
                pkg.startsWith("com.samsung.") ||
                pkg == "com.android.chrome" ||
                pkg == "com.google.android.apps.maps"
            }
            .sortedBy { pm.getApplicationLabel(it).toString() }
        apps = installed.map {
            AppInfo(
                packageName = it.packageName,
                appName = pm.getApplicationLabel(it).toString()
            )
        }
        filteredApps = apps
        isLoading = false
    }

    LaunchedEffect(searchQuery) {
        filteredApps = if (searchQuery.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.ind_sim_select_apps),
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.ind_sim_search_apps)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true
                )

                Text(
                    text = stringResource(R.string.ind_sim_selected_count, currentSelected.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredApps) { app ->
                            val isSelected = currentSelected.contains(app.packageName)
                            ListItem(
                                headlineContent = {
                                    Text(
                                        app.appName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        app.packageName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            currentSelected = if (isSelected) {
                                                currentSelected - app.packageName
                                            } else {
                                                currentSelected + app.packageName
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    currentSelected = if (isSelected) {
                                        currentSelected - app.packageName
                                    } else {
                                        currentSelected + app.packageName
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        onClick = {
                            onPackagesChanged(currentSelected)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            }
        }
    }
}

data class AppInfo(val packageName: String, val appName: String)
