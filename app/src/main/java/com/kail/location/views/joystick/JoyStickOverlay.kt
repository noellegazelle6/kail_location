package com.kail.location.views.joystick

import android.content.Context
import android.content.SharedPreferences
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.kail.location.R
import com.kail.location.viewmodels.SettingsViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun JoyStickOverlay(
    onMoveInfo: (Boolean, Double, Double) -> Unit, // auto, angle, r
    onSpeedChange: (Double) -> Unit,
    onWindowDrag: (Float, Float) -> Unit,
    onOpenMap: () -> Unit,
    onOpenHistory: () -> Unit,
    onClose: () -> Unit // Not used in current XML but good to have
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    // Settings
    val joystickType = remember { prefs.getString(SettingsViewModel.KEY_JOYSTICK_TYPE, "0") ?: "0" }
    
    // State
    var currentMode by remember { mutableStateOf(JoyStickMode.WALK) }
    
    // Initial speed
    LaunchedEffect(Unit) {
        val speed = getSpeedForMode(context, prefs, JoyStickMode.WALK)
        onSpeedChange(speed)
    }

    Column(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Row: Move, History, Map
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                iconRes = R.drawable.ic_move,
                contentDescription = "Move",
                onClick = { /* Drag handled by parent pointerInput */ },
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onWindowDrag(dragAmount.x, dragAmount.y)
                    }
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            CircleIconButton(
                iconRes = R.drawable.ic_history,
                contentDescription = "History",
                onClick = onOpenHistory
            )
            Spacer(modifier = Modifier.width(4.dp))
            CircleIconButton(
                iconRes = R.drawable.ic_map,
                contentDescription = "Map",
                onClick = onOpenMap
            )
        }

        // Center: Rocker or Buttons
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (joystickType == "0") {
                Rocker(
                    modifier = Modifier.size(140.dp), // Approx 140dp based on XML usage
                    onUpdate = onMoveInfo
                )
            } else {
                DirectionalButtons(
                    onUpdate = onMoveInfo
                )
            }
        }

        // Bottom Row: Walk, Run, Bike
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                iconRes = R.drawable.ic_walk,
                contentDescription = "Walk",
                isSelected = currentMode == JoyStickMode.WALK,
                onClick = {
                    currentMode = JoyStickMode.WALK
                    onSpeedChange(getSpeedForMode(context, prefs, JoyStickMode.WALK))
                }
            )
            Spacer(modifier = Modifier.width(20.dp)) // layout_weight logic replaced by spacer
            CircleIconButton(
                iconRes = R.drawable.ic_run,
                contentDescription = "Run",
                isSelected = currentMode == JoyStickMode.RUN,
                onClick = {
                    currentMode = JoyStickMode.RUN
                    onSpeedChange(getSpeedForMode(context, prefs, JoyStickMode.RUN))
                }
            )
            Spacer(modifier = Modifier.width(20.dp))
            CircleIconButton(
                iconRes = R.drawable.ic_bike,
                contentDescription = "Bike",
                isSelected = currentMode == JoyStickMode.BIKE,
                onClick = {
                    currentMode = JoyStickMode.BIKE
                    onSpeedChange(getSpeedForMode(context, prefs, JoyStickMode.BIKE))
                }
            )
        }
    }
}

