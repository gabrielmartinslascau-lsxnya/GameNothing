package app.gamenative.performance

import android.app.Activity
import android.app.GameManager
import android.app.GameState
import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.PerformanceHintManager
import android.os.Process
import com.winlator.container.Container
import com.winlator.core.envvars.EnvVars
import timber.log.Timber

/**
 * Universal Android OS Performance Signaling (GameNothing 2.0-patch4).
 *
 * Strict Zero-Overhead Invariant:
 * - When `tuningMode` is `OFF` or `ECO`, EVERY method is an immediate zero-allocation no-op.
 *   Nothing calls `setSustainedPerformanceMode`, `GameManager`, `PerformanceHintManager`, or
 *   `RootlessGamePinner`.
 *
 * When `tuningMode == PERFORMANCE` (active gameplay only):
 * 1. Sets `window.setSustainedPerformanceMode(false)` so the OS never clamps clocks to a thermal floor.
 * 2. On Android 13+ (API 33+), signals `GameState(false, MODE_GAMEPLAY_UNINTERRUPTIBLE)` while resumed
 *    and resets to `MODE_NONE` on pause/exit.
 * 3. On Android 12+ (API 31+), opens a guarded ADPF `PerformanceHintManager.Session` ONLY for an
 *    in-process render thread TID (never the Android UI thread), computing `targetDurationNanos`
 *    respecting any user-configured FPS cap (`fpsLimiterTarget` or `DXVK_FRAME_RATE`) so frame
 *    pacing never conflicts with the cap, with guaranteed `.close()` on pause/exit.
 * 4. Starts and stops [RootlessGamePinner] strictly during active `PERFORMANCE` gameplay.
 */
object AndroidOsPerformanceSignaler {

    private var sessionActive: Boolean = false
    private var activeHintSession: PerformanceHintManager.Session? = null
    private var lastRefreshRateHz: Int = 60
    private var lastUserFpsCap: Int = 0
    private var lastContainerCpuList: String? = null
    private var registeredRenderThreadTid: Int = 0
    private var activeTuningMode: TuningMode = TuningMode.OFF

    @Synchronized
    fun onSessionStart(
        context: Context,
        activity: Activity?,
        container: Container,
        refreshRateHz: Int,
        userFpsCap: Int = 0,
    ) {
        val mode = TuningMode.fromContainer(container)
        activeTuningMode = mode

        // Strict zero-overhead no-op when OFF or ECO
        if (mode != TuningMode.PERFORMANCE) {
            return
        }

        val effectiveUserCap = resolveEffectiveUserFpsCap(container, userFpsCap)
        lastRefreshRateHz = refreshRateHz.coerceIn(30, 240)
        lastUserFpsCap = effectiveUserCap
        lastContainerCpuList = container.getCPUList(false)
        sessionActive = true

        applyPerformanceBoosts(context, activity)
    }

    /**
     * Registers an in-process render thread TID (e.g. GL/Vulkan surface render thread) so ADPF
     * can hint that thread while in `PERFORMANCE` mode. Ignores the Android main/UI thread.
     */
    @Synchronized
    fun registerRenderThread(context: Context?, tid: Int) {
        if (!sessionActive || activeTuningMode != TuningMode.PERFORMANCE) return
        if (tid <= 0) return
        val mainTid = runCatching { Process.myPid() }.getOrDefault(-1)
        if (tid == mainTid || Looper.myLooper() == Looper.getMainLooper()) return
        if (registeredRenderThreadTid == tid && activeHintSession != null) return

        registeredRenderThreadTid = tid
        if (context != null) {
            openGuardedAdpfSessionLocked(context, tid)
        }
    }

    @Synchronized
    fun onPause(context: Context?) {
        if (!sessionActive) return
        RootlessGamePinner.stop()
        closeAdpfSessionLocked()
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val gameManager = context.getSystemService(GameManager::class.java)
                gameManager?.setGameState(GameState(false, GameState.MODE_NONE))
            }
        }
    }

    @Synchronized
    fun onResume(context: Context, activity: Activity?) {
        if (!sessionActive || activeTuningMode != TuningMode.PERFORMANCE) return
        applyPerformanceBoosts(context, activity)
    }

    @Synchronized
    fun onSessionEnd(context: Context?) {
        if (!sessionActive) return
        sessionActive = false
        activeTuningMode = TuningMode.OFF
        registeredRenderThreadTid = 0
        lastContainerCpuList = null
        lastUserFpsCap = 0

        RootlessGamePinner.stop()
        closeAdpfSessionLocked()

        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val gameManager = context.getSystemService(GameManager::class.java)
                gameManager?.setGameState(GameState(false, GameState.MODE_NONE))
            }
        }
    }

    private fun applyPerformanceBoosts(context: Context, activity: Activity?) {
        runCatching {
            activity?.window?.setSustainedPerformanceMode(false)
        }

        RootlessGamePinner.start(lastContainerCpuList)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val gameManager = context.getSystemService(GameManager::class.java)
                gameManager?.setGameState(GameState(false, GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE))
                Timber.tag("AndroidOsPerf").i("GameManager set to MODE_GAMEPLAY_UNINTERRUPTIBLE")
            }.onFailure {
                Timber.tag("AndroidOsPerf").d(it, "GameManager.setGameState unavailable")
            }
        }

        // Only open ADPF when an in-process render thread TID is explicitly registered (never UI thread)
        val renderTid = registeredRenderThreadTid
        if (renderTid > 0) {
            openGuardedAdpfSessionLocked(context, renderTid)
        }
    }

    private fun openGuardedAdpfSessionLocked(context: Context, renderTid: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        closeAdpfSessionLocked()
        runCatching {
            val hintManager = context.getSystemService(PerformanceHintManager::class.java) ?: return
            // Respect user FPS cap so ADPF target duration never fights frame pacing when cap is hit
            val targetHz = if (lastUserFpsCap > 0) {
                minOf(lastRefreshRateHz, lastUserFpsCap).coerceIn(24, 240)
            } else {
                lastRefreshRateHz.coerceIn(30, 240)
            }
            val targetDurationNanos = 1_000_000_000L / targetHz
            val session = hintManager.createHintSession(
                intArrayOf(renderTid),
                targetDurationNanos,
            )
            activeHintSession = session
            Timber.tag("AndroidOsPerf").i(
                "ADPF PerformanceHintManager session started (renderTid=%d, targetHz=%d, targetNanos=%d)",
                renderTid,
                targetHz,
                targetDurationNanos,
            )
        }.onFailure {
            Timber.tag("AndroidOsPerf").d(it, "ADPF PerformanceHintManager unavailable")
        }
    }

    private fun closeAdpfSessionLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { activeHintSession?.close() }
            activeHintSession = null
        }
    }

    internal fun resolveEffectiveUserFpsCap(container: Container, limiterCap: Int): Int {
        val dxvkCap = runCatching {
            val env = EnvVars(container.envVars)
            if (env.has("DXVK_FRAME_RATE")) env.get("DXVK_FRAME_RATE").trim().toIntOrNull() ?: 0 else 0
        }.getOrDefault(0)
        return when {
            limiterCap > 0 && dxvkCap > 0 -> minOf(limiterCap, dxvkCap)
            limiterCap > 0 -> limiterCap
            dxvkCap > 0 -> dxvkCap
            else -> 0
        }
    }
}
