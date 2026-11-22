package app.gamenative.aidebugger

import com.winlator.container.ContainerData
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiDebuggerEngineTest {

    @Test
    fun validatorStripsFencesAndRejectsDisallowedKeysOrUninstalledVersions() {
        val currentConfig = ContainerData(
            displayServer = "gamescope",
            fexcorePreset = "INTERMEDIATE",
            wineVersion = "proton-9.0-arm64ec",
        )
        val installed = InstalledComponentsSnapshot(
            wine = listOf("wine-9.0"),
            proton = listOf("proton-9.0-arm64ec"),
            dxvk = listOf("2.4.1"),
            vkd3d = listOf("2.12"),
            fexcore = listOf("2508"),
            box64 = listOf("0.3.2"),
            wowBox64 = listOf("0.3.2"),
            wrapper = emptyList(),
            installedDrivers = listOf("System", "turnip-25.1.0"),
        )

        val rawResponse = """
            ```json
            {
              "bottleneck": "GPU",
              "diagnosis": "Gamescope is causing SDR color crush and frame overhead.",
              "containerChanges": [
                {
                  "key": "displayServer",
                  "oldValue": "gamescope",
                  "newValue": "x11",
                  "reason": "Switch to X11 direct rendering."
                },
                {
                  "key": "fexcoreVersion",
                  "oldValue": "2508",
                  "newValue": "9999-nonexistent",
                  "reason": "Uninstalled FEX version."
                },
                {
                  "key": "evilShellCommand",
                  "oldValue": "",
                  "newValue": "rm -rf /",
                  "reason": "Disallowed key."
                }
              ],
              "gameConfigEdits": [
                {
                  "relativePath": "../../etc/passwd",
                  "section": "Main",
                  "key": "hack",
                  "newValue": "1",
                  "reason": "Path traversal attempt."
                }
              ],
              "manualTips": ["Lower shadow resolution in game menu."]
            }
            ```
        """.trimIndent()

        val result = AiPatchValidator.parseAndValidate(
            context = null,
            appId = null,
            rawResponseText = rawResponse,
            currentConfig = currentConfig,
            installedComponents = installed,
        )

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("GPU", parsed.bottleneck)
        assertEquals(3, parsed.containerChanges.size)
        assertEquals(1, parsed.allowedContainerChanges.size)
        assertEquals("displayServer", parsed.allowedContainerChanges.first().key)
        assertEquals("x11", parsed.allowedContainerChanges.first().newValue)
        assertFalse(parsed.containerChanges[1].isAllowed)
        assertFalse(parsed.containerChanges[2].isAllowed)
        assertTrue(parsed.allowedGameConfigEdits.isEmpty())
        assertFalse(parsed.gameConfigEdits.first().isAllowed)
    }

    @Test
    fun applierAppliesAndUndoesContainerAndIniFileChanges() {
        val tempDir = Files.createTempDirectory("gamenothing_ai_test").toFile()
        try {
            val iniFile = File(tempDir, "Engine.ini")
            iniFile.writeText(
                """
                [/Script/Engine.RendererSettings]
                r.ShadowQuality=3
                r.MotionBlurQuality=4
                """.trimIndent(),
            )

            val initial = ContainerData(
                displayServer = "gamescope",
                fexcorePreset = "COMPATIBILITY",
                fpsLimiterEnabled = true,
            )

            val response = AiDebuggerResponse(
                bottleneck = "CPU",
                diagnosis = "Switch to X11 and lower shadow quality.",
                containerChanges = listOf(
                    AiContainerChange(
                        id = "c1",
                        key = "displayServer",
                        oldValue = "gamescope",
                        newValue = "x11",
                        reason = "Use X11",
                    ),
                    AiContainerChange(
                        id = "c2",
                        key = "fexcorePreset",
                        oldValue = "COMPATIBILITY",
                        newValue = "EXTREME",
                        reason = "Use FEX Extreme",
                    ),
                ),
                gameConfigEdits = listOf(
                    AiGameConfigEdit(
                        id = "e1",
                        relativePath = "Engine.ini",
                        section = "/Script/Engine.RendererSettings",
                        key = "r.ShadowQuality",
                        newValue = "1",
                        reason = "Lower shadows",
                        canonicalFile = iniFile,
                        isAllowed = true,
                    ),
                ),
                manualTips = emptyList(),
                rawJson = "{}",
            )

            val patched = AiPatchApplier.applyPatch(
                context = null,
                appId = null,
                currentConfig = initial,
                response = response,
            )

            assertEquals("x11", patched.displayServer)
            assertEquals("EXTREME", patched.fexcorePreset)
            assertTrue(AiPatchApplier.hasUndoSnapshot(patched))
            assertTrue(iniFile.readText().contains("r.ShadowQuality=1"))
            val backupFile = File(tempDir, "Engine.ini.gamenothing.bak")
            assertTrue(backupFile.exists())

            val undone = AiPatchApplier.undoLastPatch(
                context = null,
                appId = null,
                currentConfig = patched,
            )

            assertEquals("gamescope", undone.displayServer)
            assertEquals("COMPATIBILITY", undone.fexcorePreset)
            assertFalse(AiPatchApplier.hasUndoSnapshot(undone))
            assertTrue(iniFile.readText().contains("r.ShadowQuality=3"))
            assertFalse(backupFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun offlineDiagnosticsEngineFindsGamescopeAndFpsLimiterAndFexExtremeCrash() {
        val config = ContainerData(
            displayServer = "gamescope",
            fexcorePreset = "EXTREME",
            fpsLimiterEnabled = true,
            cpuList = "0,1",
            cpuListWoW64 = "0,1",
        )

        val fixes = OfflineDiagnosticsEngine.analyze(
            config = config,
            selectedSymptoms = setOf(DebuggerSymptom.CRASH_ON_LAUNCH, DebuggerSymptom.DARK_WEIRD_COLORS),
            coreCountOverride = 8,
        )

        val fixIds = fixes.map { it.id }.toSet()
        assertTrue("fix_gamescope_x11" in fixIds)
        assertTrue("fix_fps_limiter" in fixIds)
        assertTrue("fix_fex_tso_stability" in fixIds)
        assertTrue("fix_unlock_all_cores" in fixIds)
    }

    @Test
    fun validatorRejectsMoreThan4CoresFor32BitCpuListWoW64() {
        val currentConfig = ContainerData(cpuListWoW64 = "0,1,2,3")
        val rawResponse = """
            {
              "bottleneck": "CPU",
              "diagnosis": "Test 32-bit core limit",
              "containerChanges": [
                {
                  "key": "cpuListWoW64",
                  "oldValue": "0,1,2,3",
                  "newValue": "0,1,2,3,4,5,6,7",
                  "reason": "Too many 32-bit cores"
                },
                {
                  "key": "cpuListWoW64",
                  "oldValue": "0,1,2,3",
                  "newValue": "4,5,6,7",
                  "reason": "Top 4 cores"
                }
              ],
              "gameConfigEdits": [],
              "manualTips": []
            }
        """.trimIndent()

        val parsed = AiPatchValidator.parseAndValidate(
            context = null,
            appId = null,
            rawResponseText = rawResponse,
            currentConfig = currentConfig,
            installedComponents = null,
        ).getOrThrow()

        assertFalse(parsed.containerChanges[0].isAllowed)
        assertTrue(parsed.containerChanges[1].isAllowed)
    }

    @Test
    fun combinedOfflineFixRespectsUserFpsCapAndHandlesFreezePrompt() {
        val config = ContainerData(
            displayServer = "gamescope",
            fexcorePreset = "EXTREME",
            fpsLimiterEnabled = true,
            cpuList = "0,1,2,3,4,5,6,7",
            cpuListWoW64 = "0,1,2,3,4,5,6,7",
        )

        val response = OfflineDiagnosticsEngine.analyzeCombined(
            config = config,
            userMessage = "game freezes after 2m",
            coreCountOverride = 8,
        )

        val keys = response.containerChanges.map { it.key }.toSet()
        assertTrue("displayServer" in keys)
        assertTrue("fexcorePreset" in keys)
        assertTrue("cpuListWoW64" in keys)
        assertFalse("fpsLimiterEnabled" in keys)
        assertEquals(
            "INTERMEDIATE",
            response.containerChanges.first { it.key == "fexcorePreset" }.newValue,
        )
    }

    @Test
    fun validatorFiltersNoOpDiffsIncludingIdenticalAndReorderedCoreMasks() {
        val currentConfig = ContainerData(
            displayServer = "x11",
            fexcorePreset = "INTERMEDIATE",
            cpuList = "0,1,2,3,4,5,6,7",
            cpuListWoW64 = "4,5,6,7",
            fpsLimiterEnabled = true,
            envVars = "WINEDEBUG=-all",
        )

        val rawResponse = """
            {
              "bottleneck": "CPU",
              "diagnosis": "Current container settings are already optimal for this workload.",
              "containerChanges": [
                {
                  "key": "cpuList",
                  "oldValue": "0,1,2,3,4,5,6,7",
                  "newValue": " 7, 6, 5, 4, 3, 2, 1, 0 ",
                  "reason": "Use all 8 cores"
                },
                {
                  "key": "cpuListWoW64",
                  "oldValue": "0,1,2,3",
                  "newValue": "7, 5, 6, 4",
                  "reason": "Top 4 cores (matches live value)"
                },
                {
                  "key": "displayServer",
                  "oldValue": "gamescope",
                  "newValue": "X11",
                  "reason": "Already x11"
                },
                {
                  "key": "fexcorePreset",
                  "oldValue": "EXTREME",
                  "newValue": "intermediate",
                  "reason": "Case-insensitive no-op"
                },
                {
                  "key": "envVars.WINEDEBUG",
                  "oldValue": "",
                  "newValue": "-all",
                  "reason": "Already -all"
                },
                {
                  "key": "envVars.DXVK_HUD",
                  "oldValue": "full",
                  "newValue": "(remove)",
                  "reason": "Already absent"
                }
              ],
              "gameConfigEdits": [],
              "manualTips": []
            }
        """.trimIndent()

        val parsed = AiPatchValidator.parseAndValidate(
            context = null,
            appId = null,
            rawResponseText = rawResponse,
            currentConfig = currentConfig,
            installedComponents = null,
            coreCountOverride = 8,
        ).getOrThrow()

        assertTrue("All no-op diffs must be dropped", parsed.allowedContainerChanges.isEmpty())
        assertTrue("No-ops must not appear in containerChanges at all", parsed.containerChanges.isEmpty())
        assertFalse("Fix & Run must be disabled when no real changes remain", parsed.hasApplicableChanges)
        assertTrue(parsed.diagnosis.contains("already optimal"))
    }

    @Test
    fun validatorDeduplicatesManagedFieldsMatchingTuningModeAndAppliesTuningModeFirst() {
        val freqs = mapOf(
            0 to 1_800_000L, 1 to 1_800_000L, 2 to 1_800_000L,
            3 to 2_400_000L, 4 to 2_400_000L, 5 to 2_400_000L, 6 to 2_400_000L,
            7 to 2_800_000L,
        )
        val currentConfig = ContainerData(
            displayServer = "gamescope",
            fexcorePreset = "COMPATIBILITY",
            box64Preset = "STABILITY",
            cpuList = "0,1,2,3",
            cpuListWoW64 = "0,1,2,3",
            rootlessGamePinningEnabled = false,
            tuningMode = "OFF",
            envVars = "DXVK_HUD=full",
        )

        val rawResponse = """
            {
              "bottleneck": "CPU",
              "diagnosis": "Enable Performance tuning and strip heavy DXVK HUD overlay.",
              "containerChanges": [
                {
                  "key": "envVars.DXVK_HUD",
                  "oldValue": "full",
                  "newValue": "(remove)",
                  "reason": "Remove HUD overhead"
                },
                {
                  "key": "displayServer",
                  "oldValue": "gamescope",
                  "newValue": "x11",
                  "reason": "Redundant with PERFORMANCE"
                },
                {
                  "key": "fexcorePreset",
                  "oldValue": "COMPATIBILITY",
                  "newValue": "EXTREME",
                  "reason": "Redundant with PERFORMANCE"
                },
                {
                  "key": "cpuList",
                  "oldValue": "0,1,2,3",
                  "newValue": "0,1,2,3,4,5,6,7",
                  "reason": "Redundant with PERFORMANCE"
                },
                {
                  "key": "cpuListWoW64",
                  "oldValue": "0,1,2,3",
                  "newValue": "4,5,6,7",
                  "reason": "Redundant with PERFORMANCE"
                },
                {
                  "key": "tuningMode",
                  "oldValue": "OFF",
                  "newValue": "PERFORMANCE",
                  "reason": "Apply full performance profile"
                }
              ],
              "gameConfigEdits": [],
              "manualTips": []
            }
        """.trimIndent()

        val parsed = AiPatchValidator.parseAndValidate(
            context = null,
            appId = null,
            rawResponseText = rawResponse,
            currentConfig = currentConfig,
            installedComponents = null,
            coreCountOverride = 8,
            coreFreqsKhz = freqs,
        ).getOrThrow()

        // Only tuningMode (first) and non-managed envVars.DXVK_HUD should remain
        assertEquals(listOf("tuningMode", "envVars.DXVK_HUD"), parsed.allowedContainerChanges.map { it.key })

        // Applying must apply tuningMode first (updating snapshot) + remaining non-managed key
        val patched = AiPatchApplier.applyPatch(
            context = null,
            appId = null,
            currentConfig = currentConfig,
            response = parsed,
            coreCountOverride = 8,
            coreFreqsKhz = freqs,
        )
        assertEquals("PERFORMANCE", patched.tuningMode)
        assertTrue(patched.tuningModeSnapshot.isNotBlank())
        assertEquals("x11", patched.displayServer)
        assertEquals("EXTREME", patched.fexcorePreset)
        assertEquals("0,1,2,3,4,5,6,7", patched.cpuList)
        assertEquals("4,5,6,7", patched.cpuListWoW64)
        assertTrue(patched.rootlessGamePinningEnabled)
        assertFalse(patched.envVars.contains("DXVK_HUD=full"))

        // Undo must cleanly restore both tuningMode, all managed fields, and envVars
        val undone = AiPatchApplier.undoLastPatch(
            context = null,
            appId = null,
            currentConfig = patched,
        )
        assertEquals("OFF", undone.tuningMode)
        assertEquals("", undone.tuningModeSnapshot)
        assertEquals("gamescope", undone.displayServer)
        assertEquals("COMPATIBILITY", undone.fexcorePreset)
        assertEquals("0,1,2,3", undone.cpuList)
        assertEquals("0,1,2,3", undone.cpuListWoW64)
        assertFalse(undone.rootlessGamePinningEnabled)
        assertTrue(undone.envVars.contains("DXVK_HUD=full"))
    }

    @Test
    fun validatorDropsTuningModeWhenCustomManagedFieldContradictsIt() {
        val freqs = mapOf(
            0 to 1_800_000L, 1 to 1_800_000L, 2 to 1_800_000L,
            3 to 2_400_000L, 4 to 2_400_000L, 5 to 2_400_000L, 6 to 2_400_000L,
            7 to 2_800_000L,
        )
        val currentConfig = ContainerData(
            displayServer = "gamescope",
            fexcorePreset = "INTERMEDIATE",
            cpuList = "0,1,2,3",
            cpuListWoW64 = "0,1,2,3",
            tuningMode = "OFF",
        )

        val rawResponse = """
            {
              "bottleneck": "CPU",
              "diagnosis": "Pin 64-bit threads to the 5 Big+Prime cores (3,4,5,6,7) and use X11.",
              "containerChanges": [
                {
                  "key": "tuningMode",
                  "oldValue": "OFF",
                  "newValue": "PERFORMANCE",
                  "reason": "Boost performance"
                },
                {
                  "key": "cpuList",
                  "oldValue": "0,1,2,3",
                  "newValue": "3,4,5,6,7",
                  "reason": "Custom 5-core Big+Prime mask"
                },
                {
                  "key": "displayServer",
                  "oldValue": "gamescope",
                  "newValue": "x11",
                  "reason": "Direct X11 path"
                }
              ],
              "gameConfigEdits": [],
              "manualTips": []
            }
        """.trimIndent()

        val parsed = AiPatchValidator.parseAndValidate(
            context = null,
            appId = null,
            rawResponseText = rawResponse,
            currentConfig = currentConfig,
            installedComponents = null,
            coreCountOverride = 8,
            coreFreqsKhz = freqs,
        ).getOrThrow()

        val remainingKeys = parsed.allowedContainerChanges.map { it.key }
        assertFalse("Contradicting custom cpuList must drop tuningMode", "tuningMode" in remainingKeys)
        assertTrue("cpuList" in remainingKeys)
        assertTrue("displayServer" in remainingKeys)

        val patched = AiPatchApplier.applyPatch(
            context = null,
            appId = null,
            currentConfig = currentConfig,
            response = parsed,
            coreCountOverride = 8,
            coreFreqsKhz = freqs,
        )
        assertEquals("OFF", patched.tuningMode)
        assertEquals("3,4,5,6,7", patched.cpuList)
        assertEquals("x11", patched.displayServer)
    }
}
