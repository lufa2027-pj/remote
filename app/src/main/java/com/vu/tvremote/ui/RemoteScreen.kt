package com.vu.tvremote.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vu.tvremote.data.ConnectionState
import com.vu.tvremote.data.RemoteUiState
import com.vu.tvremote.protocol.KeyCodes
import com.vu.tvremote.ui.components.DeviceSelectionDialog
import com.vu.tvremote.ui.components.PairingDialog
import com.vu.tvremote.viewmodel.RemoteViewModel

@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: VU Branding & Connection Bar
            HeaderSection(
                state = state,
                onScanClick = { viewModel.startScanning() },
                onManualIpClick = { viewModel.showManualIpDialog(true) },
                onDisconnect = { viewModel.disconnect() }
            )

            // Top Quick Controls: Power, Mute, Play/Pause
            TopControlsRow(
                isConnected = state.connectionState is ConnectionState.Connected,
                onPower = { viewModel.pressKey(KeyCodes.KEYCODE_POWER, "Power") },
                onMute = { viewModel.pressKey(KeyCodes.KEYCODE_VOLUME_MUTE, "Mute") },
                onPlayPause = { viewModel.pressKey(KeyCodes.KEYCODE_MEDIA_PLAY_PAUSE, "Play/Pause") }
            )

            // Mode Toggle: Gesture Touchpad vs D-Pad Wheel
            var useTouchpad by remember { mutableStateOf(true) }
            Row(
                modifier = Modifier
                    .background(Color(0xFF191D24), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2C323E), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = if (useTouchpad) Color(0xFF0A84FF) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.clickable { useTouchpad = true }
                ) {
                    Text(
                        text = "Gesture Touchpad",
                        color = if (useTouchpad) Color.White else Color(0xFF8E95A5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Surface(
                    color = if (!useTouchpad) Color(0xFF0A84FF) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.clickable { useTouchpad = false }
                ) {
                    Text(
                        text = "D-Pad Wheel",
                        color = if (!useTouchpad) Color.White else Color(0xFF8E95A5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Centerpiece: Gesture Touchpad or D-PAD Wheel
            if (useTouchpad) {
                GestureTouchpad(
                    isConnected = state.connectionState is ConnectionState.Connected,
                    onSwipeUp = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_UP, "Swipe Up") },
                    onSwipeDown = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_DOWN, "Swipe Down") },
                    onSwipeLeft = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_LEFT, "Swipe Left") },
                    onSwipeRight = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_RIGHT, "Swipe Right") },
                    onTap = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_CENTER, "Tap (OK)") }
                )
            } else {
                DPadWheel(
                    isConnected = state.connectionState is ConnectionState.Connected,
                    onUp = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_UP, "Up") },
                    onDown = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_DOWN, "Down") },
                    onLeft = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_LEFT, "Left") },
                    onRight = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_RIGHT, "Right") },
                    onCenter = { viewModel.pressKey(KeyCodes.KEYCODE_DPAD_CENTER, "OK") }
                )
            }

            // Essential Navigation Row: Back, Home, Menu
            NavigationRow(
                isConnected = state.connectionState is ConnectionState.Connected,
                onBack = { viewModel.pressKey(KeyCodes.KEYCODE_BACK, "Back") },
                onHome = { viewModel.pressKey(KeyCodes.KEYCODE_HOME, "Home") },
                onMenu = { viewModel.pressKey(KeyCodes.KEYCODE_MENU, "Menu") }
            )

            // Volume Section & Footer Hardware Badge
            VolumeSection(
                isConnected = state.connectionState is ConnectionState.Connected,
                onVolUp = { viewModel.pressKey(KeyCodes.KEYCODE_VOLUME_UP, "Vol +") },
                onVolDown = { viewModel.pressKey(KeyCodes.KEYCODE_VOLUME_DOWN, "Vol -") }
            )

            // Footer Hardware Label
            FooterLabel(lastPressed = state.lastPressedKey)
        }

        // Dialogs
        if (state.showPairingDialog) {
            PairingDialog(
                onDismiss = { viewModel.showPairingDialog(false) },
                onSubmit = { pin -> viewModel.submitPairingPin(pin) }
            )
        }

        if (state.showManualIpDialog) {
            DeviceSelectionDialog(
                discovered = state.discoveredDevices,
                onDismiss = { viewModel.showManualIpDialog(false) },
                onSelectDevice = { device -> viewModel.connectToDevice(device) },
                onConnectManual = { ip -> viewModel.connectWithManualIp(ip) },
                onRefresh = { viewModel.startScanning() }
            )
        }
    }
}

