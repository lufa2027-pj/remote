package com.vu.tvremote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vu.tvremote.data.TvDevice

@Composable
fun DeviceSelectionDialog(
    discovered: List<TvDevice>,
    onDismiss: () -> Unit,
    onSelectDevice: (TvDevice) -> Unit,
    onConnectManual: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var manualIp by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E222A),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connect to VU TV",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF0A84FF))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Discovered on Wi-Fi:",
                    color = Color(0xFF8E95A5),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (discovered.isEmpty()) {
                    Text(
                        text = "No Android TVs discovered yet. Ensure phone and TV are on the same 2.4/5GHz Wi-Fi.",
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(discovered) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectDevice(device) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF0A84FF))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(device.name, color = Color.White, fontSize = 14.sp)
                                    Text(device.host, color = Color(0xFF8E95A5), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = Color(0xFF2C323E))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Or enter TV IP manually:",
                    color = Color(0xFF8E95A5),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    placeholder = { Text("e.g. 192.168.1.105", color = Color(0xFF555D6D)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0A84FF),
                        unfocusedBorderColor = Color(0xFF333B49)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF8E95A5))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConnectManual(manualIp) },
                        enabled = manualIp.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0A84FF)
                        )
                    ) {
                        Text("Connect IP")
                    }
                }
            }
        }
    }
}
