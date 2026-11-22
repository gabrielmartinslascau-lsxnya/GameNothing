package app.gamenative.playground

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

object LinuxFex {
    const val TOOL = "/usr/local/bin/droiddeck-fex"
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"
    val MODES = listOf(AUTO, ON, OFF)
    const val ELF_I386 = 3
    const val ELF_X86_64 = 62
    const val ELF_AARCH64 = 183
    private const val MODE_FILE = "fex"
    private const val ARCH_FILE = "arch"
    private const val STATUS = "root/.local/share/droiddeck-fex/status.json"

    fun ready(context: Context): Boolean =
        File(LinuxRuntime.rootDir(context), STATUS).takeIf { it.isFile }?.readText()
            ?.let { runCatching { JSONObject(it).optBoolean("ready") }.getOrNull() } == true

    fun mode(dir: File): String =
        File(dir, MODE_FILE).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it in MODES } ?: AUTO

    fun setMode(dir: File, mode: String): Boolean {
        val file = File(dir, MODE_FILE)
        return when (mode) {
            AUTO -> !file.exists() || file.delete()
            ON, OFF -> runCatching { file.writeText(mode); true }.getOrDefault(false)
            else -> false
        }
    }

    fun elfMachine(file: File): Int? = try {
        RandomAccessFile(file, "r").use { f ->
            val head = ByteArray(20)
            if (f.read(head) < head.size) return null
            if (head[0] != 0x7f.toByte() || head[1] != 'E'.code.toByte() || head[2] != 'L'.code.toByte() || head[3] != 'F'.code.toByte()) return null
            if (head[5].toInt() == 2) ((head[18].toInt() and 0xff) shl 8) or (head[19].toInt() and 0xff)
            else (head[18].toInt() and 0xff) or ((head[19].toInt() and 0xff) shl 8)
        }
    } catch (e: Exception) {
        null
    }

    fun isX86(machine: Int?): Boolean = machine == ELF_X86_64 || machine == ELF_I386

    fun archName(machine: Int?): String? = when (machine) {
        ELF_X86_64 -> "x86_64"
        ELF_I386 -> "i386"
        ELF_AARCH64 -> "arm64"
        else -> null
    }

    fun writeArch(dir: File, machine: Int?) {
        val name = archName(machine)
        val file = File(dir, ARCH_FILE)
        if (name == null) file.delete() else runCatching { file.writeText(name) }
    }
}
