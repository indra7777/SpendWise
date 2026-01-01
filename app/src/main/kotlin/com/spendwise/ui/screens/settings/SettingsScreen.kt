package com.spendwise.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.ui.theme.FinanceColors

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var serverEnabled by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }
    var geminiApiKey by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Notification Access Section
        item {
            SettingsSection(title = "Permissions") {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification Access",
                    description = "Required to capture transaction notifications",
                    trailing = {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Enable")
                        }
                    }
                )
            }
        }

        // Dashboard Server Section
        item {
            SettingsSection(title = "Dashboard Server") {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Wifi,
                        title = "Local Server",
                        description = if (serverEnabled) "Running at $serverUrl" else "Start server to view dashboard in browser",
                        trailing = {
                            Switch(
                                checked = serverEnabled,
                                onCheckedChange = { enabled ->
                                    serverEnabled = enabled
                                    if (enabled) {
                                        serverUrl = "http://192.168.1.100:8080"
                                        // TODO: Start server
                                    } else {
                                        serverUrl = ""
                                        // TODO: Stop server
                                    }
                                }
                            )
                        }
                    )

                    if (serverEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = FinanceColors.Positive.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = FinanceColors.Positive
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Server Running",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = serverUrl,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { /* Copy URL */ }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                                IconButton(onClick = { /* Show QR */ }) {
                                    Icon(Icons.Default.QrCode, contentDescription = "QR Code")
                                }
                            }
                        }
                    }
                }
            }
        }

        // AI Settings Section
        item {
            SettingsSection(title = "AI Configuration") {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Psychology,
                        title = "Local AI (Gemma 3n)",
                        description = "On-device categorization - Private & Offline",
                        trailing = {
                            Switch(
                                checked = true,
                                onCheckedChange = { }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API Key (Optional)") },
                        placeholder = { Text("For advanced insights...") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null)
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Gemini API is used for complex analysis and report generation. Your transaction data remains on device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Budget Settings
        item {
            SettingsSection(title = "Budget") {
                SettingsItem(
                    icon = Icons.Default.AccountBalance,
                    title = "Monthly Budget",
                    description = "₹40,000",
                    trailing = {
                        IconButton(onClick = { /* Edit budget */ }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                )
            }
        }

        // Data Management
        item {
            SettingsSection(title = "Data") {
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "Export Data",
                    description = "Download your transactions as CSV",
                    trailing = {
                        IconButton(onClick = { /* Export */ }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsItem(
                    icon = Icons.Default.FileUpload,
                    title = "Import Statement",
                    description = "Import bank statement or UPI export",
                    trailing = {
                        IconButton(onClick = { /* Import */ }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
            }
        }

        // About
        item {
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "SpendWise",
                    description = "Version 1.0.0",
                    trailing = { }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}