@Composable
private fun HeaderSection(
    state: RemoteUiState,
    onScanClick: () -> Unit,
    onManualIpClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VU SMART 2K",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Model 80V4 · Android 9",
                    color = Color(0xFF8E95A5),
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onManualIpClick) {
                    Icon(
                        imageVector = Icons.Default.SettingsEthernet,
                        contentDescription = "Manual IP / Devices",
                        tint = Color(0xFFB0B7C3)
                    )
                }
                IconButton(onClick = onScanClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan Wi-Fi",
                        tint = Color(0xFF0A84FF)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Connection Status Banner
        val (statusText, statusColor) = when (val s = state.connectionState) {
            is ConnectionState.Connected -> Pair("Connected to ${s.device.host}", Color(0xFF30D158))
            is ConnectionState.Connecting -> Pair("Connecting to ${s.device.name}…", Color(0xFFFF9F0A))
            is ConnectionState.Discovering -> Pair("Scanning Wi-Fi for VU TV…", Color(0xFF0A84FF))
            is ConnectionState.PairingRequired -> Pair("Enter PIN shown on TV", Color(0xFFFF453A))
            is ConnectionState.Error -> Pair(s.message, Color(0xFFFF453A))
            ConnectionState.Disconnected -> Pair("Disconnected · Tap Refresh to scan", Color(0xFF8E95A5))
        }

        Surface(
            color = Color(0xFF191D24),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        color = Color(0xFFE1E4EA),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                if (state.connectionState is ConnectionState.Connected) {
                    Text(
                        text = "Disconnect",
                        color = Color(0xFFFF453A),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onDisconnect() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopControlsRow(
    isConnected: Boolean,
    onPower: () -> Unit,
    onMute: () -> Unit,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Power Button with red highlight
        RemoteIconButton(
            icon = Icons.Default.PowerSettingsNew,
            label = "Power",
            iconColor = Color(0xFFFF453A),
            enabled = isConnected,
            onClick = onPower
        )

        // Mute Button
        RemoteIconButton(
            icon = Icons.Default.VolumeOff,
            label = "Mute",
            enabled = isConnected,
            onClick = onMute
        )

        // Play/Pause Button
        RemoteIconButton(
            icon = Icons.Default.PlayArrow,
            label = "Play/Pause",
            enabled = isConnected,
            onClick = onPlayPause
        )
    }
}

@Composable
private fun DPadWheel(
    isConnected: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF262C36), Color(0xFF181C23))
                )
            )
            .border(2.dp, Color(0xFF333B49), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Up
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .size(52.dp)
                .clip(CircleShape)
                .clickable(enabled = isConnected, onClick = onUp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Down
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(52.dp)
                .clip(CircleShape)
                .clickable(enabled = isConnected, onClick = onDown),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Left
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(52.dp)
                .clip(CircleShape)
                .clickable(enabled = isConnected, onClick = onLeft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Right
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(52.dp)
                .clip(CircleShape)
                .clickable(enabled = isConnected, onClick = onRight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Center OK Button
        Box(
            modifier = Modifier
                .size(86.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF333B47), Color(0xFF1E232B))
                    )
                )
                .border(1.5.dp, Color(0xFF454F60), CircleShape)
                .clickable(enabled = isConnected, onClick = onCenter),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun NavigationRow(
    isConnected: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteIconButton(
            icon = Icons.Default.ArrowBack,
            label = "Back",
            enabled = isConnected,
            onClick = onBack
        )

        RemoteIconButton(
            icon = Icons.Default.Home,
            label = "Home",
            iconColor = Color(0xFF0A84FF),
            enabled = isConnected,
            onClick = onHome
        )

        RemoteIconButton(
            icon = Icons.Default.Menu,
            label = "Menu",
            enabled = isConnected,
            onClick = onMenu
        )
    }
}

@Composable
private fun VolumeSection(
    isConnected: Boolean,
    onVolUp: () -> Unit,
    onVolDown: () -> Unit
) {
    Surface(
        color = Color(0xFF1C212A),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3644)),
        modifier = Modifier
            .width(180.dp)
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onVolDown,
                enabled = isConnected,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Volume Down",
                    tint = Color.White
                )
            }

            Text(
                text = "VOL",
                color = Color(0xFF8E95A5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onVolUp,
                enabled = isConnected,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Volume Up",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun RemoteIconButton(
    icon: ImageVector,
    label: String,
    iconColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = when {
        !enabled -> Color(0xFF181B22)
        isPressed -> Color(0xFF384050)
        else -> Color(0xFF222731)
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, Color(0xFF313846), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) iconColor else Color(0xFF555B68),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun GestureTouchpad(
    isConnected: Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onTap: () -> Unit
) {
    var dragAccumulatorX by remember { mutableFloatStateOf(0f) }
    var dragAccumulatorY by remember { mutableFloatStateOf(0f) }
    val threshold = 55f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E232B), Color(0xFF13171F))
                )
            )
            .border(2.dp, Color(0xFF2E3542), RoundedCornerShape(24.dp))
            .pointerInput(isConnected) {
                if (!isConnected) return@pointerInput
                detectTapGestures(
                    onTap = { onTap() }
                )
            }
            .pointerInput(isConnected) {
                if (!isConnected) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragAccumulatorX = 0f
                        dragAccumulatorY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatorX += dragAmount.x
                        dragAccumulatorY += dragAmount.y

                        if (kotlin.math.abs(dragAccumulatorX) > kotlin.math.abs(dragAccumulatorY)) {
                            if (dragAccumulatorX > threshold) {
                                onSwipeRight()
                                dragAccumulatorX = 0f
                            } else if (dragAccumulatorX < -threshold) {
                                onSwipeLeft()
                                dragAccumulatorX = 0f
                            }
                        } else {
                            if (dragAccumulatorY > threshold) {
                                onSwipeDown()
                                dragAccumulatorY = 0f
                            } else if (dragAccumulatorY < -threshold) {
                                onSwipeUp()
                                dragAccumulatorY = 0f
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = Color(0xFF0A84FF),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Swipe to Scroll",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tap anywhere for Select (OK)",
                color = Color(0xFF8E95A5),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun FooterLabel(lastPressed: String?) {
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "com.google.android.tv.remote.service",
            color = Color(0xFF5A6273),
            fontSize = 10.sp
        )
        if (!lastPressed.isNullOrEmpty()) {
            Text(
                text = " · Sent: $lastPressed",
                color = Color(0xFF0A84FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
