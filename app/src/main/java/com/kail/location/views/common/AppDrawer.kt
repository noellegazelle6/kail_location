package com.kail.location.views.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kail.location.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    drawerState: DrawerState,
    currentScreen: String,
    onNavigate: (Int) -> Unit,
    appVersion: String,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onDeveloperModeSelected: () -> Unit = {},
    onXposedSettingsSelected: () -> Unit = {},
    scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()
) {
    var showRunModeDialog by remember { mutableStateOf(false) }
    var showEnvDialog by remember { mutableStateOf(false) }
    var showXposedDownloadDialog by remember { mutableStateOf(false) }
    var showLoginActivity by remember { mutableStateOf(false) }
    var showProfileActivity by remember { mutableStateOf(false) }
    var envMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (showLoginActivity) {
        val intent = Intent(context, com.kail.location.views.auth.LoginActivity::class.java)
        context.startActivity(intent)
        showLoginActivity = false
    }

    if (showProfileActivity) {
        val intent = Intent(context, com.kail.location.views.auth.ProfileActivity::class.java)
        context.startActivity(intent)
        showProfileActivity = false
    }

    fun isXposedModuleInstalled(): Boolean {
        return try {
            val pm = context.packageManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo("com.kail.locationxposed", android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo("com.kail.locationxposed", 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    var xposedDownloadProgress by remember { mutableStateOf(-1) }
    var xposedInstallUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(xposedInstallUri) {
        if (xposedInstallUri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(xposedInstallUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            xposedInstallUri = null
        }
    }

    fun downloadAndInstallXposed() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) { xposedDownloadProgress = 0 }
                val url = "https://github.com/noellegazelle6/kail_location/releases/download/v$appVersion/KailLocationXposed.apk"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body ?: return@launch
                val total = body.contentLength()
                val dir = File(context.getExternalFilesDir(null), "Updates")
                dir.mkdirs()
                val outFile = File(dir, "KailLocationXposed.apk")
                val input = body.byteStream()
                val output = FileOutputStream(outFile)
                val buffer = ByteArray(8192)
                var sum = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    sum += read
                    if (total > 0) {
                        val pct = ((sum * 100) / total).toInt().coerceIn(0, 100)
                        withContext(kotlinx.coroutines.Dispatchers.Main) { xposedDownloadProgress = pct }
                    }
                }
                output.flush(); output.close(); input.close()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", outFile)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    xposedDownloadProgress = -1
                    xposedInstallUri = uri
                }
            } catch (_: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { xposedDownloadProgress = -1 }
            }
        }
    }

    suspend fun closeDrawerSmooth() {
        drawerState.animateTo(
            DrawerValue.Closed,
            androidx.compose.animation.core.tween(durationMillis = 140)
        )
    }

    if (showRunModeDialog) {
        AlertDialog(
            onDismissRequest = { showRunModeDialog = false },
            title = { Text(stringResource(R.string.run_mode_dialog_title)) },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRunModeChange("root")
                                showRunModeDialog = false
                            }
                            .padding(16.dp)
                    ) {
                        RadioButton(
                            selected = runMode == "root",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.run_mode_root))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRunModeDialog = false
                                onDeveloperModeSelected()
                            }
                            .padding(16.dp)
                    ) {
                        RadioButton(
                            selected = runMode == "developer",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.run_mode_developer))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRunModeDialog = false
                                if (isXposedModuleInstalled()) {
                                    onRunModeChange("xposed")
                                } else {
                                    showXposedDownloadDialog = true
                                }
                            }
                            .padding(16.dp)
                    ) {
                        RadioButton(
                            selected = runMode == "xposed",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.run_mode_xposed))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRunModeChange("sandbox")
                                showRunModeDialog = false
                            }
                            .padding(16.dp)
                    ) {
                        RadioButton(
                            selected = runMode == "sandbox",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.run_mode_sandbox))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRunModeDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showEnvDialog) {
        AlertDialog(
            onDismissRequest = { showEnvDialog = false },
            title = { Text(stringResource(R.string.drawer_env_check)) },
            text = { Text(envMessage) },
            confirmButton = {
                TextButton(onClick = { showEnvDialog = false }) {
                    Text(stringResource(R.string.drawer_ok))
                }
            }
        )
    }

    if (showXposedDownloadDialog && xposedDownloadProgress < 0) {
        AlertDialog(
            onDismissRequest = { showXposedDownloadDialog = false },
            title = { Text(stringResource(R.string.xposed_module_not_found)) },
            text = { Text(stringResource(R.string.xposed_module_download_hint)) },
            dismissButton = {
                TextButton(onClick = { showXposedDownloadDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showXposedDownloadDialog = false
                    downloadAndInstallXposed()
                }) {
                    Text(stringResource(R.string.xposed_module_download))
                }
            }
        )
    }

    if (xposedDownloadProgress >= 0) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.xposed_module_download)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.xposed_module_download_hint))
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { xposedDownloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$xposedDownloadProgress%", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = {}
        )
    }

    ModalDrawerSheet {
        LazyColumn {
            item { DrawerHeader(appVersion, onLoginClick = { showLoginActivity = true }, onProfileClick = { showProfileActivity = true }) }
            item { HorizontalDivider() }

            // ===== Group: 模拟 =====
            item {
                Text(
                    text = stringResource(R.string.nav_menu_sim_group),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_location_simulation)) },
                    icon = { Icon(painterResource(R.drawable.ic_position), contentDescription = null) },
                    selected = currentScreen == "LocationSimulation",
                    onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_location_simulation) } }
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_route_simulation)) },
                    icon = { Icon(painterResource(R.drawable.ic_move), contentDescription = null) },
                    selected = currentScreen == "RouteSimulation",
                    onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_route_simulation) } }
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_nav_sim)) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    selected = currentScreen == "NavigationSimulation",
                    onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_navigation_simulation) } }
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_nfc_sim)) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = currentScreen == "NfcSimulation",
                    onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_nfc_simulation) } }
                )
            }
            if (runMode == "root") {
                item {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_menu_independent_sim)) },
                        icon = { Icon(painterResource(R.drawable.ic_position), contentDescription = null) },
                        selected = currentScreen == "IndependentSimulation",
                        onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_independent_simulation) } }
                    )
                }
                item {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_menu_root_app_hide)) },
                        icon = { Icon(painterResource(R.drawable.ic_menu_settings), contentDescription = null) },
                        selected = currentScreen == "RootAppHide",
                        onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_root_app_hide) } }
                    )
                }
                item {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_menu_wifi_sim)) },
                        icon = { Icon(painterResource(R.drawable.ic_menu_settings), contentDescription = null) },
                        selected = currentScreen == "WifiSimulation",
                        onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_wifi_simulation) } }
                    )
                }
                item {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_menu_cell_sim)) },
                        icon = { Icon(painterResource(R.drawable.ic_menu_dev), contentDescription = null) },
                        selected = currentScreen == "CellSimulation",
                        onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_cell_simulation) } }
                    )
                }
            }
            if (runMode == "sandbox") {
                item {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_sandbox)) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        selected = currentScreen == "Sandbox",
                        onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_sandbox) } }
                    )
                }
            }
            if (runMode == "xposed") {
                item {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_xposed_settings)) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        selected = false,
                        onClick = { scope.launch { closeDrawerSmooth(); onXposedSettingsSelected() } }
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ===== Group: 设置 =====
            item {
                Text(
                    text = stringResource(R.string.nav_menu_settings),
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_settings)) },
                    icon = { Icon(painterResource(R.drawable.ic_menu_settings), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_settings) } }
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_run_mode)) },
                    icon = { Icon(painterResource(R.drawable.ic_menu_dev), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { closeDrawerSmooth(); showRunModeDialog = true } }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ===== Group: 更多 =====
            item {
                Text(
                    text = stringResource(R.string.nav_menu_more),
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_contact)) },
                    icon = { Icon(painterResource(R.drawable.ic_contact), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_contact) } }
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_sponsor)) },
                    icon = { Icon(painterResource(R.drawable.ic_user), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_sponsor) } }
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_menu_github)) },
                    icon = { Icon(painterResource(R.drawable.ic_menu_dev), contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_source_code) } }
                )
            }
        }
    }
}
