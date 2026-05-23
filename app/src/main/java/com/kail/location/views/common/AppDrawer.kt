package com.kail.location.views.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kail.location.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    drawerState: DrawerState,
    currentScreen: String,
    onNavigate: (Int) -> Unit,
    appVersion: String,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()
) {
    var showRunModeDialog by remember { mutableStateOf(false) }
    var showEnvDialog by remember { mutableStateOf(false) }
    var envMessage by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

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
                                onRunModeChange("noroot")
                                showRunModeDialog = false
                            }
                            .padding(16.dp)
                    ) {
                        RadioButton(
                            selected = runMode == "noroot",
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.run_mode_noroot))
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

    ModalDrawerSheet {
        DrawerHeader(appVersion)
        HorizontalDivider()
        
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_menu_location_simulation)) },
            icon = { Icon(painterResource(R.drawable.ic_position), contentDescription = null) },
            selected = currentScreen == "LocationSimulation",
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_location_simulation) } }
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_menu_route_simulation)) },
            icon = { Icon(painterResource(R.drawable.ic_move), contentDescription = null) },
            selected = currentScreen == "RouteSimulation",
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_route_simulation) } }
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_nav_sim)) },
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            selected = currentScreen == "NavigationSimulation",
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_navigation_simulation) } }
        )

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_nfc_sim)) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = currentScreen == "NfcSimulation",
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_nfc_simulation) } }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        Text(
            text = stringResource(R.string.nav_menu_settings),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall
        )
        
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_menu_settings)) },
            icon = { Icon(painterResource(R.drawable.ic_menu_settings), contentDescription = null) },
            selected = false,
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_settings) } }
        )

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_run_mode)) },
            icon = { Icon(painterResource(R.drawable.ic_menu_dev), contentDescription = null) },
            selected = false,
            onClick = { scope.launch { closeDrawerSmooth(); showRunModeDialog = true } }
        )
        
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_menu_dev)) },
            icon = { Icon(painterResource(R.drawable.ic_menu_dev), contentDescription = null) },
            selected = false,
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_dev) } }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.nav_menu_more),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_menu_contact)) },
            icon = { Icon(painterResource(R.drawable.ic_contact), contentDescription = null) },
            selected = false,
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_contact) } }
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_menu_sponsor)) },
            icon = { Icon(painterResource(R.drawable.ic_user), contentDescription = null) },
            selected = false,
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_sponsor) } }
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_menu_github)) },
            icon = { Icon(painterResource(R.drawable.ic_menu_dev), contentDescription = null) },
            selected = false,
            onClick = { scope.launch { closeDrawerSmooth(); onNavigate(R.id.nav_source_code) } }
        )
    }
}
