package com.kail.location.views.sandbox

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kail.location.R
import com.kail.location.sandbox.SandboxManager
import com.kail.location.sandbox.SandboxSettingsManager
import com.kail.location.viewmodels.SandboxViewModel
import com.kail.location.views.common.AppDrawer
import kotlinx.coroutines.launch

/**
 * 沙盒模式主界面。
 * 展示已克隆到沙盒中的应用列表，支持启动、卸载、清除数据等操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    sandboxApps: List<SandboxManager.SandboxAppInfo>,
    systemApps: List<SandboxManager.SystemAppInfo>,
    isLoading: Boolean,
    toastMessage: String?,
    onClearToast: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onClearAppData: (String) -> Unit,
    onStopApp: (String) -> Unit,
    onCloneApp: (String) -> Unit,
    onLoadSystemApps: () -> Unit,
    onCreateShortcut: (String, String, Drawable?) -> Unit,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onDeveloperModeSelected: () -> Unit = {},
    onXposedSettingsSelected: () -> Unit = {},
    onNavigate: (Int) -> Unit,
    appVersion: String
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAppMenu by remember { mutableStateOf<SandboxManager.SandboxAppInfo?>(null) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            onClearToast()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "Sandbox",
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
                    title = { Text(stringResource(R.string.drawer_sandbox)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showInstallDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Install",
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
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showInstallDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                if (isLoading && sandboxApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (sandboxApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu_dev),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.sandbox_empty),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.sandbox_empty_hint),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(sandboxApps) { app ->
                            SandboxAppItem(
                                app = app,
                                onClick = { onLaunchApp(app.packageName) },
                                onLongClick = { showAppMenu = app },
                                onCreateShortcut = { onCreateShortcut(app.packageName, app.name, app.icon) },
                                onClearData = { onClearAppData(app.packageName) },
                                onDeleteApp = { onUninstallApp(app.packageName) }
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.app_statement),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    showAppMenu?.let { app ->
        AlertDialog(
            onDismissRequest = { showAppMenu = null },
            title = { Text(app.name) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLaunchApp(app.packageName)
                                showAppMenu = null
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.sandbox_launch_app))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onClearAppData(app.packageName)
                                showAppMenu = null
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.sandbox_clear_data))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStopApp(app.packageName)
                                showAppMenu = null
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.sandbox_stop_running))
                    }
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUninstallApp(app.packageName)
                                showAppMenu = null
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.sandbox_uninstall_app), color = Color.Red)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppMenu = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showInstallDialog) {
        LaunchedEffect(Unit) {
            if (systemApps.isEmpty()) {
                onLoadSystemApps()
            }
        }
        InstallDialog(
            systemApps = systemApps,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onCloneApp = { packageName ->
                onCloneApp(packageName)
                showInstallDialog = false
                searchQuery = ""
            },
            onDismiss = {
                showInstallDialog = false
                searchQuery = ""
            },
            onLoadApps = onLoadSystemApps,
            isLoading = isLoading
        )
    }

    if (showSettings) {
        SandboxSettingsDialog(
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun SandboxAppItem(
    app: SandboxManager.SandboxAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCreateShortcut: () -> Unit,
    onClearData: () -> Unit,
    onDeleteApp: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(icon = app.icon, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCreateShortcut,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.sandbox_create_shortcut), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onClearData,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.sandbox_clear_data_btn), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onDeleteApp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Red
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.sandbox_uninstall_btn), fontSize = 12.sp, color = Color.Red)
            }
        }
    }
}

@Composable
fun AppIcon(icon: Drawable?, modifier: Modifier = Modifier) {
    if (icon != null) {
        val painter = remember(icon) {
            try {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    icon.intrinsicWidth.coerceAtLeast(1),
                    icon.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                BitmapPainter(bitmap.asImageBitmap())
            } catch (e: Exception) {
                null
            }
        }
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = modifier
            )
        } else {
            DefaultAppIcon(modifier)
        }
    } else {
        DefaultAppIcon(modifier)
    }
}

@Composable
fun DefaultAppIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.LightGray, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_menu_dev),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun InstallDialog(
    systemApps: List<SandboxManager.SystemAppInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloneApp: (String) -> Unit,
    onDismiss: () -> Unit,
    onLoadApps: () -> Unit,
    isLoading: Boolean = false
) {
    val filteredApps = if (searchQuery.isEmpty()) {
        systemApps
    } else {
        systemApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sandbox_clone_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.sandbox_search_placeholder)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoading && systemApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (systemApps.isEmpty()) {
                    TextButton(onClick = onLoadApps, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sandbox_load_system_apps))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps) { app ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCloneApp(app.packageName) }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppIcon(icon = app.icon, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = app.name,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun SandboxSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hideRoot by remember { mutableStateOf(false) }
    var daemonEnable by remember { mutableStateOf(false) }
    var useVpnNetwork by remember { mutableStateOf(false) }
    var disableFlagSecure by remember { mutableStateOf(false) }
    var isSupportGms by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isSendingLogs by remember { mutableStateOf(false) }
    var stepSimEnabled by remember { mutableStateOf(false) }
    var stepSimSpm by remember { mutableStateOf(120f) }
    var showStepSimDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            hideRoot = SandboxSettingsManager.hideRoot
            daemonEnable = SandboxSettingsManager.daemonEnable
            useVpnNetwork = SandboxSettingsManager.useVpnNetwork
            disableFlagSecure = SandboxSettingsManager.disableFlagSecure
            isSupportGms = SandboxSettingsManager.isSupportGms
        } catch (e: Exception) {
            android.util.Log.e("SandboxSettings", "Error loading settings: ${e.message}")
        }
    }

    LaunchedEffect(stepSimEnabled, stepSimSpm) {
        val intent = android.content.Intent(context, com.kail.location.service.Sandbox.ServiceGoSandbox::class.java).apply {
            putExtra(com.kail.location.service.Sandbox.ServiceGoSandbox.EXTRA_CONTROL_ACTION, com.kail.location.service.Sandbox.ServiceGoSandbox.CONTROL_SET_STEP)
            putExtra(com.kail.location.service.Sandbox.ServiceGoSandbox.EXTRA_STEP_ENABLED, stepSimEnabled)
            putExtra(com.kail.location.service.Sandbox.ServiceGoSandbox.EXTRA_STEP_FREQ, stepSimSpm)
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("SandboxSettings", "Failed to send step sim config: ${e.message}")
        }
    }

    toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sandbox_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.sandbox_hide_root),
                    description = stringResource(R.string.sandbox_hide_root_desc),
                    checked = hideRoot,
                    onCheckedChange = {
                        hideRoot = it
                        SandboxSettingsManager.hideRoot = it
                        toastMessage = context.getString(R.string.sandbox_restart_to_apply)
                    }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.sandbox_daemon),
                    description = stringResource(R.string.sandbox_daemon_desc),
                    checked = daemonEnable,
                    onCheckedChange = {
                        daemonEnable = it
                        SandboxSettingsManager.daemonEnable = it
                        toastMessage = context.getString(R.string.sandbox_restart_to_apply)
                    }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.sandbox_vpn),
                    description = stringResource(R.string.sandbox_vpn_desc),
                    checked = useVpnNetwork,
                    onCheckedChange = {
                        useVpnNetwork = it
                        SandboxSettingsManager.useVpnNetwork = it
                        toastMessage = context.getString(R.string.sandbox_restart_to_apply)
                    }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.sandbox_disable_secure),
                    description = stringResource(R.string.sandbox_disable_secure_desc),
                    checked = disableFlagSecure,
                    onCheckedChange = {
                        disableFlagSecure = it
                        SandboxSettingsManager.disableFlagSecure = it
                        toastMessage = context.getString(R.string.sandbox_restart_to_apply)
                    }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    title = stringResource(R.string.sandbox_step_sim),
                    description = stringResource(R.string.sandbox_step_sim_desc),
                    checked = stepSimEnabled,
                    onCheckedChange = { stepSimEnabled = it }
                )
                if (stepSimEnabled) {
                    SettingsActionRow(
                        title = stringResource(R.string.sandbox_step_fmt, stepSimSpm.toInt()),
                        description = stringResource(R.string.sandbox_step_adjust),
                        onClick = { showStepSimDialog = true }
                    )
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                if (isSupportGms) {
                    SettingsActionRow(
                        title = stringResource(R.string.sandbox_gms),
                        description = stringResource(R.string.sandbox_gms_desc),
                        onClick = {
                            toastMessage = context.getString(R.string.sandbox_gms_coming)
                        }
                    )
                } else {
                    SettingsActionRow(
                        title = stringResource(R.string.sandbox_gms),
                        description = stringResource(R.string.sandbox_gms_unsupported),
                        enabled = false,
                        onClick = {}
                    )
                }
                SettingsActionRow(
                    title = stringResource(R.string.sandbox_send_log),
                    description = stringResource(R.string.sandbox_send_log_desc),
                    onClick = {
                        if (!isSendingLogs) {
                            isSendingLogs = true
                            try {
                                SandboxSettingsManager.sendLogs(
                                    object : top.niunaijun.blackbox.BlackBoxCore.LogSendListener {
                                        override fun onSuccess() {
                                            toastMessage = context.getString(R.string.sandbox_log_sent)
                                            isSendingLogs = false
                                        }
                                        override fun onFailure(error: String?) {
                                            toastMessage = context.getString(R.string.sandbox_log_failed, error ?: "")
                                            isSendingLogs = false
                                        }
                                    }
                                )
                                toastMessage = context.getString(R.string.sandbox_log_sending)
                            } catch (e: Exception) {
                                toastMessage = context.getString(R.string.sandbox_log_failed, e.message ?: "")
                                isSendingLogs = false
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        }
    )

    if (showStepSimDialog) {
        var tempSpm by remember { mutableStateOf(stepSimSpm) }
        AlertDialog(
            onDismissRequest = { showStepSimDialog = false },
            title = { Text(stringResource(R.string.sandbox_adjust_cadence)) },
            text = {
                Column {
                    Text(stringResource(R.string.sandbox_cadence_fmt, tempSpm.toInt()), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = tempSpm,
                        onValueChange = { tempSpm = it },
                        valueRange = 30f..240f,
                        steps = 20,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text(stringResource(R.string.sandbox_cadence_range), fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    stepSimSpm = tempSpm
                    showStepSimDialog = false
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStepSimDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(text = description, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color.Unspecified else Color.Gray
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = if (enabled) Color.Gray else Color.LightGray
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.rotate(270f)
        )
    }
}
