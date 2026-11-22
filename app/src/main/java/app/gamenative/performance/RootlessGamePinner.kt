package app.gamenative.performance

import android.os.Process
import app.gamenative.powercontrol.TaskAffinity
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Universal Rootless Game Pinning (GameNothing 2.0-patch4).
 *
 * Works on every Android phone with zero root and zero privileged daemon requirements.
 *
 * Safety invariants:
 * 1. ONLY runs when `tuningMode == PERFORMANCE` (never polls or allocates in `OFF` or `ECO`).
 * 2. NEVER touches `wineserver`, `pulseaudio`, `gamescope`, `XServer`/`Xwayland`/`gndrm`,
 *    `proot`, `fexserver`, `vortek`/`virgl`, `dbus`, `steam`, or the Android host process/UI thread.
 * 3. Only pins active game/render worker threads belonging to same-UID Windows `.exe` processes.
 * 4. Synchronizes `pinnedThreads` access across IO polling and Main stop/pause threads,
 *    silently ignores `ESRCH`/`EPERM`, and restores threads to the container's configured mask.
 */
object RootlessGamePinner {

    private const val SAMPLE_INTERVAL_MS = 3_000L

    private val EXCLUDED_EXACT_NAMES = setOf(
        "wineserver",
        "pulseaudio",
        "libpulseaudio.so",
        "gamescope",
        "xwayland",
        "gndrm",
        "gndrm-daemon",
        "xserver",
        "proot",
        "fexserver",
        "fexinterpreter",
        "vortek",
        "virgl",
        "virgl_test_server",
        "dbus-daemon",
        "comet",
        "steam",
        "steamwebhelper.exe",
        "libsteambootstrap.so",
        "services.exe",
        "plugplay.exe",
        "svchost.exe",
        "rpcss.exe",
        "conhost.exe",
        "winedevice.exe",
        "explorer.exe",
        "start.exe",
        "tabtip.exe",
        "wineboot.exe",
        "winemenubuilder.exe",
        "logcat",
        "sh",
        "dash",
        "bash",
    )

    private val EXCLUDED_SUBSTRINGS = listOf(
        "wineserver",
        "pulseaudio",
        "libpulseaudio",
        "gamescope",
        "xwayland",
        "gndrm",
        "xserver",
        "proot",
        "fexserver",
        "vortek",
        "virgl",
        "dbus",
        "libsteambootstrap",
    )

    data class CpuClusterProfile(
        val allCores: List<Int>,
        val bigPrimeCores: List<Int>,
        val littleCores: List<Int>,
        val allCoresMask: Int,
        val bigPrimeMask: Int,
        val coreMaxFreqKhz: Map<Int, Long>,
    ) {
        val isHeterogeneous: Boolean
            get() = bigPrimeCores.isNotEmpty() && bigPrimeCores.size < allCores.size && bigPrimeMask != 0
    }

    @Volatile
    private var cachedClusterProfile: CpuClusterProfile? = null

    private val stateLock = Any()
    private var activeScope: CoroutineScope? = null
    private var activeJob: Job? = null
    private val pinnedThreads = HashSet<Int>()
    private var activeRestoreMask: Int = 0

    /**
     * Detects CPU cluster topology by reading `/sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq`.
     */
    fun detectCpuClusters(): CpuClusterProfile {
        cachedClusterProfile?.let { return it }

        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val freqMap = PerformanceModeManager.readCpuMaxFreqsKhz(coreCount)
        val allCores = (0 until coreCount).toList()
        val allMask = coresToBitmask(allCores)

        if (freqMap.size < 2) {
            return CpuClusterProfile(
                allCores = allCores,
                bigPrimeCores = emptyList(),
                littleCores = emptyList(),
                allCoresMask = allMask,
                bigPrimeMask = 0,
                coreMaxFreqKhz = freqMap,
            ).also { cachedClusterProfile = it }
        }

        val minFreq = freqMap.values.minOrNull() ?: 0L
        val maxFreq = freqMap.values.maxOrNull() ?: 0L

        if (minFreq <= 0L || maxFreq <= minFreq) {
            return CpuClusterProfile(
                allCores = allCores,
                bigPrimeCores = emptyList(),
                littleCores = emptyList(),
                allCoresMask = allMask,
                bigPrimeMask = 0,
                coreMaxFreqKhz = freqMap,
            ).also { cachedClusterProfile = it }
        }

        val bigPrime = freqMap.entries
            .filter { it.value > minFreq }
            .sortedByDescending { it.value }
            .map { it.key }
        val little = freqMap.entries
            .filter { it.value <= minFreq }
            .map { it.key }

        return CpuClusterProfile(
            allCores = allCores,
            bigPrimeCores = bigPrime,
            littleCores = little,
            allCoresMask = allMask,
            bigPrimeMask = coresToBitmask(bigPrime),
            coreMaxFreqKhz = freqMap,
        ).also {
            cachedClusterProfile = it
            Timber.tag("RootlessGamePinner").i(
                "Detected CPU topology: bigPrime=%s (mask=0x%x), little=%s, all=%s",
                bigPrime,
                it.bigPrimeMask,
                little,
                allCores,
            )
        }
    }

