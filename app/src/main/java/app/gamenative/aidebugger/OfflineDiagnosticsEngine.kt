package app.gamenative.aidebugger

import app.gamenative.performance.PerformanceModeManager
import com.winlator.container.ContainerData
import com.winlator.core.envvars.EnvVars
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class DebuggerSymptom(val id: String, val label: String) {
    LOW_FPS("low_fps", "Low FPS"),
    STUTTERS("stutters", "Stutters / Drops"),
    CRASH_ON_LAUNCH("crash_on_launch", "Crash on Launch"),
    BLACK_SCREEN("black_screen", "Black Screen"),
    DARK_WEIRD_COLORS("dark_weird_colors", "Dark / Weird Colors"),
    AUDIO_CRACKLE("audio_crackle", "Audio Crackle"),
}

data class OfflineQuickFix(
    val id: String,
    val title: String,
    val bottleneckBadge: String,
    val explanation: String,
    val patchResponse: AiDebuggerResponse,
)

/**
 * Instant (0-second, zero-API-call) local heuristic engine that inspects the container
 * configuration, telemetry, and selected symptom chips before making any cloud AI request.
 */
object OfflineDiagnosticsEngine {

    fun analyze(
        config: ContainerData,
        selectedSymptoms: Set<DebuggerSymptom> = emptySet(),
        adaptiveFpsCapEnabled: Boolean = false,
        gpuRenderer: String = "",
        coreCountOverride: Int? = null,
    ): List<OfflineQuickFix> {
        val fixes = mutableListOf<OfflineQuickFix>()
        val perfCpu = PerformanceModeManager.computePerformanceCpuSelection(coreCountOverride)
        val allCores = perfCpu.cpuList
        val bestWow64Cores = perfCpu.cpuListWoW64

        val wantsFpsOrStutterFix = selectedSymptoms.isEmpty() ||
            DebuggerSymptom.LOW_FPS in selectedSymptoms ||
            DebuggerSymptom.STUTTERS in selectedSymptoms

        // 1. Gamescope SDR Dark-Gamma / Black Screen / Compositor Overhead
        if (config.displayServer.equals("gamescope", ignoreCase = true)) {
            val isColorOrBlackSymptom = DebuggerSymptom.DARK_WEIRD_COLORS in selectedSymptoms ||
                DebuggerSymptom.BLACK_SCREEN in selectedSymptoms
            val isNonAdreno = gpuRenderer.isNotBlank() && !gpuRenderer.contains("Adreno", ignoreCase = true)
            if (isColorOrBlackSymptom || isNonAdreno || wantsFpsOrStutterFix) {
                val change = AiContainerChange(
                    id = "offline_gamescope_to_x11",
                    key = "displayServer",
                    oldValue = config.displayServer,
                    newValue = "x11",
                    reason = "Bypass Gamescope SDR dark-gamma shader bug and nested compositor frame pacing overhead.",
                )
                fixes.add(
                    OfflineQuickFix(
                        id = "fix_gamescope_x11",
                        title = "Switch Display Server from Gamescope to X11",
                        bottleneckBadge = if (isColorOrBlackSymptom) "GPU" else "FPS Cap",
                        explanation = "Gamescope applies a broken SDR degamma curve on Android drivers (causing crushed blacks/dark colors) and adds extra compositor overhead.",
                        patchResponse = buildOfflineResponse(
                            bottleneck = if (isColorOrBlackSymptom) "GPU" else "FPS Cap",
                            diagnosis = "Gamescope display server is active, causing SDR dark-gamma color crushing and extra compositor frame overhead. Switching to direct X11 resolves this immediately.",
                            changes = listOf(change),
                        ),
                    ),
                )
            }
        }

        // 2. FPS Limiter / Auto FPS Cap active
        if (config.fpsLimiterEnabled || adaptiveFpsCapEnabled) {
            val change = AiContainerChange(
                id = "offline_disable_fps_limiter",
                key = "fpsLimiterEnabled",
                oldValue = "true",
                newValue = "false",
                reason = "Disable per-container FPS limiter and Adaptive FPS Cap so the game runs uncapped.",
            )
            fixes.add(
                OfflineQuickFix(
                    id = "fix_fps_limiter",
                    title = "Uncap Frame Rate (Disable Auto FPS Cap)",
                    bottleneckBadge = "FPS Cap",
                    explanation = "The per-container FPS limiter / Adaptive FPS Cap is currently throttling your frame rate.",
                    patchResponse = buildOfflineResponse(
                        bottleneck = "FPS Cap",
                        diagnosis = "Your container's Auto FPS Cap / FPS Limiter is enabled, artificially throttling frame output below your hardware capability.",
                        changes = listOf(change),
                    ),
                ),
            )
        }

        // 3. Performance Tuning Mode Not Enabled (1-tap full optimization — respects user FPS cap)
        if (!config.isPerformanceMode && wantsFpsOrStutterFix && DebuggerSymptom.CRASH_ON_LAUNCH !in selectedSymptoms) {
            val changes = listOf(
                AiContainerChange(
                    id = "offline_enable_perf_mode",
                    key = "tuningMode",
                    oldValue = config.tuningMode,
                    newValue = "PERFORMANCE",
                    reason = "Enable X11 direct path, FEX Extreme / Box64 Performance, all 64-bit cores ($allCores), top ≤4 32-bit cores ($bestWow64Cores), and safe Rootless Game Pinning (keeps user FPS cap intact).",
                ),
            )
            fixes.add(
                OfflineQuickFix(
                    id = "fix_enable_performance_mode",
                    title = "Enable Performance Mode",
                    bottleneckBadge = "CPU",
                    explanation = "One-tap switches to X11, FEX Extreme + Box64 Performance, unlocks 64-bit cores ($allCores), caps 32-bit WoW64 to top ≤4 cores ($bestWow64Cores), and enables safe Rootless Game Pinning without touching your FPS cap.",
                    patchResponse = buildOfflineResponse(
                        bottleneck = "CPU",
                        diagnosis = "Performance tuning mode is currently ${config.tuningMode}. Enabling Performance Mode switches to X11 and FEX Extreme, unlocks all 64-bit cores, caps 32-bit cores to ≤4 fastest cores, and pins active game threads without touching your FPS cap.",
                        changes = changes,
                    ),
                ),
            )
        }

        // 4. FEXCore EXTREME Memory-Ordering Race Condition on Crash or Stutter
        if (config.fexcorePreset.equals("EXTREME", ignoreCase = true) &&
            (DebuggerSymptom.CRASH_ON_LAUNCH in selectedSymptoms || DebuggerSymptom.STUTTERS in selectedSymptoms)
        ) {
            val change = AiContainerChange(
                id = "offline_fex_extreme_to_intermediate",
                key = "fexcorePreset",
                oldValue = config.fexcorePreset,
                newValue = "INTERMEDIATE",
                reason = "Restore safer TSO memory ordering for multi-threaded x86_64 engines that race or crash under FEX Extreme.",
            )
            fixes.add(
                OfflineQuickFix(
                    id = "fix_fex_tso_stability",
                    title = "Switch FEXCore Preset from Extreme to Intermediate",
                    bottleneckBadge = "Crash / Launch",
                    explanation = "FEX Extreme disables strict x86 Total Store Ordering (TSO) for speed, which can cause thread race crashes or worker-thread stalls in some Unity/Unreal engines.",
                    patchResponse = buildOfflineResponse(
                        bottleneck = "Crash / Launch",
                        diagnosis = "FEXCore is set to EXTREME, which relaxes x86 memory ordering (TSO) and can trigger multi-thread race crashes or stalls in this game engine.",
                        changes = listOf(change),
                    ),
                ),
            )
        }

        // 5. Partial 64-bit CPU Core Affinity Mask or Over-allocated (>4) 32-bit WoW64 Mask
        val currentCpuList = config.cpuList.trim()
        val currentWow64List = config.cpuListWoW64.trim()
        val wow64CoreCount = currentWow64List.split(",").count { it.trim().toIntOrNull() != null }
        val needsCpu64Fix = currentCpuList.isNotEmpty() && currentCpuList != allCores
        val needsWow64Fix = currentWow64List.isNotEmpty() && (wow64CoreCount > 4 || currentWow64List != bestWow64Cores)
        if (needsCpu64Fix || needsWow64Fix) {
            val changes = mutableListOf<AiContainerChange>()
            if (needsCpu64Fix) {
                changes.add(
                    AiContainerChange(
                        id = "offline_unlock_cpulist",
                        key = "cpuList",
                        oldValue = currentCpuList,
                        newValue = allCores,
                        reason = "Unlock all CPU cores for 64-bit guest threads.",
                    ),
                )
            }
            if (needsWow64Fix) {
                changes.add(
                    AiContainerChange(
                        id = "offline_unlock_cpulist_wow64",
                        key = "cpuListWoW64",
                        oldValue = currentWow64List,
                        newValue = bestWow64Cores,
                        reason = "Assign top ≤4 highest-frequency CPU cores for 32-bit/WoW64 guest threads (never >4 cores).",
                    ),
                )
            }
            fixes.add(
                OfflineQuickFix(
                    id = "fix_unlock_all_cores",
                    title = "Optimize CPU Affinity (64-bit: $allCores · 32-bit: $bestWow64Cores)",
                    bottleneckBadge = "CPU",
                    explanation = "Container CPU affinity is suboptimal (64-bit: '$currentCpuList', 32-bit: '$currentWow64List'). Unlocking all 64-bit cores while capping 32-bit WoW64 to top ≤4 cores prevents both starvation and 32-bit deadlocks.",
                    patchResponse = buildOfflineResponse(
                        bottleneck = "CPU",
                        diagnosis = "CPU core affinity is suboptimal. Unlocking all 64-bit cores ($allCores) while keeping 32-bit WoW64 capped to top ≤4 cores ($bestWow64Cores) maximizes throughput without triggering 32-bit worker deadlocks.",
                        changes = changes,
                    ),
                ),
            )
        }

        // 6. Heavy Debug Logging / HUD Overhead in envVars
        val envVars = EnvVars(config.envVars)
        val dxvkHud = if (envVars.has("DXVK_HUD")) envVars.get("DXVK_HUD") else ""
        val wineDebug = if (envVars.has("WINEDEBUG")) envVars.get("WINEDEBUG") else ""
        val hasHeavyHud = dxvkHud.contains("full", ignoreCase = true) || dxvkHud.length > 20
        val hasHeavyWineDebug = wineDebug.isNotBlank() && !wineDebug.equals("-all", ignoreCase = true)
        if (hasHeavyHud || hasHeavyWineDebug) {
            val changes = mutableListOf<AiContainerChange>()
            if (hasHeavyHud) {
                changes.add(
                    AiContainerChange(
                        id = "offline_clean_dxvk_hud",
                        key = "envVars.DXVK_HUD",
                        oldValue = dxvkHud,
                        newValue = "(remove)",
                        reason = "Remove heavy DXVK_HUD overlay rendering overhead.",
                    ),
                )
            }
            if (hasHeavyWineDebug) {
                changes.add(
                    AiContainerChange(
                        id = "offline_clean_winedebug",
                        key = "envVars.WINEDEBUG",
                        oldValue = wineDebug,
                        newValue = "-all",
                        reason = "Silence synchronous Wine debug logging I/O stalls.",
                    ),
                )
            }
            fixes.add(
                OfflineQuickFix(
                    id = "fix_heavy_debug_env",
                    title = "Disable Heavy Debug Logging & HUD Overhead",
                    bottleneckBadge = "CPU",
                    explanation = "Active verbose WINEDEBUG or full DXVK_HUD environment variables cause synchronous disk I/O and frame time spikes.",
                    patchResponse = buildOfflineResponse(
                        bottleneck = "CPU",
                        diagnosis = "Verbose debug environment variables are active in this container, causing per-frame logging and HUD overhead.",
                        changes = changes,
                    ),
                ),
            )
        }

        // 7. Audio Crackle / PulseAudio Low Latency
        if (DebuggerSymptom.AUDIO_CRACKLE in selectedSymptoms &&
            (!config.audioDriver.equals("pulse", ignoreCase = true) || !config.pulseaudioLowLatency)
        ) {
            val changes = mutableListOf<AiContainerChange>()
            if (!config.audioDriver.equals("pulse", ignoreCase = true)) {
                changes.add(
                    AiContainerChange(
                        id = "offline_audio_pulse",
                        key = "audioDriver",
                        oldValue = config.audioDriver,
                        newValue = "pulse",
                        reason = "Switch to PulseAudio backend for smoother buffer scheduling.",
                    ),
                )
            }
            if (!config.pulseaudioLowLatency) {
                changes.add(
                    AiContainerChange(
                        id = "offline_pulse_low_latency",
                        key = "pulseaudioLowLatency",
                        oldValue = "false",
                        newValue = "true",
                        reason = "Enable low-latency PulseAudio quantum tuning to eliminate audio buffer crackle.",
                    ),
                )
            }
            fixes.add(
                OfflineQuickFix(
                    id = "fix_audio_crackle",
                    title = "Enable PulseAudio Low-Latency Mode",
                    bottleneckBadge = "CPU",
                    explanation = "Switches audio backend to PulseAudio with low-latency buffer tuning to prevent audio underruns and crackling.",
                    patchResponse = buildOfflineResponse(
                        bottleneck = "CPU",
                        diagnosis = "Audio crackling under emulation is typically caused by ALSA buffer underruns when FEX/Box64 JIT compiles shaders. Switching to PulseAudio low-latency mode stabilizes audio scheduling.",
                        changes = changes,
                    ),
                ),
            )
        }

        return fixes
    }

