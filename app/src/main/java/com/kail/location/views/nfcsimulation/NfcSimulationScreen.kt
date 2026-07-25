package com.kail.location.views.nfcsimulation

import com.kail.location.utils.GoUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.kail.location.R
import com.kail.location.views.common.AppDrawer
import android.widget.Toast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcSimulationScreen(
    viewModel: NfcSimulationViewModel,
    onNavigate: (Int) -> Unit,
    appVersion: String
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Run mode is shared app-wide via the default SharedPreferences
    // ("setting_run_mode"). Reflect the current selection in the side menu and
    // persist changes so the NFC page's menu tracks the mode like every other
    // screen, instead of being hardcoded to "root".
    val appPrefs = remember {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }
    var runMode by remember {
        mutableStateOf(appPrefs.getString("setting_run_mode", "developer") ?: "developer")
    }
    
    val nfcEnabled by viewModel.nfcEnabled.collectAsState()
    val tagId by viewModel.tagId.collectAsState()
    val tagType by viewModel.tagType.collectAsState()
    val ndefContent by viewModel.ndefContent.collectAsState()
    val mockUrl by viewModel.mockUrl.collectAsState()
    val mockPackageName by viewModel.mockPackageName.collectAsState()
    val sendResult by viewModel.sendResult.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()
    
    var urlInput by remember { mutableStateOf("") }
    var packageInput by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    
    // Auto-fill input fields from ViewModel
    // Use scanCount to force update on each scan
    val scanCount by viewModel.scanCount.collectAsState()
    LaunchedEffect(scanCount) {
        urlInput = mockUrl
        packageInput = mockPackageName
    }
    
    LaunchedEffect(sendResult) {
        sendResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSendResult()
        }
    }
    
    // Rename Dialog
    showRenameDialog?.let { id ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text(stringResource(R.string.nfc_sim_rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.nfc_sim_rename_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameHistory(id, renameText)
                    showRenameDialog = null
                }) {
                    Text(stringResource(R.string.nfc_sim_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text(stringResource(R.string.nfc_sim_rename_cancel))
                }
            }
        )
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "NfcSimulation",
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
                    title = { Text(stringResource(R.string.nfc_sim_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.animateTo(DrawerValue.Open, androidx.compose.animation.core.tween(durationMillis = 160)) } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // NFC Status
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.nfc_sim_status), fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (nfcEnabled) stringResource(R.string.nfc_sim_enabled) else stringResource(R.string.nfc_sim_disabled),
                                color = if (nfcEnabled) Color(0xFF4CAF50) else Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Read Result
                if (tagId != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.nfc_sim_read_success),
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                    TextButton(onClick = { viewModel.clearTag() }) {
                                        Text(stringResource(R.string.nfc_sim_clear), color = Color.Red)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = stringResource(R.string.nfc_sim_tag_id, tagId ?: ""), fontSize = 14.sp)
                                Text(text = stringResource(R.string.nfc_sim_tag_type, tagType ?: ""), fontSize = 14.sp, color = Color.Gray)
                                ndefContent?.let {
                                    Text(text = stringResource(R.string.nfc_sim_content, it), fontSize = 14.sp, color = Color.Gray, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                
                // Mock Send
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.nfc_sim_send_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text(stringResource(R.string.nfc_sim_url)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = packageInput,
                                onValueChange = { packageInput = it },
                                label = { Text(stringResource(R.string.nfc_sim_package)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    viewModel.updateMockUrl(urlInput)
                                    viewModel.updateMockPackageName(packageInput)
                                    viewModel.sendMockNfc(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.nfc_sim_send))
                            }
                        }
                    }
                }
                
                // History Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.nfc_sim_history),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (historyRecords.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearHistory() }) {
                                Text(stringResource(R.string.nfc_sim_clear_history), color = Color.Red)
                            }
                        }
                    }
                }
                
                // History List
                if (historyRecords.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.nfc_sim_no_history),
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(historyRecords) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { viewModel.applyFromHistory(item) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (item.name.isNotBlank()) {
                                        Text(
                                            text = item.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = item.content,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.name.isNotBlank()) 0.6f else 0.87f)
                                    )
                                    Text(
                                        text = "${item.type} • ${formatTime(item.timestamp)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Favorite",
                                        tint = if (item.isFavorite) Color(0xFFFFB300) else Color.Gray,
                                        modifier = Modifier.graphicsLayer(alpha = if (item.isFavorite) 1f else 0.4f)
                                    )
                                }
                                IconButton(onClick = {
                                    showRenameDialog = item.id
                                    renameText = item.name
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.nfc_sim_rename_content_desc),
                                        tint = Color.Gray
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteHistory(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.nfc_sim_delete_content_desc),
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Help
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.nfc_sim_help_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.nfc_sim_help_content),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
