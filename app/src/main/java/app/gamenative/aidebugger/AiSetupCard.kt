package app.gamenative.aidebugger

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * GameNothing 2.0 Connection Picker & Provider Setup UI (Section 4.1.1 & 4.2).
 *
 * Provides:
 * 1. Primary 1-Tap "Continue with Google (OpenRouter)" OAuth PKCE flow + OpenRouter Model Selector (`openrouter/free` default).
 * 2. Direct Google Gemini Key (`gemini-3.5-flash-lite` default) with 1-tap link to Google AI Studio.
 * 3. Zero-Login Copy & Paste mode (no account or key required).
 * 4. Collapsible Advanced Custom OpenAI-Compatible Endpoint accordion.
 * 5. 1-Tap Disconnect / Clear Key.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSetupCard(
    onConnectionChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeProvider by remember { mutableStateOf(AiKeyStore.getActiveProvider(context)) }
    var isConnected by remember { mutableStateOf(AiKeyStore.hasActiveApiConnection(context)) }

    var openRouterModel by rememberSaveable { mutableStateOf(AiKeyStore.getOpenRouterModel(context)) }
    var customOpenRouterModelInput by rememberSaveable { mutableStateOf("") }
    var showManualCodeInput by rememberSaveable { mutableStateOf(false) }
    var manualCodeOrKeyInput by rememberSaveable { mutableStateOf("") }
    var isAuthenticatingOpenRouter by remember { mutableStateOf(false) }

    var showGeminiSection by rememberSaveable { mutableStateOf(activeProvider == AiKeyStore.AiProvider.GEMINI) }
    var geminiKeyInput by rememberSaveable { mutableStateOf(AiKeyStore.getGeminiKey(context).orEmpty()) }
    var geminiModel by rememberSaveable { mutableStateOf(AiKeyStore.getGeminiModel(context)) }

    var showCustomEndpointAccordion by rememberSaveable { mutableStateOf(activeProvider == AiKeyStore.AiProvider.CUSTOM) }
    var customBaseUrlInput by rememberSaveable { mutableStateOf(AiKeyStore.getCustomBaseUrl(context)) }
    var customModelInput by rememberSaveable { mutableStateOf(AiKeyStore.getCustomModel(context)) }
    var customKeyInput by rememberSaveable { mutableStateOf(AiKeyStore.getCustomKey(context).orEmpty()) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshState() {
        activeProvider = AiKeyStore.getActiveProvider(context)
        isConnected = AiKeyStore.hasActiveApiConnection(context)
        openRouterModel = AiKeyStore.getOpenRouterModel(context)
        geminiModel = AiKeyStore.getGeminiModel(context)
        onConnectionChanged()
    }

    DisposableEffect(Unit) {
        onDispose {
            OpenRouterPkceAuth.cancelActiveServer()
        }
    }

    val glassSurfaceColor = Color(0xFF141824)
    val glassBorderColor = Color.White.copy(alpha = 0.14f)
    val accentCyan = Color(0xFF38BDF8)
    val accentEmerald = Color(0xFF34D399)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = glassSurfaceColor,
        border = BorderStroke(1.dp, glassBorderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Provider & Model Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = if (isConnected) {
                            "Connected: ${AiKeyStore.getActiveModelSummary(context)}"
                        } else {
                            "Free forever · Keys encrypted in Android Keystore"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) accentEmerald else Color(0xFF94A3B8),
                    )
                }

                if (isConnected || activeProvider != AiKeyStore.AiProvider.NONE) {
                    TextButton(
                        onClick = {
                            AiKeyStore.clearAll(context)
                            geminiKeyInput = ""
                            customKeyInput = ""
                            manualCodeOrKeyInput = ""
                            statusMessage = "Cleared saved AI credentials."
                            errorMessage = null
                            refreshState()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFF87171),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Disconnect",
                            color = Color(0xFFF87171),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Status / Error Banner
            if (!statusMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentEmerald.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentEmerald.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = statusMessage.orEmpty(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = accentEmerald,
                    )
                }
            }
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF87171).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFCA5A5),
                    )
                }
            }

            // 1. Primary Option: 1-Tap Continue with Google (OpenRouter)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1C2233),
                border = BorderStroke(
                    1.dp,
                    if (activeProvider == AiKeyStore.AiProvider.OPENROUTER && isConnected) {
                        accentEmerald.copy(alpha = 0.6f)
                    } else {
                        accentCyan.copy(alpha = 0.35f)
                    },
                ),
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
                                text = "1-Tap OpenRouter (Recommended)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Text(
                                text = "Sign in with Google · Auto-routes to free reasoning models (openrouter/free)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accentCyan.copy(alpha = 0.18f),
                        ) {
                            Text(
                                text = "FREE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentCyan,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                if (isAuthenticatingOpenRouter) return@Button
                                isAuthenticatingOpenRouter = true
                                errorMessage = null
                                statusMessage = "Opening OpenRouter sign-in..."
                                scope.launch {
                                    when (val res = OpenRouterPkceAuth.startOneTapLogin(context)) {
                                        is OpenRouterPkceAuth.AuthResult.Connected -> {
                                            isAuthenticatingOpenRouter = false
                                            showManualCodeInput = false
                                            statusMessage = "Connected to OpenRouter!"
                                            refreshState()
                                        }
                                        is OpenRouterPkceAuth.AuthResult.NeedsManualCode -> {
                                            isAuthenticatingOpenRouter = false
                                            showManualCodeInput = true
                                            statusMessage = null
                                            errorMessage = res.reason
                                        }
                                        is OpenRouterPkceAuth.AuthResult.Error -> {
                                            isAuthenticatingOpenRouter = false
                                            showManualCodeInput = true
                                            statusMessage = null
                                            errorMessage = res.message
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentCyan,
                                contentColor = Color(0xFF0A0F1D),
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (isAuthenticatingOpenRouter) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF0A0F1D),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Waiting for Google Sign-In...", fontWeight = FontWeight.Bold)
                            } else {
                                Text(
                                    text = if (!AiKeyStore.getOpenRouterKey(context).isNullOrBlank()) {
                                        "Reconnect OpenRouter"
                                    } else {
                                        "Continue with Google (OpenRouter)"
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showManualCodeInput = !showManualCodeInput },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Key")
                        }
                    }

                    // Manual code / sk-or-... key paste fallback
                    AnimatedVisibility(visible = showManualCodeInput) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualCodeOrKeyInput,
                                onValueChange = { manualCodeOrKeyInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("OpenRouter Auth Code or sk-or-v1-... Key") },
                                singleLine = true,
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        when (val res = OpenRouterPkceAuth.exchangeManualCodeOrKey(context, manualCodeOrKeyInput)) {
                                            is OpenRouterPkceAuth.AuthResult.Connected -> {
                                                statusMessage = "OpenRouter key saved!"
                                                errorMessage = null
                                                showManualCodeInput = false
                                                manualCodeOrKeyInput = ""
                                                refreshState()
                                            }
                                            is OpenRouterPkceAuth.AuthResult.NeedsManualCode -> {
                                                errorMessage = res.reason
                                            }
                                            is OpenRouterPkceAuth.AuthResult.Error -> {
                                                errorMessage = res.message
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Save OpenRouter Code / Key")
                            }
                        }
                    }

                    // OpenRouter Visual Model Selector
                    Text(
                        text = "OpenRouter Model:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCBD5E1),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AiKeyStore.OPENROUTER_MODEL_PRESETS.forEach { preset ->
                            val selected = openRouterModel.equals(preset.id, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    openRouterModel = preset.id
                                    AiKeyStore.setOpenRouterModel(context, preset.id)
                                    if (!AiKeyStore.getOpenRouterKey(context).isNullOrBlank()) {
                                        AiKeyStore.setActiveProvider(context, AiKeyStore.AiProvider.OPENROUTER)
                                    }
                                    refreshState()
                                },
                                label = {
                                    Text(
                                        text = "${preset.label} (${preset.badge})",
                                        fontSize = 11.sp,
                                    )
                                },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }

                    // Custom OpenRouter model override
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customOpenRouterModelInput,
                            onValueChange = { customOpenRouterModelInput = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Or custom OpenRouter model ID (e.g. qwen/qwen3-coder:free)") },
                            singleLine = true,
                        )
                        if (customOpenRouterModelInput.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val trimmed = customOpenRouterModelInput.trim()
                                    openRouterModel = trimmed
                                    AiKeyStore.setOpenRouterModel(context, trimmed)
                                    customOpenRouterModelInput = ""
                                    statusMessage = "Selected OpenRouter model: $trimmed"
                                    refreshState()
                                },
                            ) {
                                Text("Use")
                            }
                        }
                    }
                }
            }

            // 2. Secondary Option: Direct Google Gemini Key (gemini-3.5-flash-lite default)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1A2030),
                border = BorderStroke(
                    1.dp,
                    if (activeProvider == AiKeyStore.AiProvider.GEMINI && isConnected) {
                        accentEmerald.copy(alpha = 0.6f)
                    } else {
                        glassBorderColor
                    },
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGeminiSection = !showGeminiSection },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Direct Google Gemini Key",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Text(
                                text = "Free tier via Google AI Studio · Default: gemini-3.5-flash-lite",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Icon(
                            imageVector = if (showGeminiSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                        )
                    }

                    AnimatedVisibility(visible = showGeminiSection) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://aistudio.google.com/app/apikey"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Get Free Gemini Key (Google AI Studio) →")
                            }

                            OutlinedTextField(
                                value = geminiKeyInput,
                                onValueChange = { geminiKeyInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Paste Gemini API Key (AIza...)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AiKeyStore.GEMINI_MODEL_PRESETS.forEach { preset ->
                                    val selected = geminiModel.equals(preset.id, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            geminiModel = preset.id
                                            AiKeyStore.setGeminiModel(context, preset.id)
                                            refreshState()
                                        },
                                        label = { Text("${preset.label} (${preset.badge})", fontSize = 11.sp) },
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (geminiKeyInput.isBlank()) {
                                        errorMessage = "Please paste a valid Google Gemini API key."
                                    } else {
                                        AiKeyStore.saveGeminiKey(context, geminiKeyInput.trim())
                                        AiKeyStore.setGeminiModel(context, geminiModel)
                                        AiKeyStore.setActiveProvider(context, AiKeyStore.AiProvider.GEMINI)
                                        statusMessage = "Google Gemini ($geminiModel) connected!"
                                        errorMessage = null
                                        refreshState()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Save & Use Gemini Key")
                            }
                        }
                    }
                }
            }

            // 3. Zero-Login Mode Selection
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1A2030),
                border = BorderStroke(
                    1.dp,
                    if (activeProvider == AiKeyStore.AiProvider.NONE) {
                        accentCyan.copy(alpha = 0.5f)
                    } else {
                        glassBorderColor
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AiKeyStore.setActiveProvider(context, AiKeyStore.AiProvider.NONE)
                            statusMessage = "Zero-Login Copy & Paste mode active."
                            errorMessage = null
                            refreshState()
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zero-Login Copy & Paste AI Fix",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = "Copy diagnostic prompt into ChatGPT, Claude, or Gemini app and paste the JSON fix back — zero accounts or API keys needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                    }
                    if (activeProvider == AiKeyStore.AiProvider.NONE) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = accentCyan,
                        )
                    }
                }
            }

            HorizontalDivider(color = glassBorderColor)

            // 4. Advanced: Custom OpenAI-Compatible Endpoint Accordion
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomEndpointAccordion = !showCustomEndpointAccordion }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Advanced: Custom OpenAI-Compatible Endpoint",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF94A3B8),
                    )
                    Icon(
                        imageVector = if (showCustomEndpointAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                    )
                }

                AnimatedVisibility(visible = showCustomEndpointAccordion) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customBaseUrlInput,
                            onValueChange = { customBaseUrlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Base URL (e.g. https://api.groq.com/openai/v1)") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Model ID (e.g. llama-3.3-70b-versatile)") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = customKeyInput,
                            onValueChange = { customKeyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key (Bearer token)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                if (customBaseUrlInput.isBlank() || customModelInput.isBlank()) {
                                    errorMessage = "Base URL and Model ID are required for Custom Endpoint."
                                } else {
                                    AiKeyStore.saveCustomEndpoint(
                                        context = context,
                                        baseUrl = customBaseUrlInput.trim(),
                                        modelId = customModelInput.trim(),
                                        apiKey = customKeyInput.trim(),
                                    )
                                    AiKeyStore.setActiveProvider(context, AiKeyStore.AiProvider.CUSTOM)
                                    statusMessage = "Custom endpoint (${customModelInput.trim()}) saved!"
                                    errorMessage = null
                                    refreshState()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save Custom Endpoint")
                        }
                    }
                }
            }
        }
    }
}
