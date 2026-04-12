package com.velo.app.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.velo.app.ui.theme.veloColors
import kotlinx.coroutines.launch

@Composable
fun AccessGateway(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Natively read the cached offline preference instantly
    var isActive by remember { mutableStateOf(DeviceTracker.isDeviceActive(context)) }
    var isChecking by remember { mutableStateOf(false) }

    // Silently evaluate the Neon database over the network once upon composable mount
    LaunchedEffect(Unit) {
        val serverResponse = DeviceTracker.verifyServerStatus(context)
        if (serverResponse != null) {
            isActive = serverResponse
            DeviceTracker.setDeviceStatus(context, serverResponse)
        }
    }

    if (isActive) {
        // App allowed
        content()
    } else {
        // App forcefully sandboxed
        val colors = veloColors
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.bg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = "Access Blocked",
                    tint = colors.accent,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Access Revoked",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "You are not permitted to use this app. Your device profile has been locked out by the database administrator.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textDim,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Button(
                    onClick = {
                        if (!isChecking) {
                            isChecking = true
                            coroutineScope.launch {
                                val response = DeviceTracker.verifyServerStatus(context)
                                if (response != null) {
                                    isActive = response
                                    DeviceTracker.setDeviceStatus(context, response)
                                }
                                isChecking = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            color = colors.bg,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Recheck",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recheck Connection", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
