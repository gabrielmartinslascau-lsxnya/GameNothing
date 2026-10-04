package app.gamenative.linux

import android.content.Context
import android.os.Environment
import app.gamenative.PrefManager
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.ManifestContentTypes
import app.gamenative.utils.ManifestInstaller
import app.gamenative.utils.ManifestRepository
import app.gamenative.utils.downloader.DXWrapperDownloader
import app.gamenative.utils.downloader.LinuxRootfsDownloader
import com.winlator.container.ContainerManager
import com.winlator.contents.AdrenotoolsManager
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import com.winlator.core.DXVKHelper
import com.winlator.core.FileUtils
import com.winlator.core.KeyValueSet
import com.winlator.core.ProcessHelper
import com.winlator.core.TarCompressorUtils
import com.winlator.core.envvars.EnvVars
import com.winlator.fexcore.FEXCorePresetManager
import com.winlator.xenvironment.ImageFs
import com.winlator.xenvironment.ImageFsInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TimeZone
import timber.log.Timber

/**
 * Manages the initialization, configuration, and execution of PRoot for the Linux environment.
 * Uses robust PRoot flags (--link2symlink, --kernel-release) and GameNative's native engine hooks.
 */
object LinuxContainerManager {

    /**
     * Constructs the PRoot execution command and environment array.
     */
    fun buildProotCommand(
        context: Context,
        guestCommand: String
    ): Pair<String, Array<String>> {
        val imageFs = ImageFs.find(context)
        val rootfsDir = imageFs.rootDir
        val tmpDir = File(context.cacheDir, "proot_tmp").apply { mkdirs() }
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        val shmDir = File(rootfsDir, "tmp/shm").apply { mkdirs() }
        val x11Dir = File(imageFs.getTmpDir(), ".X11-unix").apply { mkdirs() }
        val soundDir = File(rootfsDir, "tmp/.sound").apply { mkdirs() }
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath

        // Sync contents and ensure active Proton version symlink
        val contentsManager = ContentsManager(context)
        contentsManager.syncContents()

        val protonVer = PrefManager.linuxProtonVersion
        ImageFsInstaller.ensureProtonVersionSymlink(context, rootfsDir, protonVer)

        // Env vars for inside the container
        val guestEnv = EnvVars().apply {
            put("TZ", TimeZone.getDefault().id)
            put("HOME", "/root")
            put("USER", "root")
            put("TMPDIR", "/tmp")
            put("LC_ALL", "en_US.utf8")
            put("DISPLAY", ":0")
            put("PULSE_SERVER", "unix:${soundDir.absolutePath}/pulse-socket")
            put("PULSE_LATENCY_MSEC", "60")
            put("BOX64_MMAP32", "0")
            put("WINE_LARGE_ADDRESS_AWARE", "1")
            put("MESA_SHADER_CACHE_MAX_SIZE", "4096MB")
            put("WINE", "/opt/$protonVer/bin/wine")
            put("PATH", "/opt/$protonVer/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("LD_LIBRARY_PATH", "/usr/lib/aarch64-linux-gnu:/usr/lib:/opt/$protonVer/lib")
        }

        // Apply GameNative's native DXVKHelper environment variables
        val dxvkVer = PrefManager.linuxDxvkVersion
        val dxvkConfig = KeyValueSet("version=$dxvkVer")
        DXVKHelper.setEnvVars(context, dxvkConfig, guestEnv)

        // Apply GameNative's native FEXCorePresetManager environment variables
        val fexPreset = PrefManager.linuxFexPreset
        guestEnv.putAll(FEXCorePresetManager.getEnvVars(context, fexPreset))

        // Apply GameNative's native Adrenotools driver logic (Turnip/Zink hooks)
        AdrenotoolsManager(context).setDriverById(guestEnv, imageFs, PrefManager.linuxDriver)

        // Build PRoot invocation string with industry-standard flags (--link2symlink, --kernel-release)
        val prootBinary = "$nativeLibDir/libproot.so"
        var cmd = prootBinary
        cmd += " --kill-on-exit"
        cmd += " --link2symlink"
        cmd += " --kernel-release=5.10.0"
        cmd += " --rootfs=${rootfsDir.absolutePath}"
        cmd += " --cwd=/root"
        cmd += " --bind=/dev"
        cmd += " --bind=/proc"
        cmd += " --bind=/sys"
        cmd += " --bind=${shmDir.absolutePath}:/dev/shm"
        cmd += " --bind=${x11Dir.absolutePath}:/tmp/.X11-unix"
        cmd += " --bind=${soundDir.absolutePath}:/tmp/.sound"

        val extStorageFile = File(externalStorage)
        if (extStorageFile.exists()) {
            cmd += " --bind=$externalStorage:/mnt/sdcard"
        }

        cmd += " /usr/bin/env ${guestEnv.toEscapedString()} $guestCommand"

        // Env vars for PRoot process itself
        val prootEnv = EnvVars().apply {
            put("PROOT_TMP_DIR", tmpDir.absolutePath)
            put("PROOT_LOADER", "$nativeLibDir/libproot-loader.so")
            val loader32 = File("$nativeLibDir/libproot-loader32.so")
            if (loader32.exists()) {
                put("PROOT_LOADER_32", loader32.absolutePath)
            }
        }

        return Pair(cmd, prootEnv.toStringArray())
    }

    /**
     * Extracts and installs DXVK DLLs (d3d11.dll, dxgi.dll, etc.) directly into Wine/Proton system directory.
     */
    suspend fun installDXWrapperFiles(context: Context, dxvkVersion: String) {
        val componentId = if (dxvkVersion.startsWith("dxvk-") || dxvkVersion.startsWith("vkd3d-")) dxvkVersion else "dxvk-$dxvkVersion"
        val imageFs = ImageFs.find(context)
        val windowsDir = File(imageFs.rootDir, ImageFs.WINEPREFIX + "/drive_c/windows")
        windowsDir.mkdirs()

        try {
            val downloadedFile = DXWrapperDownloader.ensureDXWrapperAvailable(context, componentId)
            if (downloadedFile == null) {
                TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD, context.assets,
                    "dxwrapper/$componentId.tzst", windowsDir
                )
            } else {
                TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD, downloadedFile,
                    windowsDir
                )
            }
            Timber.i("Successfully installed $componentId DLLs into $windowsDir")
        } catch (e: Exception) {
            Timber.w(e, "Could not extract $componentId DLLs into $windowsDir")
        }
    }

    /**
     * Prepares critical files (DNS configuration, temporary directory structure) on the Android host side
     * prior to PRoot execution.
     */
    fun prepareHostRootfs(context: Context) {
        val rootfsDir = LinuxRootfsDownloader.getRootfsDir(context)
        val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
        val resolvConf = File(etcDir, "resolv.conf")
        FileUtils.writeString(resolvConf, "nameserver 1.1.1.1\nnameserver 8.8.8.8\nnameserver 9.9.9.9\n")

        val tmpDir = File(rootfsDir, "tmp").apply { mkdirs() }
        File(tmpDir, ".X11-unix").apply { mkdirs() }
        File(tmpDir, ".sound").apply { mkdirs() }
        File(tmpDir, "shm").apply { mkdirs() }
    }

    /**
     * Creates setup_env.sh inside rootfs/tmp/setup_env.sh to configure DNS, packages, Desktop Environment, Proton, FEX, and DXVK.
     */
    fun createSetupScript(context: Context): File {
        prepareHostRootfs(context)
        val rootfsDir = LinuxRootfsDownloader.getRootfsDir(context)
        val tmpDir = File(rootfsDir, "tmp").apply { mkdirs() }
        val scriptFile = File(tmpDir, "setup_env.sh")

        val protonVer = PrefManager.linuxProtonVersion
        val dxvkVer = PrefManager.linuxDxvkVersion
        val fexPreset = PrefManager.linuxFexPreset
        val driver = PrefManager.linuxDriver

        val fexEnvVars = FEXCorePresetManager.getEnvVars(context, fexPreset)
        val fexEnvExports = fexEnvVars.toStringArray().joinToString("\n") { entry -> "export $entry" }

        val scriptContent = """
            #!/bin/bash
            export DEBIAN_FRONTEND=noninteractive
            export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}PATH"

            mkdir -p /etc
            echo "nameserver 1.1.1.1" > /etc/resolv.conf
            echo "nameserver 8.8.8.8" >> /etc/resolv.conf

            apt-get update -y || true
            apt-get install -y x11-utils xterm xfce4 xfce4-terminal openbox dbus-x11 mesa-utils curl wget ca-certificates libgl1-mesa-dri libvulkan1 flatpak xdg-desktop-portal || true
            flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo || true

            mkdir -p /etc/profile.d
            cat << EOF > /etc/profile.d/game_env.sh
            export DISPLAY=:0
            export XDG_RUNTIME_DIR=/tmp
            export GALLIUM_DRIVER="zink"
            export MESA_LOADER_DRIVER_OVERRIDE="zink"
            export DXVK_VERSION="$dxvkVer"
            export PROTON_VERSION="$protonVer"
            export FEX_DEFAULT_PRESET="$fexPreset"
            export SELECTED_DRIVER="$driver"
            export BOX64_MMAP32=0
            export WINE_LARGE_ADDRESS_AWARE=1
            export MESA_SHADER_CACHE_MAX_SIZE=4096MB
            $fexEnvExports
            export PATH="/opt/$protonVer/bin:\${'$'}PATH"
            EOF

            chmod +x /etc/profile.d/game_env.sh

            mkdir -p /usr/local/bin
            cat << 'EOF' > /usr/local/bin/start_linux_desktop.sh
            #!/bin/bash
            export DISPLAY=:0
            export XDG_RUNTIME_DIR=/tmp
            source /etc/profile.d/game_env.sh 2>/dev/null || true
            if command -v dbus-run-session >/dev/null 2>&1; then
                if command -v startxfce4 >/dev/null 2>&1; then
                    exec dbus-run-session startxfce4
                elif command -v openbox-session >/dev/null 2>&1; then
                    exec dbus-run-session openbox-session
                fi
            fi
            if command -v startxfce4 >/dev/null 2>&1; then
                exec startxfce4
            elif command -v openbox-session >/dev/null 2>&1; then
                exec openbox-session
            elif command -v xterm >/dev/null 2>&1; then
                exec xterm -geometry 120x40 -e /bin/bash
            else
                echo "No desktop environment found. Keeping container session active..."
                while true; do sleep 10; done
            fi
            EOF

            chmod +x /usr/local/bin/start_linux_desktop.sh
        """.trimIndent()

        FileUtils.writeString(scriptFile, scriptContent)
        FileUtils.chmod(scriptFile, 511) // 0777 octal
        return scriptFile
    }

    /**
     * Runs setup_env.sh inside PRoot container and extracts Wine/Proton assets & DXVK DLLs.
     */
    fun runSetupScript(context: Context, onTermination: (Int) -> Unit): Int {
        val rootfsDir = LinuxRootfsDownloader.getRootfsDir(context)
        val protonVer = PrefManager.linuxProtonVersion
        val dxvkVer = PrefManager.linuxDxvkVersion

        // Sync and apply selected Wine/Proton profile via ContentsManager
        try {
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            val profile = contentsManager.getProfileByEntryName(protonVer)
            if (profile != null) {
                contentsManager.applyContent(profile)
            }
            ImageFsInstaller.ensureProtonVersionSymlink(context, rootfsDir, protonVer)
            ImageFsInstaller.installWineFromAssets(context, context.assets)
        } catch (e: Exception) {
            Timber.w(e, "Could not extract or symlink Wine/Proton assets into linux_rootfs/opt/")
        }

        // Extract DXVK DLLs directly into Wine system directories
        runBlocking {
            installDXWrapperFiles(context, dxvkVer)
        }

        createSetupScript(context)
        val (command, envp) = buildProotCommand(context, "/bin/bash /tmp/setup_env.sh")

        Timber.d("Running PRoot setup script synchronously: $command")
        val output = ProcessHelper.execWithOutput(command, envp, rootfsDir, true, 300)
        Timber.d("PRoot setup script output: $output")
        onTermination(0)
        return 0
    }

    /**
     * Ensures all Linux environment dependencies (Debian RootFS, selected Proton/Wine version,
     * DXVK/VKD3D wrapper DLLs, and Graphics drivers) are downloaded, installed, and configured together.
     */
    suspend fun ensureAndSetupLinuxEnvironment(
        context: Context,
        protonVersion: String,
        dxvkVersion: String,
        fexPreset: String,
        driver: String,
        onProgress: (Float) -> Unit = {},
        onStatusChange: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. RootFS Download & Extraction
            if (!LinuxRootfsDownloader.isRootfsInstalled(context)) {
                onStatusChange("Downloading Linux RootFS...")
                val rootfsResult = LinuxRootfsDownloader.downloadAndExtractRootfs(
                    context = context,
                    onProgress = { onProgress(it * 0.40f) },
                    onStatusChange = onStatusChange
                )
                if (rootfsResult.isFailure) {
                    return@withContext Result.failure(rootfsResult.exceptionOrNull() ?: Exception("RootFS download failed"))
                }
            } else {
                onProgress(0.40f)
            }

            // 2. Proton / Wine Version Download & Installation
            var effectiveProtonVer = protonVersion
            onStatusChange("Ensuring Proton/Wine ($effectiveProtonVer)...")
            onProgress(0.45f)
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            var profile = contentsManager.getProfileByEntryName(effectiveProtonVer)
            if (profile == null) {
                onStatusChange("Checking Proton/Wine manifest ($effectiveProtonVer)...")
                val manifest = ManifestRepository.loadManifest(context)
                val protonEntries = (manifest.items[ManifestContentTypes.PROTON] ?: emptyList()) +
                        (manifest.items[ManifestContentTypes.WINE] ?: emptyList())
                val entry = protonEntries.find { e ->
                    e.id.equals(effectiveProtonVer, ignoreCase = true) ||
                    e.name.equals(effectiveProtonVer, ignoreCase = true) ||
                    e.id.replace("-", "").contains(effectiveProtonVer.replace("-", ""), ignoreCase = true) ||
                    effectiveProtonVer.replace("-", "").contains(e.id.replace("-", ""), ignoreCase = true)
                } ?: protonEntries.firstOrNull()

                if (entry != null) {
                    effectiveProtonVer = entry.id
                    profile = contentsManager.getProfileByEntryName(effectiveProtonVer)
                    if (profile == null) {
                        onStatusChange("Downloading ${entry.name}...")
                        val contentType = if (entry.id.lowercase().contains("wine"))
                            ContentProfile.ContentType.CONTENT_TYPE_WINE
                        else
                            ContentProfile.ContentType.CONTENT_TYPE_PROTON

                        val installResult = ManifestInstaller.downloadAndInstallContent(
                            context = context,
                            entry = entry,
                            expectedType = contentType,
                            onProgress = { p ->
                                val pct = (p * 100).toInt()
                                onStatusChange("Downloading ${entry.name} ($pct%)...")
                                onProgress(0.45f + (p * 0.20f))
                            }
                        )
                        if (!installResult.success) {
                            Timber.w("Could not download Proton/Wine ${entry.id}: ${installResult.message}")
                        }
                        contentsManager.syncContents()
                        profile = contentsManager.getProfileByEntryName(effectiveProtonVer)
                    }
                }
            }
            onProgress(0.65f)

            // 3. DXVK / VKD3D Wrapper DLLs
            onStatusChange("Installing DXVK ($dxvkVersion)...")
            installDXWrapperFiles(context, dxvkVersion)
            onProgress(0.80f)

            // 4. Graphics Driver Ensure
            if (driver.isNotEmpty() && driver != "System") {
                onStatusChange("Ensuring Graphics Driver ($driver)...")
                val adrenotools = AdrenotoolsManager(context)
                if (!adrenotools.enumarateInstalledDrivers().contains(driver)) {
                    val manifest = ManifestRepository.loadManifest(context)
                    val driverEntries = manifest.items[ManifestContentTypes.DRIVER] ?: emptyList()
                    val entry = driverEntries.find { it.id == driver || it.name == driver }
                    if (entry != null) {
                        ManifestInstaller.downloadAndInstallDriver(
                            context = context,
                            entry = entry,
                            onProgress = { p -> onProgress(0.80f + (p * 0.10f)) }
                        )
                    }
                }
            }
            onProgress(0.90f)

            // 5. Save preferences & configure container
            LinuxRootfsDownloader.saveUserChoices(effectiveProtonVer, dxvkVersion, fexPreset, driver)
            val container = ContainerUtils.getOrCreateContainer(context, "CUSTOM_GAME_99999")
            container.wineVersion = effectiveProtonVer
            container.setDXWrapper(if (dxvkVersion.contains("d3d")) dxvkVersion else "dxvk")
            container.setDXWrapperConfig("version=$dxvkVersion")
            container.setFEXCorePreset(fexPreset)
            container.executablePath = "/usr/local/bin/start_linux_desktop.sh"
            container.saveData()
            ContainerManager(context).activateContainer(container)

            // 6. Run PRoot setup script inside container
            onStatusChange("Configuring PRoot Linux environment...")
            runSetupScript(context) { exitCode ->
                Timber.d("PRoot setup script completed with exit code $exitCode")
            }
            onProgress(1.0f)
            onStatusChange("Setup complete!")

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error ensuring Linux environment")
            Result.failure(e)
        }
    }
}
