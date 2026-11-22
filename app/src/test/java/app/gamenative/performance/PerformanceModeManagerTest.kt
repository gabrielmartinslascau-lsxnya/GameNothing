package app.gamenative.performance

import com.winlator.box86_64.Box86_64Preset
import com.winlator.container.Container
import com.winlator.container.ContainerData
import com.winlator.fexcore.FEXCorePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PerformanceModeManagerTest {

    // --- 1. ECO Mode Core Pairing Layouts (Spec §2.3B) ---

    @Test
    fun ecoMode_3Little4Medium1Prime_selects4CoresAnd4ForWow64() {
        // Snapdragon 7 Gen 4 layout: 3 LITTLE + 4 MEDIUM + 1 PRIME
        val freqs = mapOf(
            0 to 1_800_000L, 1 to 1_800_000L, 2 to 1_800_000L, // 3 LITTLE
            3 to 2_400_000L, 4 to 2_400_000L, 5 to 2_400_000L, 6 to 2_400_000L, // 4 MEDIUM
            7 to 2_800_000L, // 1 PRIME
        )
        val sel = PerformanceModeManager.computeEcoCpuSelection(coreCountOverride = 8, coreFreqsKhz = freqs)
        assertEquals(listOf(0, 1, 2, 3), sel.cores64)
        assertEquals("0,1,2,3", sel.cpuList)
        assertEquals(listOf(0, 1, 2, 3), sel.cores32)
        assertEquals("0,1,2,3", sel.cpuListWoW64)
        assertTrue(sel.cores64.size in setOf(2, 4, 6))
        assertTrue(sel.cores32.size <= 4 && sel.cores32.size % 2 == 0)
    }

    @Test
    fun ecoMode_4Little3Medium1Prime_roundsOdd5To6AndCapsWow64ToTop4() {
        val freqs = mapOf(
            0 to 1_800_000L, 1 to 1_800_000L, 2 to 1_800_000L, 3 to 1_800_000L, // 4 LITTLE
            4 to 2_400_000L, 5 to 2_400_000L, 6 to 2_400_000L, // 3 MEDIUM
            7 to 3_000_000L, // 1 PRIME
        )
        val sel = PerformanceModeManager.computeEcoCpuSelection(coreCountOverride = 8, coreFreqsKhz = freqs)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), sel.cores64)
        assertEquals("0,1,2,3,4,5", sel.cpuList)
        // 32-bit capped to top 4 of those 6 (2 MEDIUM + top 2 LITTLE)
        assertEquals(4, sel.cores32.size)
        assertEquals(listOf(2, 3, 4, 5), sel.cores32)
        assertTrue(sel.cores32.all { it in sel.cores64 })
    }

    @Test
    fun ecoMode_2Little5Medium1Prime_roundsOdd3To4And4ForWow64() {
        val freqs = mapOf(
            0 to 1_800_000L, 1 to 1_800_000L, // 2 LITTLE
            2 to 2_400_000L, 3 to 2_400_000L, 4 to 2_400_000L, 5 to 2_400_000L, 6 to 2_400_000L, // 5 MEDIUM
            7 to 3_200_000L, // 1 PRIME
        )
        val sel = PerformanceModeManager.computeEcoCpuSelection(coreCountOverride = 8, coreFreqsKhz = freqs)
        assertEquals(listOf(0, 1, 2, 3), sel.cores64)
        assertEquals("0,1,2,3", sel.cpuList)
        assertEquals(listOf(0, 1, 2, 3), sel.cores32)
        assertEquals("0,1,2,3", sel.cpuListWoW64)
    }

    @Test
    fun ecoMode_6Performance2Prime_selects4Or6FromLowerClusterNever8() {
        // 2-cluster SoC (e.g. Snapdragon 8 Elite: 6 Performance + 2 Prime)
        val freqs = mapOf(
            0 to 3_530_000L, 1 to 3_530_000L, 2 to 3_530_000L,
            3 to 3_530_000L, 4 to 3_530_000L, 5 to 3_530_000L, // 6 Performance (lower cluster)
            6 to 4_320_000L, 7 to 4_320_000L, // 2 Prime (upper cluster)
        )
        val sel = PerformanceModeManager.computeEcoCpuSelection(coreCountOverride = 8, coreFreqsKhz = freqs)
        assertTrue("Eco must pick 4 or 6 cores, got ${sel.cores64}", sel.cores64.size in setOf(4, 6))
        assertTrue("Eco must only use lower cluster (0..5)", sel.cores64.all { it in 0..5 })
        assertFalse("Eco must never pick all 8 cores", sel.cores64.size == 8)
        assertEquals(4, sel.cores32.size)
        assertTrue(sel.cores32.all { it in sel.cores64 })
    }

    @Test
    fun ecoMode_uniformOrUnreadableSysfs_fallsBackToFirst4EvenCores() {
        val selEmpty = PerformanceModeManager.computeEcoCpuSelection(coreCountOverride = 8, coreFreqsKhz = emptyMap())
        assertEquals(listOf(0, 1, 2, 3), selEmpty.cores64)
        assertEquals(listOf(0, 1, 2, 3), selEmpty.cores32)

        val uniform = (0..7).associateWith { 2_000_000L }
        val selUniform = PerformanceModeManager.computeEcoCpuSelection(coreCountOverride = 8, coreFreqsKhz = uniform)
        assertEquals(listOf(0, 1, 2, 3), selUniform.cores64)
        assertEquals(listOf(0, 1, 2, 3), selUniform.cores32)
    }

    // --- 2. PERFORMANCE Mode 32-bit <= 4 Highest-Frequency Core Cap ---

    @Test
    fun performanceMode_unlocksAll64BitCoresAndCaps32BitToTop4HighestFreq() {
        val freqs = mapOf(
            0 to 1_800_000L, 1 to 1_800_000L, 2 to 1_800_000L,
            3 to 2_400_000L, 4 to 2_400_000L, 5 to 2_400_000L, 6 to 2_400_000L,
            7 to 2_800_000L,
        )
        val sel = PerformanceModeManager.computePerformanceCpuSelection(coreCountOverride = 8, coreFreqsKhz = freqs)
        assertEquals("0,1,2,3,4,5,6,7", sel.cpuList)
        assertEquals(4, sel.cores32.size)
        assertEquals(listOf(4, 5, 6, 7), sel.cores32)
        assertEquals("4,5,6,7", sel.cpuListWoW64)
    }

    // --- 3. Snapshot/Restore, PERFORMANCE <-> ECO Baseline Preservation & Hard Invariants ---

    @Test
    fun switchingPerformanceToEcoToOffPreservesOriginalBaselineAndNeverTouchesUserFpsCap() {
        val freqs = mapOf(
            0 to 1_800_000L, 1 to 1_800_000L, 2 to 1_800_000L,
            3 to 2_400_000L, 4 to 2_400_000L, 5 to 2_400_000L, 6 to 2_400_000L,
            7 to 2_800_000L,
        )
        val initial = ContainerData(
            displayServer = "gamescope",
            fexcorePreset = FEXCorePreset.COMPATIBILITY,
            box64Preset = Box86_64Preset.STABILITY,
            cpuList = "0,1,2,3",
            cpuListWoW64 = "0,1",
            fpsLimiterEnabled = true,
            rootlessGamePinningEnabled = false,
            wineVersion = "proton-9.0-arm64ec",
            dxwrapper = "dxvk",
            dxwrapperConfig = "version=2.4.1",
            graphicsDriver = "turnip",
            screenSize = "1600x900",
            steamOfflineMode = false,
        )

        val perf = PerformanceModeManager.enablePerformanceMode(
            config = initial,
            coreCountOverride = 8,
            coreFreqsKhz = freqs,
        )
        assertEquals(TuningMode.PERFORMANCE.id, perf.tuningMode)
        assertTrue(perf.tuningModeSnapshot.isNotBlank())
        assertEquals(Container.DISPLAY_SERVER_X11, perf.displayServer)
        assertEquals(FEXCorePreset.EXTREME, perf.fexcorePreset)
        assertEquals(Box86_64Preset.PERFORMANCE, perf.box64Preset)
        assertEquals("0,1,2,3,4,5,6,7", perf.cpuList)
        assertEquals("4,5,6,7", perf.cpuListWoW64)
        assertTrue(perf.rootlessGamePinningEnabled)
        // Hard invariants MUST remain untouched
        assertTrue("User FPS cap MUST NOT be modified in Performance Mode", perf.fpsLimiterEnabled)
        assertEquals("proton-9.0-arm64ec", perf.wineVersion)
        assertEquals("dxvk", perf.dxwrapper)
        assertEquals("version=2.4.1", perf.dxwrapperConfig)
        assertEquals("turnip", perf.graphicsDriver)
        assertEquals("1600x900", perf.screenSize)
        assertFalse(perf.steamOfflineMode)

        // Direct switch PERFORMANCE -> ECO must preserve the original OFF baseline snapshot
        val eco = PerformanceModeManager.enableEcoMode(
            config = perf,
            coreCountOverride = 8,
            coreFreqsKhz = freqs,
        )
        assertEquals(TuningMode.ECO.id, eco.tuningMode)
        assertEquals(perf.tuningModeSnapshot, eco.tuningModeSnapshot)
        assertEquals("gamescope", eco.displayServer)
        assertEquals(FEXCorePreset.INTERMEDIATE, eco.fexcorePreset)
        assertEquals("0,1,2,3", eco.cpuList)
        assertEquals("0,1,2,3", eco.cpuListWoW64)
        assertFalse(eco.rootlessGamePinningEnabled)
        assertTrue("User FPS cap MUST NOT be modified in Eco Mode", eco.fpsLimiterEnabled)

        // Returning ECO -> OFF must restore the exact original OFF baseline
        val restored = PerformanceModeManager.disableTuningMode(eco)
        assertEquals(TuningMode.OFF.id, restored.tuningMode)
        assertEquals("", restored.tuningModeSnapshot)
        assertEquals("gamescope", restored.displayServer)
        assertEquals(FEXCorePreset.COMPATIBILITY, restored.fexcorePreset)
        assertEquals(Box86_64Preset.STABILITY, restored.box64Preset)
        assertEquals("0,1,2,3", restored.cpuList)
        assertEquals("0,1", restored.cpuListWoW64)
        assertTrue(restored.fpsLimiterEnabled)
        assertFalse(restored.rootlessGamePinningEnabled)
    }

    @Test
    fun manualOverrideFlipsTuningModeOffWithoutRevertingUserEdit() {
        val initial = ContainerData(
            displayServer = "gamescope",
            fexcorePreset = FEXCorePreset.INTERMEDIATE,
            cpuList = "0,1,2,3",
            cpuListWoW64 = "0,1,2,3",
        )
        val perf = PerformanceModeManager.enablePerformanceMode(initial, coreCountOverride = 8)
        assertEquals(TuningMode.PERFORMANCE.id, perf.tuningMode)

        // User manually changes FEXCore preset from EXTREME to COMPATIBILITY
        val manuallyEdited = perf.copy(fexcorePreset = FEXCorePreset.COMPATIBILITY)
        val reconciled = PerformanceModeManager.reconcileManualOverride(perf, manuallyEdited)

        assertEquals(TuningMode.OFF.id, reconciled.tuningMode)
        assertEquals("", reconciled.tuningModeSnapshot)
        assertEquals(FEXCorePreset.COMPATIBILITY, reconciled.fexcorePreset)
        assertEquals(Container.DISPLAY_SERVER_X11, reconciled.displayServer)
    }

    @Test
    fun legacyPerformanceModeFlagMigratesCleanlyToTuningMode() {
        assertEquals(TuningMode.PERFORMANCE, TuningMode.fromRaw(raw = "", legacyPerformanceMode = true))
        assertEquals(TuningMode.OFF, TuningMode.fromRaw(raw = "", legacyPerformanceMode = false))
        assertEquals(TuningMode.ECO, TuningMode.fromRaw(raw = "ECO", legacyPerformanceMode = true))
    }

    // --- 4. RootlessGamePinner Daemon Exclusion Protection ---

    @Test
    fun rootlessGamePinnerExcludesWineserverPulseAudioAndDisplayDaemonsWithCliFlags() {
        assertTrue(RootlessGamePinner.shouldExcludeProcess("/usr/bin/wineserver -p", "wineserver"))
        assertTrue(RootlessGamePinner.shouldExcludeProcess("/data/lib/libpulseaudio.so --exit-idle-time=-1", "pulseaudio"))
        assertTrue(RootlessGamePinner.shouldExcludeProcess("gamescope -W 1280 -H 720 --backend drm", "gamescope"))
        assertTrue(RootlessGamePinner.shouldExcludeProcess("gndrm-daemon --socket /tmp/gndrm", "gndrm-daemon"))
        assertTrue(RootlessGamePinner.shouldExcludeProcess("C:\\windows\\system32\\services.exe", "services.exe"))
        assertFalse(RootlessGamePinner.shouldExcludeProcess("wine64 Z:\\games\\HollowKnight.exe -fullscreen", "HollowKnight.exe"))
    }
}