    /**
     * Infers structured symptom flags from a short free-text description in the 2-click AI Fix UI.
     */
    fun inferSymptomsFromPrompt(userMessage: String): Set<DebuggerSymptom> {
        val text = userMessage.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return setOf(DebuggerSymptom.LOW_FPS, DebuggerSymptom.STUTTERS)

        val inferred = mutableSetOf<DebuggerSymptom>()
        if (text.contains("fps") || text.contains("lag") || text.contains("slow") || text.contains("choppy")) {
            inferred.add(DebuggerSymptom.LOW_FPS)
        }
        if (text.contains("stutter") || text.contains("drop") || text.contains("freeze") || text.contains("hang") || text.contains("lock")) {
            inferred.add(DebuggerSymptom.STUTTERS)
        }
        if (text.contains("crash") || text.contains("close") || text.contains("exit") || text.contains("launch") || text.contains("boot") || text.contains("freeze")) {
            inferred.add(DebuggerSymptom.CRASH_ON_LAUNCH)
        }
        if (text.contains("black")) {
            inferred.add(DebuggerSymptom.BLACK_SCREEN)
        }
        if (text.contains("dark") || text.contains("color") || text.contains("gamma") || text.contains("washed")) {
            inferred.add(DebuggerSymptom.DARK_WEIRD_COLORS)
        }
        if (text.contains("audio") || text.contains("sound") || text.contains("crackle") || text.contains("no sound") || text.contains("mute")) {
            inferred.add(DebuggerSymptom.AUDIO_CRACKLE)
        }
        if (inferred.isEmpty()) {
            inferred.add(DebuggerSymptom.LOW_FPS)
        }
        return inferred
    }

