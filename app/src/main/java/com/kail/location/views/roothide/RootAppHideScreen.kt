package com.kail.location.views.roothide

import com.kail.location.utils.GoUtils
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kail.location.R
import com.kail.location.viewmodels.RootAppHideViewModel
import com.kail.location.views.common.AppDrawer
import com.kail.location.views.independentsimulation.AppPickerDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootAppHideScreen(
    viewModel: RootAppHideViewModel,
    onBackClick: () -> Unit,
    onNavigate: (Int) -> Unit = {},
    appVersion: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val appPrefs = remember {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }
    var runMode by remember {
        mutableStateOf(appPrefs.getString("setting_run_mode", "developer") ?: "developer")
    }

    val isEnabled by viewModel.isEnabled.collectAsState()
    val hideRoot by viewModel.hideRoot.collectAsState()
    val hideAppList by viewModel.hideAppList.collectAsState()
    val targetPackages by viewModel.targetPackages.collectAsState()

    var showAppPicker by remember { mutableStateOf(false) }
    var selectedPackages by remember {
        mutableStateOf(targetPackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }

    val canStart = selectedPackages.isNotEmpty() && (hideRoot || hideAppList)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "RootAppHide",
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
                    title = { Text(stringResource(R.string.root_hide_title)) },
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
                        text = if (isEnabled)
                            stringResource(R.string.root_hide_status_running)
                        else
                            stringResource(R.string.root_hide_status_stopped),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.root_hide_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hide Root switch
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.root_hide_hide_root),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.root_hide_hide_root_summary),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = hideRoot,
                        onCheckedChange = { viewModel.setHideRoot(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hide app-list switch
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.root_hide_hide_applist),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.root_hide_hide_applist_summary),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = hideAppList,
                        enabled = hideRoot,
                        onCheckedChange = { viewModel.setHideAppList(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            text = stringResource(R.string.root_hide_target_apps),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (selectedPackages.isEmpty())
                                stringResource(R.string.root_hide_target_apps_hint)
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
                    if (!isEnabled && !canStart) return@Button
                    viewModel.setEnabled(!isEnabled)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                enabled = isEnabled || canStart
            ) {
                Text(
                    text = if (isEnabled)
                        stringResource(R.string.root_hide_stop)
                    else
                        stringResource(R.string.root_hide_start)
                )
            }

            if (!isEnabled && !canStart) {
                Text(
                    text = stringResource(R.string.root_hide_no_apps),
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
