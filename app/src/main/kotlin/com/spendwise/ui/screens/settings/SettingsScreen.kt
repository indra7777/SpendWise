package com.spendwise.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.spendwise.data.repository.AuthState
import com.spendwise.ui.theme.FinanceColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton

@Composable
fun SettingsScreen(
    importViewModel: ImportViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var serverEnabled by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }
    var geminiApiKey by remember { mutableStateOf("") }

    val importState by importViewModel.uiState.collectAsState()
    val authState by settingsViewModel.authState.collectAsState()
    val exportState by settingsViewModel.exportState.collectAsState()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importViewModel.importFile(it) }
    }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { settingsViewModel.exportData(it) }
    }

    // Check SMS permission status (reactive) - declared first so it can be used in launcher callback
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // SMS permission launcher
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("SettingsScreen", "SMS permission result: $isGranted")
        if (isGranted) {
            hasSmsPermission = true
            importViewModel.checkSmsAvailability()
            importViewModel.importFromSms()
        }
    }

    // Refresh SMS count when screen is shown and permission is granted
    LaunchedEffect(Unit) {
        // Re-check permission on every screen launch
        hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        android.util.Log.d("SettingsScreen", "SMS Permission check: $hasSmsPermission")

        if (hasSmsPermission) {
            android.util.Log.d("SettingsScreen", "Calling checkSmsAvailability()")
            importViewModel.checkSmsAvailability()
        }
    }

    // Import Progress Dialog
    if (importState.isImporting) {
        ImportProgressDialog(
            progress = importState.progress,
            message = importState.progressMessage
        )
    }

    // Export Result Dialogs
    if (exportState is ExportState.Success) {
        AlertDialog(
            onDismissRequest = { settingsViewModel.resetExportState() },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = FinanceColors.Positive) },
            title = { Text("Export Complete") },
            text = { Text("${(exportState as ExportState.Success).count} transactions exported successfully.") },
            confirmButton = { Button(onClick = { settingsViewModel.resetExportState() }) { Text("OK") } }
        )
    }
    if (exportState is ExportState.Error) {
        AlertDialog(
            onDismissRequest = { settingsViewModel.resetExportState() },
            icon = { Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Export Failed") },
            text = { Text((exportState as ExportState.Error).message) },
            confirmButton = { Button(onClick = { settingsViewModel.resetExportState() }) { Text("OK") } }
        )
    }
    if (exportState is ExportState.Exporting) {
        Dialog(onDismissRequest = {}) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Exporting data...")
                }
            }
        }
    }

    // PDF Password Dialog
    if (importState.showPasswordDialog) {
        PdfPasswordDialog(
            bankType = importState.detectedBank ?: BankType.UNKNOWN,
            onSubmit = { password -> importViewModel.importPdfWithPassword(password) },
            onDismiss = { importViewModel.dismissPasswordDialog() }
        )
    }

    // Import Result Dialog
    if (importState.showResultDialog && importState.result != null) {
        ImportResultDialog(
            result = importState.result!!,
            onDismiss = { importViewModel.dismissResultDialog() }
        )
    }

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

        // Account Section
        item {
            SettingsSection(title = "Account") {
                when (val state = authState) {
                    is AuthState.Loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Loading...")
                        }
                    }
                    is AuthState.NotAuthenticated -> {
                        Column {
                            Text(
                                text = "Sign in to backup and sync your data across devices",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    (context as? Activity)?.let { activity ->
                                        settingsViewModel.signInWithGoogle(activity)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = ButtonDefaults.outlinedButtonBorder
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign in with Google")
                            }
                        }
                    }
                    is AuthState.Authenticated -> {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.avatarUrl != null) {
                                        AsyncImage(
                                            model = state.avatarUrl,
                                            contentDescription = "Profile picture",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = state.name.firstOrNull()?.uppercase() ?: "U",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.name.ifEmpty { "User" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = state.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { settingsViewModel.signOut() }) {
                                    Icon(
                                        Icons.Default.Logout,
                                        contentDescription = "Sign out",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = FinanceColors.Positive.copy(alpha = 0.1f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = FinanceColors.Positive,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Data backup enabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FinanceColors.Positive
                                    )
                                }
                            }
                        }
                    }
                    is AuthState.Error -> {
                        Column {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    (context as? Activity)?.let { activity ->
                                        settingsViewModel.signInWithGoogle(activity)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }

        // Notification Access Section (Google Play & DPDP Compliance - Prominent Disclosure)
        item {
            SettingsSection(title = "Permissions") {
                Column {
                    // PROMINENT DISCLOSURE - Required by Google Play for Notification Listener
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Why we need notification access",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "SpendWise uses notification access ONLY to:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Detect UPI payment notifications (PhonePe, GPay, etc.)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Extract transaction amount and merchant name only",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Auto-categorize your expenses",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "We DO NOT read personal messages, emails, or other notifications. Only financial keywords (₹, paid, debited, etc.) are processed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                        IconButton(onClick = { 
                            exportLauncher.launch("spendwise_export_${System.currentTimeMillis()}.csv")
                        }) {
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
                        IconButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf(
                                    "application/pdf",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "text/plain",
                                    "*/*"
                                ))
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Supported: PDF statements (SBI, HDFC, ICICI, Axis, PhonePe) - password-protected supported, CSV exports",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // SMS Import Section
                SettingsItem(
                    icon = Icons.Default.Sms,
                    title = "Import from SMS",
                    description = when {
                        !hasSmsPermission -> "Grant SMS permission to scan bank messages"
                        importState.smsCount > 0 -> "${importState.smsCount} bank SMS found"
                        else -> "No bank SMS found"
                    },
                    trailing = {
                        if (importState.isImportingSms) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (!hasSmsPermission) {
                            Button(
                                onClick = {
                                    smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                }
                            ) {
                                Text("Grant")
                            }
                        } else {
                            Button(
                                onClick = { importViewModel.importFromSms() },
                                enabled = importState.smsCount > 0
                            ) {
                                Text("Import")
                            }
                        }
                    }
                )

                if (importState.isImportingSms) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column {
                        LinearProgressIndicator(
                            progress = { importState.smsImportProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = importState.smsImportMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Reads bank SMS (HDFC, ICICI, SBI, Axis, etc.) to import past transactions. Requires SMS permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Privacy & Legal (DPDP Compliance)
        item {
            PrivacyLegalSection(
                settingsViewModel = settingsViewModel
            )
        }

        // About
        item {
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "SpendWise",
                    description = "Version 1.1.0 - DPDP Compliant",
                    trailing = { }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * DPDP Compliance Section:
 * - Right to Erasure (Delete My Data)
 * - Grievance Redressal (Contact info)
 * - Privacy Policy
 */
@Composable
fun PrivacyLegalSection(
    settingsViewModel: SettingsViewModel
) {
    val deleteDataState by settingsViewModel.deleteDataState.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    "Delete All Data?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("This will permanently delete:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• All transactions")
                    Text("• All insights and analytics")
                    Text("• Your account (if signed in)")
                    Text("• All cloud backups")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "This action cannot be undone.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsViewModel.deleteAllData()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Success Dialog
    if (deleteDataState is DeleteDataState.Success) {
        AlertDialog(
            onDismissRequest = { settingsViewModel.resetDeleteState() },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = FinanceColors.Positive
                )
            },
            title = { Text("Data Deleted") },
            text = { Text("All your data has been permanently deleted from this device.") },
            confirmButton = {
                Button(onClick = { settingsViewModel.resetDeleteState() }) {
                    Text("OK")
                }
            }
        )
    }

    SettingsSection(title = "Privacy & Legal") {
        Column {
            // Privacy Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DPDP Act 2023 Compliant - Your data is processed locally and never shared without consent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delete My Data (Right to Erasure)
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = "Delete My Data",
                description = "Permanently delete all your data",
                trailing = {
                    if (deleteDataState is DeleteDataState.Deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    }
                }
            )

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Grievance Officer (DPDP Requirement)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactSupport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Grievance Officer",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "For data protection concerns",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Email: indraprakashgottipati61@gmail.com",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Response time: Within 30 days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Privacy Policy Link
            SettingsItem(
                icon = Icons.Default.Policy,
                title = "Privacy Policy",
                description = "How we handle your data",
                trailing = {
                    IconButton(onClick = { /* Open privacy policy URL */ }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open")
                    }
                }
            )
        }
    }
}

@Composable
fun ImportProgressDialog(
    progress: Float,
    message: String
) {
    Dialog(onDismissRequest = { }) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Importing Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ImportResultDialog(
    result: ImportResultSummary,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.success) FinanceColors.Positive else FinanceColors.Negative,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (result.success) "Import Complete" else "Import Failed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Format: ${result.format}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (result.success) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            value = result.totalParsed.toString(),
                            label = "Parsed"
                        )
                        StatItem(
                            value = result.totalImported.toString(),
                            label = "Imported",
                            color = FinanceColors.Positive
                        )
                        StatItem(
                            value = result.totalSkipped.toString(),
                            label = "Skipped"
                        )
                    }
                }

                if (result.errors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = FinanceColors.Warning.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Warnings:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = FinanceColors.Warning
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            result.errors.forEach { error ->
                                Text(
                                    text = "• $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

@Composable
fun PdfPasswordDialog(
    bankType: BankType,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Password Protected PDF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Detected: ${bankType.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password hint card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Password Format",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = bankType.passwordHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        hasError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter Password") },
                    placeholder = { Text("PDF password...") },
                    singleLine = true,
                    isError = hasError,
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null)
                    }
                )

                if (hasError) {
                    Text(
                        text = "Incorrect password. Please try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (password.isNotBlank()) {
                                onSubmit(password)
                            } else {
                                hasError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = password.isNotBlank()
                    ) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}