    /**
     * Runs instant offline heuristics for the 2-click AI Fix screen and merges non-conflicting
     * local fixes into a single ready-to-apply [AiDebuggerResponse] (never disables user FPS cap
     * unless the user explicitly mentions an FPS cap/limit).
     */
    fun analyzeCombined(
        config: ContainerData,
        userMessage: String = "",
        adaptiveFpsCapEnabled: Boolean = false,
        gpuRenderer: String = "",
        coreCountOverride: Int? = null,
    ): AiDebuggerResponse {
        val symptoms = inferSymptomsFromPrompt(userMessage)
        val userMentionedFpsCap = userMessage.contains("cap", ignoreCase = true) ||
            userMessage.contains("limit", ignoreCase = true)

        val rawFixes = analyze(
            config = config,
            selectedSymptoms = symptoms,
            adaptiveFpsCapEnabled = adaptiveFpsCapEnabled,
            gpuRenderer = gpuRenderer,
            coreCountOverride = coreCountOverride,
        ).filter { fix ->
            // Respect user's intentional FPS cap unless they explicitly asked about FPS caps.
            fix.id != "fix_fps_limiter" || userMentionedFpsCap
        }

        if (rawFixes.isEmpty()) {
            return buildOfflineResponse(
                bottleneck = "CPU",
                diagnosis = "No obvious local misconfigurations found. Tap Online or Copy Script for deeper AI log analysis.",
                changes = emptyList(),
            )
        }

        // Deduplicate changes by key (prefer explicit granular fixes over broad tuningMode if crash/freeze),
        // then filter no-ops and resolve tuningMode + managed-field overlaps consistently.
        val mergedChanges = linkedMapOf<String, AiContainerChange>()
        val isCrashOrFreeze = DebuggerSymptom.CRASH_ON_LAUNCH in symptoms
        for (fix in rawFixes) {
            if (isCrashOrFreeze && fix.id == "fix_enable_performance_mode") continue
            for (change in fix.patchResponse.containerChanges) {
                if (!mergedChanges.containsKey(change.key)) {
                    mergedChanges[change.key] = change
                }
            }
        }

        val resolvedChanges = AiPatchValidator.resolveAndFilterChanges(
            changes = mergedChanges.values.toList(),
            currentConfig = config,
            coreCountOverride = coreCountOverride,
        )

        val summary = if (resolvedChanges.isEmpty()) {
            "Container looks clean locally. Tap Online or Copy Script for deeper AI log analysis."
        } else {
            rawFixes.take(2).joinToString(" ") { it.title + "." }
        }

        return buildOfflineResponse(
            bottleneck = rawFixes.first().bottleneckBadge,
            diagnosis = summary,
            changes = resolvedChanges,
        )
    }

    private fun buildOfflineResponse(
        bottleneck: String,
        diagnosis: String,
        changes: List<AiContainerChange>,
    ): AiDebuggerResponse {
        val raw = JSONObject().apply {
            put("bottleneck", bottleneck)
            put("diagnosis", diagnosis)
            put(
                "containerChanges",
                JSONArray().apply {
                    changes.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("key", c.key)
                                put("oldValue", c.oldValue)
                                put("newValue", c.newValue)
                                put("reason", c.reason)
                            },
                        )
                    }
                },
            )
            put("gameConfigEdits", JSONArray())
            put("manualTips", JSONArray())
        }
        return AiDebuggerResponse(
            bottleneck = bottleneck,
            diagnosis = diagnosis,
            containerChanges = changes,
            gameConfigEdits = emptyList(),
            manualTips = emptyList(),
            rawJson = raw.toString(2),
        )
    }
}
