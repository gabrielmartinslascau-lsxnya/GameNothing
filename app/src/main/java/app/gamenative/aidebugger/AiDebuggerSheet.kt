package app.gamenative.aidebugger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.container.ContainerData
import kotlinx.coroutines.launch

/**
 * Unified, dead-simple 2-click "AI Fix" dialog for GameNothing 2.0-patch4.
 *
 * Strictly 3 structural regions (no multi-step wizard, no walls of settings):
 * 1. Pinned Top Bar: "AI Fix" + small gear icon (OpenRouter/Gemini setup/sign-out) + Close.
 * 2. Scrollable Body (`Modifier.weight(1f)`):
 *    - Optional single-card login prompt (shown only on gear tap or when tapping Online unconnected)
 *    - One short text box: "What's wrong? (e.g. game freezes after 2m)"
 *    - Two primary buttons: Online and Offline
 *    - Two compact offline sub-actions right below: Copy Script and Paste Fix
 *    - Result Card (1-2 line plain-English summary + keyed Old -> New change list)
 * 3. Pinned Bottom Bar: Fix & Run + Undo (visible whenever `lastAiPatchSnapshot != null`).
 */
@Composable
fun AiDebuggerSheet(
    appId: String?,
    config: ContainerData,
    onDismiss: () -> Unit,
    onConfigUpdated: (ContainerData) -> Unit,
    onApplyAndRun: ((ContainerData) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var workingConfig by remember(config) { mutableStateOf(config) }
    var userMessage by rememberSaveable { mutableStateOf("") }

    var diagnosticBundle by remember { mutableStateOf<ContainerDiagnosticBundle?>(null) }
    var isCollectingBundle by remember { mutableStateOf(true) }

    var showProviderCard by rememberSaveable { mutableStateOf(false) }
    var connectionVersion by remember { mutableIntStateOf(0) }

    var isRunningOnline by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf<AiDebuggerResponse?>(null) }

    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedJsonText by rememberSaveable { mutableStateOf("") }

    var statusBanner by remember { mutableStateOf<String?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }

    // Silently auto-bundle everything on Dispatchers.IO in the background
    LaunchedEffect(appId, workingConfig) {
        isCollectingBundle = true
        diagnosticBundle = runCatching {
            ContainerDiagnosticCollector.collect(context, appId, workingConfig)
        }.getOrNull()
        isCollectingBundle = false
    }

    val hasApiConnection = remember(connectionVersion) { AiKeyStore.hasActiveApiConnection(context) }
    val hasUndoSnapshot = AiPatchApplier.hasUndoSnapshot(workingConfig)
    val canFixAndRun = aiResponse?.hasApplicableChanges == true

    val glassBg = Color(0xFF0E121D)
    val cardBg = Color(0xFF171D2E)
    val borderLight = Color.White.copy(alpha = 0.14f)
    val accentCyan = Color(0xFF38BDF8)
    val accentEmerald = Color(0xFF34D399)
    val accentAmber = Color(0xFFFBBF24)

    suspend fun ensureBundle(): ContainerDiagnosticBundle? {
        diagnosticBundle?.let { return it }
        isCollectingBundle = true
        val collected = runCatching {
            ContainerDiagnosticCollector.collect(context, appId, workingConfig)
        }.getOrNull()
        diagnosticBundle = collected
        isCollectingBundle = false
        return collected
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(22.dp),
                color = glassBg,
                border = BorderStroke(1.dp, borderLight),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. PINNED TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = accentCyan,
                            modifier = Modifier.size(20.dp),
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Fix",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            diagnosticBundle?.gameName?.takeIf { it.isNotBlank() }?.let { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        IconButton(
                            onClick = { showProviderCard = !showProviderCard },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "AI Provider Settings",
                                tint = if (showProviderCard || hasApiConnection) accentCyan else Color(0xFF94A3B8),
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close AI Fix",
                                tint = Color.White,
                            )
                        }
                    }

                    HorizontalDivider(color = borderLight)

                    // 2. SCROLLABLE BODY
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!statusBanner.isNullOrBlank()) {
                            item(key = "banner_status") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = accentEmerald.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, accentEmerald.copy(alpha = 0.4f)),
                                ) {
                                    Text(
                                        text = statusBanner.orEmpty(),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accentEmerald,
                                    )
                                }
                            }
                        }

                        if (!errorBanner.isNullOrBlank()) {
                            item(key = "banner_error") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF87171).copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.4f)),
                                ) {
                                    Text(
                                        text = errorBanner.orEmpty(),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFCA5A5),
                                    )
                                }
                            }
                        }

                        // Single-card login / provider prompt (shown on gear tap or Online when unconnected)
                        if (showProviderCard) {
                            item(key = "single_card_provider") {
                                CompactAiLoginCard(
                                    onConnected = { msg ->
                                        connectionVersion++
                                        showProviderCard = false
                                        statusBanner = msg
                                        errorBanner = null
                                    },
                                    onDisconnected = {
                                        connectionVersion++
                                        statusBanner = "Signed out of AI provider."
                                        errorBanner = null
                                    },
                                    onError = { msg ->
                                        errorBanner = msg
                                    },
                                )
                            }
                        }

                        // Short text box + 2 primary buttons (Online / Offline) + compact Copy Script / Paste Fix
                        item(key = "main_controls") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, borderLight),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedTextField(
                                        value = userMessage,
                                        onValueChange = { userMessage = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text(
                                                text = "What's wrong? (e.g. game freezes after 2m)",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        singleLine = true,
                                    )

                                    // Two primary buttons: Online & Offline
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                if (isRunningOnline) return@Button
                                                if (!AiKeyStore.hasActiveApiConnection(context)) {
                                                    showProviderCard = true
                                                    errorBanner = null
                                                    statusBanner = "Sign in with OpenRouter or paste a Gemini key to run Online AI Fix."
                                                    return@Button
                                                }
                                                isRunningOnline = true
                                                errorBanner = null
                                                statusBanner = null
                                                scope.launch {
                                                    val bundle = ensureBundle()
                                                    if (bundle == null) {
                                                        isRunningOnline = false
                                                        errorBanner = "Could not collect container diagnostics."
                                                        return@launch
                                                    }
                                                    val inferred = OfflineDiagnosticsEngine.inferSymptomsFromPrompt(userMessage)
                                                    val result = AiDebuggerClient.runDiagnosis(
                                                        context = context,
                                                        symptoms = inferred,
                                                        userNotes = userMessage,
                                                        bundle = bundle,
                                                    )
                                                    isRunningOnline = false
                                                    result.onSuccess { parsed ->
                                                        aiResponse = parsed
                                                        statusBanner = null
                                                        errorBanner = null
                                                    }.onFailure { err ->
                                                        errorBanner = err.message ?: "Online AI request failed."
                                                    }
                                                }
                                            },
                                            enabled = !isRunningOnline,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentCyan,
                                                contentColor = Color(0xFF0A0F1D),
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            if (isRunningOnline) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color(0xFF0A0F1D),
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Analyzing...", fontWeight = FontWeight.Bold, maxLines = 1)
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Online", fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                errorBanner = null
                                                statusBanner = null
                                                val localResult = OfflineDiagnosticsEngine.analyzeCombined(
                                                    config = workingConfig,
                                                    userMessage = userMessage,
                                                    adaptiveFpsCapEnabled = diagnosticBundle?.adaptiveFpsCapEnabled ?: false,
                                                    gpuRenderer = diagnosticBundle?.gpuRenderer.orEmpty(),
                                                )
                                                aiResponse = localResult
                                            },
                                            enabled = !isRunningOnline,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentEmerald,
                                                contentColor = Color(0xFF08121E),
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Offline", fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }

                                    // Compact offline sub-actions right below: Copy Script & Paste Fix
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val bundle = ensureBundle()
                                                    if (bundle == null) {
                                                        errorBanner = "Still preparing diagnostic script..."
                                                        return@launch
                                                    }
                                                    val inferred = OfflineDiagnosticsEngine.inferSymptomsFromPrompt(userMessage)
                                                    val prompt = AiDebuggerClient.buildCopyablePrompt(
                                                        symptoms = inferred,
                                                        userNotes = userMessage,
                                                        bundle = bundle,
                                                    )
                                                    copyToClipboard(context, "GameNothing AI Fix Script", prompt)
                                                    statusBanner = "Copied diagnostic script. Paste into ChatGPT, Gemini, or Claude, then tap Paste Fix."
                                                    errorBanner = null
                                                }
                                            },
                                            enabled = !isCollectingBundle,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy Script", fontSize = 12.sp, maxLines = 1)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val clipText = readFromClipboard(context)
                                                if (!clipText.isNullOrBlank() && clipText.contains("{")) {
                                                    pastedJsonText = clipText
                                                }
                                                showPasteDialog = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Paste Fix", fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        // Result Card (appears directly below once ready)
                        aiResponse?.let { response ->
                            item(key = "result_card") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = cardBg,
                                    border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.45f)),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = response.diagnosis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                        )

                                        val allowedChanges = response.allowedContainerChanges
                                        val blockedChanges = response.containerChanges.filter { !it.isAllowed }
                                        val allowedEdits = response.allowedGameConfigEdits

                                        if (allowedChanges.isNotEmpty() || allowedEdits.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                allowedChanges.forEach { change ->
                                                    key(change.id) {
                                                        Surface(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = Color(0xFF111624),
                                                            border = BorderStroke(1.dp, borderLight),
                                                        ) {
                                                            Text(
                                                                text = "${change.key}: ${change.oldValue.ifBlank { "(default)" }} -> ${change.newValue}",
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = accentEmerald,
                                                            )
                                                        }
                                                    }
                                                }

                                                allowedEdits.forEach { edit ->
                                                    key(edit.id) {
                                                        Surface(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = Color(0xFF111624),
                                                            border = BorderStroke(1.dp, borderLight),
                                                        ) {
                                                            Text(
                                                                text = "${edit.relativePath} [${edit.section}] ${edit.key} -> ${edit.newValue}",
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = accentCyan,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF111624),
                                                border = BorderStroke(1.dp, borderLight),
                                            ) {
                                                Text(
                                                    text = "No setting changes needed",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF94A3B8),
                                                )
                                            }
                                        }

                                        if (blockedChanges.isNotEmpty()) {
                                            Text(
                                                text = "Skipped ${blockedChanges.size} unsafe/uninstalled suggestion(s).",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF94A3B8),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = borderLight)

                    // 3. PINNED BOTTOM BAR: Fix & Run + Undo (when snapshot exists)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF121726),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (hasUndoSnapshot) {
                                OutlinedButton(
                                    onClick = {
                                        val restored = AiPatchApplier.undoLastPatch(context, appId, workingConfig)
                                        workingConfig = restored
                                        onConfigUpdated(restored)
                                        aiResponse = null
                                        statusBanner = "Restored previous container & .ini settings."
                                        errorBanner = null
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = accentAmber,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Undo",
                                        color = accentAmber,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val response = aiResponse ?: return@Button
                                    val updated = AiPatchApplier.applyPatch(
                                        context = context,
                                        appId = appId,
                                        currentConfig = workingConfig,
                                        response = response,
                                    )
                                    workingConfig = updated
                                    onConfigUpdated(updated)
                                    if (onApplyAndRun != null) {
                                        onApplyAndRun(updated)
                                    } else {
                                        onDismiss()
                                    }
                                },
                                enabled = canFixAndRun,
                                modifier = if (hasUndoSnapshot) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentEmerald,
                                    contentColor = Color(0xFF08121E),
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Fix & Run",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Compact Paste Fix JSON Dialog
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Paste Fix") },
            text = {
                OutlinedTextField(
                    value = pastedJsonText,
                    onValueChange = { pastedJsonText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("Paste AI JSON reply here...") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = AiPatchValidator.parseAndValidate(
                            context = context,
                            appId = appId,
                            rawResponseText = pastedJsonText,
                            currentConfig = workingConfig,
                            installedComponents = diagnosticBundle?.installedComponents,
                        )
                        parsed.onSuccess { response ->
                            aiResponse = response
                            showPasteDialog = false
                            statusBanner = null
                            errorBanner = null
                        }.onFailure { err ->
                            errorBanner = "Invalid AI JSON: ${err.message}"
                            showPasteDialog = false
                        }
                    },
                ) {
                    Text("Load Fix")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Single-card login prompt for Online AI Fix:
 * 1-tap OpenRouter Google Sign-In (`openrouter/free`) or Gemini `gemini-3.5-flash-lite` API key + Sign Out.
 */
@Composable
private fun CompactAiLoginCard(
    onConnected: (String) -> Unit,
    onDisconnected: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isConnected = AiKeyStore.hasActiveApiConnection(context)
    val modelSummary = AiKeyStore.getActiveModelSummary(context)
    var isSigningIn by remember { mutableStateOf(false) }
    var geminiKey by rememberSaveable { mutableStateOf(AiKeyStore.getGeminiKey(context).orEmpty()) }

    DisposableEffect(Unit) {
        onDispose {
            OpenRouterPkceAuth.cancelActiveServer()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A2236),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected) "Connected" else "Connect Online AI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = if (isConnected) modelSummary else "1-tap OpenRouter Google login or Gemini key",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) Color(0xFF34D399) else Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isConnected) {
                    TextButton(
                        onClick = {
                            AiKeyStore.clearAll(context)
                            geminiKey = ""
                            onDisconnected()
                        },
                    ) {
                        Text("Sign Out", color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                }
            }

            Button(
                onClick = {
                    if (isSigningIn) return@Button
                    isSigningIn = true
                    scope.launch {
                        when (val res = OpenRouterPkceAuth.startOneTapLogin(context)) {
                            is OpenRouterPkceAuth.AuthResult.Connected -> {
                                isSigningIn = false
                                onConnected("Connected to OpenRouter!")
                            }
                            is OpenRouterPkceAuth.AuthResult.NeedsManualCode -> {
                                isSigningIn = false
                                onError(res.reason)
                            }
                            is OpenRouterPkceAuth.AuthResult.Error -> {
                                isSigningIn = false
                                onError(res.message)
                            }
                        }
                    }
                },
                enabled = !isSigningIn,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color(0xFF0A0F1D),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF0A0F1D),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Waiting for Google Sign-In...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Continue with Google (OpenRouter)", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Gemini key (gemini-3.5-flash-lite)", fontSize = 12.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )

                Button(
                    onClick = {
                        val trimmed = geminiKey.trim()
                        if (trimmed.isBlank()) {
                            onError("Paste a Gemini API key first.")
                        } else {
                            AiKeyStore.saveGeminiKey(context, trimmed)
                            AiKeyStore.setGeminiModel(context, AiKeyStore.DEFAULT_GEMINI_MODEL)
                            AiKeyStore.setActiveProvider(context, AiKeyStore.AiProvider.GEMINI)
                            onConnected("Connected to Gemini (${AiKeyStore.DEFAULT_GEMINI_MODEL})!")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Get free Gemini key (Google AI Studio)", fontSize = 12.sp)
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun readFromClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
