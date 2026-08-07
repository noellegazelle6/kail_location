package com.kail.location.views.camerasimulation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kail.location.R
import com.kail.location.viewmodels.CameraSimulationViewModel
import com.kail.location.views.common.AppDrawer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSimulationScreen(
    viewModel: CameraSimulationViewModel = viewModel(),
    onNavigate: (Int) -> Unit,
    appVersion: String,
    runMode: String,
    onRunModeChange: (String) -> Unit,
    onDeveloperModeSelected: () -> Unit = {},
    onXposedSettingsSelected: () -> Unit = {}
) {
    val enabled by viewModel.enabled.collectAsState()
    val videoName by viewModel.videoName.collectAsState()
    val rotationOffset by viewModel.rotationOffset.collectAsState()
    val photoFake by viewModel.photoFake.collectAsState()
    val targetPackages by viewModel.targetPackages.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onVideoSelected(it) }
    }

    // The enable flag is persisted; re-entering this page while enabled must
    // re-push the config and re-inject targets (they may have been killed or
    // never injected, e.g. toolchain was missing on a previous attempt).
    LaunchedEffect(enabled) {
        if (enabled) viewModel.pushConfigAndInject()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                currentScreen = "CameraSimulation",
                onNavigate = onNavigate,
                appVersion = appVersion,
                runMode = runMode,
                onRunModeChange = onRunModeChange,
                onDeveloperModeSelected = onDeveloperModeSelected,
                onXposedSettingsSelected = onXposedSettingsSelected
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.camera_sim_title), color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.animateTo(DrawerValue.Open, tween(durationMillis = 160)) }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    actions = {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            context.startActivity(android.content.Intent(context, CameraSettingsActivity::class.java))
                        }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                    // ===== Top status card (LocationSimulation style) =====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.camera_sim_video_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (videoName.isEmpty()) stringResource(R.string.camera_sim_no_video) else videoName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.camera_sim_apps_count, targetPackages.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val statusText = stringResource(
                                    if (enabled) R.string.camera_sim_status_on else R.string.camera_sim_status_off
                                )
                                Text(
                                    text = statusText + " · " + rotationOffset + "°" +
                                        if (photoFake) " · " + stringResource(R.string.camera_sim_photo_short) else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { viewModel.setEnabled(!enabled) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (enabled) Color.Red else MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Text(
                                            if (enabled) stringResource(R.string.camera_sim_stop)
                                            else stringResource(R.string.camera_sim_start)
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // FAB overlapping the card corner — pick video
                        FloatingActionButton(
                            onClick = { videoPicker.launch("video/*") },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Pick video", tint = Color.White)
                        }
                    }

                    if (statusMessage.isNotEmpty()) {
                        Text(
                            text = statusMessage,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // ===== Video library =====
                    Text(
                        text = stringResource(R.string.camera_sim_videos_section),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelSmall
                    )

                    val videoLibrary by viewModel.videoLibrary.collectAsState()

                    if (videoLibrary.isEmpty()) {
                        Text(
                            text = stringResource(R.string.camera_sim_empty_videos),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(videoLibrary, key = { it }) { video ->
                                val isCurrent = video == videoName
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectVideo(video) },
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = video,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
                                            )
                                            if (isCurrent) {
                                                Text(
                                                    text = stringResource(R.string.camera_sim_video_in_use),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteVideo(video) }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Delete",
                                                tint = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}