@Composable
fun Rocker(
    modifier: Modifier = Modifier,
    onUpdate: (Boolean, Double, Double) -> Unit
) {
    // Constants
    val outerCircleColor = Color.Gray.copy(alpha = 0.7f)
    val innerCircleColor = Color.LightGray.copy(alpha = 0.7f)
    
    var isAuto by remember { mutableStateOf(false) }
    var innerCenter by remember { mutableStateOf(Offset.Zero) }
    var viewCenter by remember { mutableStateOf(Offset.Zero) }
    var outerRadius by remember { mutableStateOf(0f) }
    var innerRadius by remember { mutableStateOf(0f) }
    
    val lockCloseIcon = painterResource(R.drawable.ic_lock_close)
    val lockOpenIcon = painterResource(R.drawable.ic_lock_open)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val position = change.position
                        
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Press -> {
                                // Check if inside inner circle (current position)
                                val dist = (position - innerCenter).getDistance()
                                if (dist <= innerRadius * 2) { // Allow some margin or check against innerRadius
                                    // Start dragging
                                }
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                change.consume()
                                // Calculate new inner center
                                val dist = (position - viewCenter).getDistance()
                                val maxDist = outerRadius - innerRadius
                                
                                if (dist < maxDist) {
                                    innerCenter = position
                                } else {
                                    // Constrain to outer circle
                                    val ratio = maxDist / dist
                                    innerCenter = viewCenter + (position - viewCenter) * ratio
                                }
                                
                                // Calculate angle and r
                                val dx = innerCenter.x - viewCenter.x
                                val dy = innerCenter.y - viewCenter.y
                                val angle = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())) - 90
                                val r = sqrt(dx.pow(2) + dy.pow(2)) / maxDist
                                
                                onUpdate(true, angle, r.toDouble())
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                // Check for click (toggle auto)
                                // Simplified click detection: if little movement?
                                // Actually, RockerView toggles auto on UP if isClick is true.
                                // Here we can just check if we want to toggle.
                                // For now, let's just reset if not auto.
                                
                                if (!isAuto) {
                                    innerCenter = viewCenter
                                    onUpdate(false, 0.0, 0.0) // Stop
                                }
                                
                                // Toggle auto logic: RockerView does it on UP if it was a "click" (no move)
                                // Let's implement a simple click listener on the inner circle via Box?
                                // No, pointerInput consumes everything.
                            }
                        }
                    }
                }
            }
            .clickable { 
                // Toggle auto
                isAuto = !isAuto
                // Reset bitmap logic handled by drawing
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            viewCenter = center
            outerRadius = size.minDimension / 2
            innerRadius = outerRadius * 0.4f // Approx ratio
            
            if (innerCenter == Offset.Zero) {
                innerCenter = viewCenter
            }

            // Draw Outer Circle
            drawCircle(
                color = outerCircleColor,
                radius = outerRadius,
                center = viewCenter
            )

            // Draw Inner Circle
            drawCircle(
                color = innerCircleColor,
                radius = innerRadius,
                center = innerCenter
            )
            
            // Draw Icon
            // This is a bit tricky with Canvas, easier to use Image composable overlaid
        }
        
        // Overlay Icon
        Image(
            painter = if (isAuto) lockCloseIcon else lockOpenIcon,
            contentDescription = null,
            modifier = Modifier
                .size((innerRadius * 2 / LocalContext.current.resources.displayMetrics.density).dp) // Convert px to dp? No, usage logic needed.
                // Better: Use Layout to position
                .align(Alignment.TopStart) // Placeholder, we need absolute positioning
                .offset { 
                    androidx.compose.ui.unit.IntOffset(
                        (innerCenter.x - innerRadius).toInt(),
                        (innerCenter.y - innerRadius).toInt()
                    )
                }
                .size(40.dp) // Approximate size
                .alpha(0.8f)
        )
    }
}
// Re-implement Rocker simpler:
@Composable
fun RockerSimple(
    modifier: Modifier = Modifier,
    onUpdate: (Boolean, Double, Double) -> Unit
) {
    var isAuto by remember { mutableStateOf(false) }
    var innerOffset by remember { mutableStateOf(Offset.Zero) } // Offset from center
    
    val outerRadius = 70.dp
    val innerRadius = 30.dp
    val maxDist = with(LocalDensity.current) { (outerRadius - innerRadius).toPx() }
    
    Box(
        modifier = modifier
            .size(outerRadius * 2)
            .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (!isAuto) {
                            innerOffset = Offset.Zero
                            onUpdate(false, 0.0, 0.0)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = innerOffset + Offset(dragAmount.x, dragAmount.y)
                        val dist = newOffset.getDistance()
                        
                        innerOffset = if (dist > maxDist) {
                            newOffset * (maxDist / dist)
                        } else {
                            newOffset
                        }
                        
                        // Calculate angle/r
                        // Note: Android coordinates: Y down.
                        // RockerView: atan2(x, y) - 90.
                        // x is horizontal, y is vertical.
                        val angle = Math.toDegrees(atan2(innerOffset.x.toDouble(), innerOffset.y.toDouble())) - 90
                        val r = dist / maxDist
                        onUpdate(true, angle, r.toDouble())
                    }
                )
            }
    ) {
        // Inner Circle (Joystick Handle)
        Box(
            modifier = Modifier
                .size(innerRadius * 2)
                .offset { androidx.compose.ui.unit.IntOffset(innerOffset.x.toInt(), innerOffset.y.toInt()) }
                .align(Alignment.Center)
                .background(Color.LightGray.copy(alpha = 0.8f), CircleShape)
                .clickable { isAuto = !isAuto },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (isAuto) R.drawable.ic_lock_close else R.drawable.ic_lock_open),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


@Composable
fun DirectionalButtons(
    onUpdate: (Boolean, Double, Double) -> Unit
) {
    // State to track locking (Center button state)
    var isLocked by remember { mutableStateOf(true) }
    // State to track currently active direction (only when locked)
    var activeDirection by remember { mutableStateOf<Double?>(null) }

    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.secondary
    val defaultColor = Color.Black

    // Helper to handle direction clicks
    fun onDirectionClick(angle: Double) {
        if (isLocked) {
            if (activeDirection == angle) {
                // Stop current direction
                activeDirection = null
                onUpdate(false, angle, 0.0)
            } else {
                // Start new direction
                activeDirection = angle
                onUpdate(true, angle, 1.0)
            }
        } else {
            // Manual mode: single step
            onUpdate(false, angle, 1.0)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: NW, N, NE
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionButton(
                iconRes = R.drawable.ic_left_up,
                isActive = isLocked && activeDirection == 135.0,
                onClick = { onDirectionClick(135.0) }
            )
            DirectionButton(
                iconRes = R.drawable.ic_up,
                isActive = isLocked && activeDirection == 90.0,
                onClick = { onDirectionClick(90.0) }
            )
            DirectionButton(
                iconRes = R.drawable.ic_right_up,
                isActive = isLocked && activeDirection == 45.0,
                onClick = { onDirectionClick(45.0) }
            )
        }
        // Row 2: W, Center, E
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionButton(
                iconRes = R.drawable.ic_left,
                isActive = isLocked && activeDirection == 180.0,
                onClick = { onDirectionClick(180.0) }
            )
            // Center Button
            IconButton(
                onClick = {
                    if (isLocked) {
                        // Unlock
                        isLocked = false
                        activeDirection = null
                        onUpdate(false, 0.0, 0.0) // Stop everything
                    } else {
                        // Lock
                        isLocked = true
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(if (isLocked) R.drawable.ic_lock_close else R.drawable.ic_lock_open),
                    contentDescription = "Lock/Unlock",
                    tint = if (isLocked) accentColor else defaultColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            DirectionButton(
                iconRes = R.drawable.ic_right,
                isActive = isLocked && activeDirection == 0.0,
                onClick = { onDirectionClick(0.0) }
            )
        }
        // Row 3: SW, S, SE
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionButton(
                iconRes = R.drawable.ic_left_down,
                isActive = isLocked && activeDirection == 225.0,
                onClick = { onDirectionClick(225.0) }
            )
            DirectionButton(
                iconRes = R.drawable.ic_down,
                isActive = isLocked && activeDirection == 270.0,
                onClick = { onDirectionClick(270.0) }
            )
            DirectionButton(
                iconRes = R.drawable.ic_right_down,
                isActive = isLocked && activeDirection == 315.0,
                onClick = { onDirectionClick(315.0) }
            )
        }
    }
}

@Composable
fun DirectionButton(
    iconRes: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isActive) MaterialTheme.colorScheme.secondary else Color.Black
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun CircleIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.LightGray.copy(alpha = 0.5f)
    val tint = if (isSelected) Color.White else Color.Black

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

enum class JoyStickMode {
    WALK, RUN, BIKE
}

fun getSpeedForMode(context: Context, prefs: SharedPreferences, mode: JoyStickMode): Double {
    return when (mode) {
        JoyStickMode.WALK -> prefs.getString(SettingsViewModel.KEY_WALK_SPEED, "1.2")?.toDoubleOrNull() ?: 1.2
        JoyStickMode.RUN -> prefs.getString(SettingsViewModel.KEY_RUN_SPEED, "3.6")?.toDoubleOrNull() ?: 3.6
        JoyStickMode.BIKE -> prefs.getString(SettingsViewModel.KEY_BIKE_SPEED, "10.0")?.toDoubleOrNull() ?: 10.0
    }
}
