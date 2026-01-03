package com.kail.location.views.routesimulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kail.location.models.RouteInfo
import com.kail.location.models.SimulationSettings
import com.kail.location.models.TransportMode
import com.kail.location.R

// Data Models

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSimulationScreen(
    onBackClick: () -> Unit
) {
    // State
    var settings by remember { mutableStateOf(SimulationSettings()) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Mock Data
    val currentRoute = RouteInfo("1", "天安门", "圆明园")
    val historyRoutes = listOf(
        RouteInfo("2", "天安门", "圆明园"),
        RouteInfo("3", "万达广场", "火车站"),
        RouteInfo("4", "家", "公司")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background with dual colors
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp) // Approximate height for the green background
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFF5F5F5)) // Light gray background for the list
            )
        }

        // Main Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar (Custom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp), // Adjust for status bar
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Changed to ArrowBack to match typical back behavior
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { /* TODO: More options */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }

            // Title
            Text(
                text = "路线模拟",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 24.dp)
            )

            // Target Route Card
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                RouteCard(
                    route = currentRoute,
                    isTarget = true,
                    settings = settings,
                    onSettingsClick = { showSettingsDialog = true },
                    onLoopToggle = { settings = settings.copy(isLoop = it) }
                )
                
                // FAB overlapping the card
                FloatingActionButton(
                    onClick = { /* TODO: Add route */ },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-16).dp, y = (-28).dp)
                        .size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }

            // History Title
            Text(
                text = "历史路线",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )

            // History List
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyRoutes) { route ->
                    RouteCard(route = route, isTarget = false)
                }
            }
        }

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                settings = settings,
                onDismiss = { showSettingsDialog = false },
                onSettingsChange = { settings = it }
            )
        }
    }
}

@Composable
fun RouteCard(
    route: RouteInfo,
    isTarget: Boolean,
    settings: SimulationSettings? = null,
    onSettingsClick: (() -> Unit)? = null,
    onLoopToggle: ((Boolean) -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isTarget) {
                Text(
                    text = "目标路线",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                // Route Visuals (Icons and Line)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 12.dp, top = 4.dp)
                ) {
                    // Start Icon
                    Icon(
                        painter = painterResource(id = R.drawable.ic_home_position), // Using existing icon or similar
                        contentDescription = "Start",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    // Dotted Line (Simulated with Box)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(Color.LightGray) // Ideally dashed
                    )
                    
                    // End Icon
                    Icon(
                        painter = painterResource(id = R.drawable.ic_position), // Using existing icon or similar
                        contentDescription = "End",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Route Details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 24.dp) // Space for the line
                    ) {
                        Text(
                            text = route.startName + route.distance,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = route.endName,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }
                }
            }

            if (isTarget && settings != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { /* TODO: Start Simulation */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("启动模拟", fontSize = 14.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "速/频",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onSettingsClick?.invoke() }
                                .padding(end = 4.dp)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bike), // Using existing icon
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onSettingsClick?.invoke() }
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = "循环",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Switch(
                            checked = settings.isLoop,
                            onCheckedChange = onLoopToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
        }
    }
}

// Extension to scale composables
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

@Composable
fun SettingsDialog(
    settings: SimulationSettings,
    onDismiss: () -> Unit,
    onSettingsChange: (SimulationSettings) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Speed Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("运动速度", fontSize = 14.sp, color = Color.Black)
                    Text("${settings.speed} m/s", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }

                // Speed Slider
                Slider(
                    value = settings.speed,
                    onValueChange = { onSettingsChange(settings.copy(speed = (it * 10).toInt() / 10f)) },
                    valueRange = 0f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Transport Modes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TransportMode.values().forEach { mode ->
                        Icon(
                            painter = painterResource(id = getModeIcon(mode)),
                            contentDescription = mode.name,
                            tint = if (settings.mode == mode) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onSettingsChange(settings.copy(mode = mode)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speed Fluctuation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("速度浮动", fontSize = 14.sp, color = Color.Black)
                    Switch(
                        checked = settings.speedFluctuation,
                        onCheckedChange = { onSettingsChange(settings.copy(speedFluctuation = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }

                // Step Frequency Simulation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("步频模拟", fontSize = 14.sp, color = Color.Black)
                    Switch(
                        checked = settings.stepFreqSimulation,
                        onCheckedChange = { onSettingsChange(settings.copy(stepFreqSimulation = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }

                // Step Frequency Value
                if (settings.stepFreqSimulation) {
                     Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("步频", fontSize = 14.sp, color = Color.Black)
                        Text("${settings.stepFreq} 步/s", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

fun getModeIcon(mode: TransportMode): Int {
    return when (mode) {
        TransportMode.Walk -> R.drawable.ic_walk
        TransportMode.Run -> R.drawable.ic_run
        TransportMode.Bike -> R.drawable.ic_bike
        TransportMode.Car -> R.drawable.ic_move // Placeholder if ic_car doesn't exist
        TransportMode.Plane -> R.drawable.ic_fly
    }
}
