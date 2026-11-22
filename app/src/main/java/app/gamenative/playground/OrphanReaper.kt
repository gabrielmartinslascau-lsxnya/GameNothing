package app.gamenative.playground

import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Reaps orphaned guest Linux processes from /proc, verifying child uid
 * and preventing background process leakage.
 */
object OrphanReaper {
    private val kept = ConcurrentHashMap.newKeySet<Int>()

    fun keep(pid: Int) {
        if (pid > 0) kept.add(pid)
    }

    fun release(pid: Int) {
        kept.remove(pid)
    }

    fun hasOtherProcesses(): Boolean = hasOtherProcesses(
        File("/proc").list()?.mapNotNull { it.toIntOrNull() },
        Process.myPid(),
    ) { pid ->
        try {
            Os.kill(pid, 0)
            true
        } catch (error: ErrnoException) {
            error.errno != OsConstants.ESRCH && error.errno != OsConstants.EPERM
        } catch (error: SecurityException) {
            true
        }
    }

    internal fun hasOtherProcesses(pids: List<Int>?, ownPid: Int, signalable: (Int) -> Boolean): Boolean =
        pids == null || pids.any { it > 1 && it != ownPid && signalable(it) }

    fun reap(reason: String, matching: (String) -> Boolean = { true }): Int {
        val me = Process.myPid()
        val uid = Process.myUid()
        val spared = descendants(kept)
        var killed = 0
        File("/proc").listFiles()?.forEach { entry ->
            val pid = entry.name.toIntOrNull() ?: return@forEach
            if (pid == me || pid <= 1 || pid in spared) return@forEach
            if (uidOf(pid) != uid) return@forEach
            val name = try {
                File(entry, "cmdline").readText().substringBefore('\u0000')
            } catch (e: Exception) {
                "?"
            }
            if (!matching(name)) return@forEach
            try {
                Process.killProcess(pid)
                killed++
                Timber.i("OrphanReaper: killed leftover $pid ($name) - $reason")
            } catch (e: Exception) {
                Timber.w(e, "OrphanReaper: could not kill $pid ($name)")
            }
        }
        if (killed > 0) {
            Timber.i("OrphanReaper: $killed leftover process(es) reaped - $reason")
        }
        return killed
    }

    private fun descendants(roots: Set<Int>): Set<Int> {
        if (roots.isEmpty()) return emptySet()
        val parent = HashMap<Int, Int>()
        File("/proc").listFiles()?.forEach { entry ->
            val pid = entry.name.toIntOrNull() ?: return@forEach
            val stat = try {
                File(entry, "stat").readText()
            } catch (e: Exception) {
                return@forEach
            }
            stat.substringAfterLast(") ").split(' ').getOrNull(1)?.toIntOrNull()?.let {
                parent[pid] = it
            }
        }
        val out = HashSet(roots)
        var grew = true
        while (grew) {
            grew = false
            for ((pid, ppid) in parent) {
                if (ppid in out && out.add(pid)) grew = true
            }
        }
        return out
    }

    private fun uidOf(pid: Int): Int {
        return try {
            File("/proc/$pid/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("Uid:") }
                    ?.substringAfter("Uid:")?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toIntOrNull() ?: -1
            }
        } catch (e: Exception) {
            -1
        }
    }
}
