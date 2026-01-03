package com.kail.location.views.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kail.location.R
import com.kail.location.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    // State observation
    val joystickType by viewModel.joystickType.collectAsState()
    val walkSpeed by viewModel.walkSpeed.collectAsState()
    val runSpeed by viewModel.runSpeed.collectAsState()
    val bikeSpeed by viewModel.bikeSpeed.collectAsState()
    val altitude by viewModel.altitude.collectAsState()
    val randomOffset by viewModel.randomOffset.collectAsState()
    val latOffset by viewModel.latOffset.collectAsState()
    val lonOffset by viewModel.lonOffset.collectAsState()
    val logOff by viewModel.logOff.collectAsState()
    val historyExpiration by viewModel.historyExpiration.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_menu_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        ) {
            // Group: Move
            PreferenceCategory(title = stringResource(R.string.setting_group_move))

            ListPreference(
                title = stringResource(R.string.setting_joystick_type),
                currentValue = joystickType,
                entries = stringArrayResource(R.array.array_joystick_type),
                entryValues = stringArrayResource(R.array.array_joystick_type_values),
                onValueChange = { viewModel.updateStringPreference(SettingsViewModel.KEY_JOYSTICK_TYPE, it) }
            )

            EditTextPreference(
                title = stringResource(R.string.setting_walk),
                value = walkSpeed,
                onValueChange = { viewModel.updateStringPreference(SettingsViewModel.KEY_WALK_SPEED, it) }
            )

            EditTextPreference(
                title = stringResource(R.string.setting_run),
                value = runSpeed,
                onValueChange = { viewModel.updateStringPreference(SettingsViewModel.KEY_RUN_SPEED, it) }
            )

            EditTextPreference(
                title = stringResource(R.string.setting_bike),
                value = bikeSpeed,
                onValueChange = { viewModel.updateStringPreference(SettingsViewModel.KEY_BIKE_SPEED, it) }
            )

            EditTextPreference(
                title = stringResource(R.string.setting_altitude),
                value = altitude,
                onValueChange = { viewModel.updateStringPreference(SettingsViewModel.KEY_ALTITUDE, it) }
            )

            // Group: Location Offset
            PreferenceCategory(title = stringResource(R.string.setting_group_location_offset))

            SwitchPreference(
                title = stringResource(R.string.setting_random_offset),
                checked = randomOffset,
                onCheckedChange = { viewModel.updateBooleanPreference(SettingsViewModel.KEY_RANDOM_OFFSET, it) },
                summary = stringResource(R.string.setting_random_offset_summary)
            )

            EditTextPreference(
                title = "纬度最大偏移(米)", // Resource might be setting_lat_max_offset but title in xml might be different. Using manual or look up.
                // Looking at strings.xml in FragmentSettings.kt, it just loads preferences_main.xml.
                // I don't have preferences_main.xml content but I saw strings.xml keys.
                // strings.xml didn't have specific string for lat/lon offset TITLE, but maybe it does.
                // I will use "纬度最大偏移(米)" as fallback or look at strings.xml again.
                // strings.xml has: <string name="setting_random_offset">随机偏移</string>
                // It does NOT seem to have explicit titles for max offset in the snippet I read.
                // Wait, FragmentSettings code: EditTextPreference pfLatOffset = findPreference("setting_lat_max_offset");
                // The title comes from XML.
                // I'll assume sensible defaults or check strings.xml again.
                // Ah, strings.xml lines 68-71:
                // <string name="setting_group_location_offset">位置偏移</string>
                // <string name="setting_random_offset">随机偏移</string>
                // <string name="setting_random_offset_summary">仅在使用历史位置时生效</string>
                // It doesn't show titles for lat/lon max offset. They might be hardcoded in XML or I missed them.
                // I'll use "纬度最大偏移(米)" and "经度最大偏移(米)".
                value = latOffset,
                onValueChange = { viewModel.updateStringPreference(SettingsViewModel.KEY_LAT_OFFSET, it) }
            )

            EditTextPreference(
                title = "经度最大偏移(米)",
                value = lonOffset,
                onValueChange = { viewModel.updateStringPreference(SettingsViewModel.KEY_LON_OFFSET, it) }
            )

            // Group: Other
            PreferenceCategory(title = "其他") // "其他" or generic

            SwitchPreference(
                title = "关闭日志", // setting_log_off
                checked = logOff,
                onCheckedChange = { viewModel.updateBooleanPreference(SettingsViewModel.KEY_LOG_OFF, it) }
            )

            EditTextPreference(
                title = "历史记录有效期(天)", // setting_history_expiration
                value = historyExpiration,
                onValueChange = { viewModel.updateStringPreference(SettingsViewModel.KEY_HISTORY_EXPIRATION, it) }
            )

            ListItem(
                headlineContent = { Text("当前版本") }, // setting_version
                supportingContent = { Text(viewModel.appVersion) }
            )
        }
    }
}

@Composable
fun PreferenceCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun EditTextPreference(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        var tempValue by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempValue.isNotBlank()) {
                            onValueChange(tempValue)
                        }
                        showDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ListPreference(
    title: String,
    currentValue: String,
    entries: Array<String>,
    entryValues: Array<String>,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    // Find display name
    val index = entryValues.indexOf(currentValue)
    val displayValue = if (index >= 0 && index < entries.size) entries[index] else currentValue

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(displayValue) },
        modifier = Modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEachIndexed { i, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(entryValues[i])
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (entryValues[i] == currentValue),
                                onClick = null // Handled by Row click
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = entry)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