    /**
     * Starts the rootless thread-pinning coroutine loop while the game is actively running
     * in `PERFORMANCE` mode.
     *
     * @param containerCpuList Optional comma-separated core list for restoring unpinned threads
     *                         so we never widen a restricted container affinity mask.
     */
    fun start(containerCpuList: String? = null) {
        synchronized(stateLock) {
            stopLocked()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            activeScope = scope
            activeJob = scope.launch {
                val profile = detectCpuClusters()
                if (!profile.isHeterogeneous) {
                    Timber.tag("RootlessGamePinner").d(
                        "Uniform or unreadable CPU cluster frequencies; skipping rootless thread pinning",
                    )
                    return@launch
                }

                val configuredMask = parseCpuListToBitmask(containerCpuList)
                val restoreMask = if (configuredMask != 0) {
                    (configuredMask and profile.allCoresMask).takeIf { it != 0 } ?: profile.allCoresMask
                } else {
                    profile.allCoresMask
                }
                val pinMask = (profile.bigPrimeMask and restoreMask).takeIf { it != 0 } ?: return@launch

                synchronized(stateLock) {
                    activeRestoreMask = restoreMask
                }

                val myUid = Process.myUid()
                val myPid = Process.myPid()
                val topThreadBudget = profile.bigPrimeCores.size.coerceAtLeast(2)
                var previousThreadTicks = HashMap<Int, Long>()

                Timber.tag("RootlessGamePinner").i(
                    "RootlessGamePinner started (uid=%d, pinMask=0x%x, restoreMask=0x%x, budget=%d)",
                    myUid,
                    pinMask,
                    restoreMask,
                    topThreadBudget,
                )

                while (isActive) {
                    try {
                        val currentThreadTicks = HashMap<Int, Long>()
                        val threadDeltas = ArrayList<Pair<Int, Long>>()

                        val candidatePids = findSameUidGamePids(myUid, myPid)
                        for (pid in candidatePids) {
                            val taskDir = File("/proc/$pid/task")
                            val tidDirs = runCatching { taskDir.listFiles() }.getOrNull() ?: continue
                            for (tidDir in tidDirs) {
                                val tid = tidDir.name.toIntOrNull() ?: continue
                                // Never touch the host process or process main thread if somehow matched
                                if (tid == myPid) continue
                                val ticks = readThreadTicks(tidDir) ?: continue
                                currentThreadTicks[tid] = ticks
                                val prev = previousThreadTicks[tid]
                                if (prev != null) {
                                    val delta = (ticks - prev).coerceAtLeast(0L)
                                    if (delta > 0L) {
                                        threadDeltas.add(tid to delta)
                                    }
                                }
                            }
                        }

                        if (threadDeltas.isNotEmpty() && isActive) {
                            threadDeltas.sortByDescending { it.second }
                            val topTids = threadDeltas
                                .take(topThreadBudget)
                                .mapTo(HashSet()) { it.first }

                            val toPin = ArrayList<Int>()
                            val toUnpin = ArrayList<Int>()

                            synchronized(stateLock) {
                                if (!isActive) return@synchronized
                                for (tid in topTids) {
                                    if (pinnedThreads.add(tid)) {
                                        toPin.add(tid)
                                    }
                                }
                                val iterator = pinnedThreads.iterator()
                                while (iterator.hasNext()) {
                                    val oldTid = iterator.next()
                                    if (oldTid !in topTids) {
                                        iterator.remove()
                                        if (currentThreadTicks.containsKey(oldTid)) {
                                            toUnpin.add(oldTid)
                                        }
                                    }
                                }
                            }

                            // Perform sched_setaffinity outside stateLock; silently ignore ESRCH/EPERM
                            for (tid in toPin) {
                                runCatching { TaskAffinity.set(tid, pinMask) }
                            }
                            for (tid in toUnpin) {
                                runCatching { TaskAffinity.set(tid, restoreMask) }
                            }
                        }

                        previousThreadTicks = currentThreadTicks
                    } catch (_: Exception) {
                        // Silently ignore transient /proc read / ESRCH / EPERM failures
                    }

                    delay(SAMPLE_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Immediately stops polling and restores any pinned threads back to the container restore mask.
     * Pure no-op if never started.
     */
    fun stop() {
        val toRestore: List<Int>
        val restoreMask: Int
        synchronized(stateLock) {
            if (activeJob == null && activeScope == null && pinnedThreads.isEmpty()) {
                return
            }
            restoreMask = activeRestoreMask.takeIf { it != 0 }
                ?: cachedClusterProfile?.allCoresMask
                ?: 0
            toRestore = if (restoreMask != 0 && pinnedThreads.isNotEmpty()) {
                pinnedThreads.toList()
            } else {
                emptyList()
            }
            stopLocked()
        }

        for (tid in toRestore) {
            runCatching { TaskAffinity.set(tid, restoreMask) }
        }
    }

    private fun stopLocked() {
        activeJob?.cancel()
        activeJob = null
        pinnedThreads.clear()
        activeRestoreMask = 0
        activeScope?.cancel()
        activeScope = null
    }

    private fun findSameUidGamePids(myUid: Int, myPid: Int): List<Int> {
        val procDir = File("/proc")
        val entries = runCatching { procDir.listFiles() }.getOrNull() ?: return emptyList()
        val result = ArrayList<Int>()
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            // Never touch our Android app host process (UI thread, XServer, RenderThread, etc.)
            if (pid == myPid) continue
            try {
                val statusFile = File(entry, "status")
                if (!statusFile.exists() || !statusFile.canRead()) continue
                var uidMatches = false
                var processName = ""
                statusFile.useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("Name:")) {
                            processName = line.substringAfter("Name:").trim().lowercase(Locale.ROOT)
                        } else if (line.startsWith("Uid:")) {
                            val parts = line.substringAfter("Uid:").trim().split(Regex("\\s+"))
                            val realUid = parts.firstOrNull()?.toIntOrNull()
                            uidMatches = (realUid == myUid)
                            break
                        }
                    }
                }
                if (!uidMatches) continue

                val cmdline = runCatching {
                    File(entry, "cmdline")
                        .takeIf { it.exists() && it.canRead() }
                        ?.readText()
                        ?.replace('\u0000', ' ')
                        ?.trim()
                        .orEmpty()
                }.getOrDefault("")

                if (shouldExcludeProcess(cmdline, processName)) continue

                result.add(pid)
            } catch (_: Exception) {
                // Process exited mid-scan; ignore ESRCH / permission errors
            }
        }
        return result
    }

    /**
     * Returns true if this process is a system/Wine/audio/display daemon or NOT a Windows game `.exe`.
     *
     * Fixes the 2.0-patch3 bug where `tokens.lastOrNull()` returned CLI flags (e.g. `-p` on
     * `wineserver -p` or `--exit-idle-time=-1` on `libpulseaudio.so`) and bypassed exclusions.
     */
    internal fun shouldExcludeProcess(cmdline: String, processName: String): Boolean {
        val normalizedProcName = processName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .lowercase(Locale.ROOT)

        if (normalizedProcName.isEmpty() || normalizedProcName in EXCLUDED_EXACT_NAMES) return true
        if (EXCLUDED_SUBSTRINGS.any { normalizedProcName.contains(it) }) return true

        val lowerCmd = cmdline.lowercase(Locale.ROOT)
        if (EXCLUDED_SUBSTRINGS.any { lowerCmd.contains(it) }) return true

        val tokens = cmdline.split(' ')
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotEmpty() }

        // Check argv[0] basename against exclusions
        val argv0 = tokens.firstOrNull()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        if (argv0 in EXCLUDED_EXACT_NAMES) return true

        // Only pin actual Windows game executables (.exe), excluding Wine background helpers
        val exeToken = tokens.firstOrNull { it.endsWith(".exe", ignoreCase = true) }
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.lowercase(Locale.ROOT)

        if (exeToken != null) {
            return exeToken in EXCLUDED_EXACT_NAMES
        }

        // If cmdline has no .exe token, only allow if processName itself ends with .exe and isn't excluded
        return !normalizedProcName.endsWith(".exe")
    }

    private fun readThreadTicks(tidDir: File): Long? {
        return try {
            val statFile = File(tidDir, "stat")
            if (!statFile.exists() || !statFile.canRead()) return null
            val text = statFile.readText()
            val closeParen = text.lastIndexOf(')')
            if (closeParen < 0 || closeParen + 2 >= text.length) return null
            val fields = text.substring(closeParen + 2).trim().split(' ')
            if (fields.size < 13) return null
            val utime = fields[11].toLongOrNull() ?: return null
            val stime = fields[12].toLongOrNull() ?: return null
            utime + stime
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseCpuListToBitmask(cpuList: String?): Int {
        if (cpuList.isNullOrBlank()) return 0
        val cores = cpuList.split(',').mapNotNull { it.trim().toIntOrNull() }
        return coresToBitmask(cores)
    }

    internal fun coresToBitmask(cores: Collection<Int>): Int {
        var mask = 0
        for (core in cores) {
            if (core in 0..30) {
                mask = mask or (1 shl core)
            }
        }
        return mask
    }
}
