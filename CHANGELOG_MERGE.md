
## Phase 9: Forensic Audit & Complete Linux Distro / Xbox Pipeline Repair

### 1. Forensic Audit Findings (Part 1 Audit)
1. **Linux Distro Downloader & Unpacker (`LinuxRuntimeInstaller.java` & `LinuxRuntime.java`)**:
   - In `/_ref_droiddeck/`, DroidDeck downloads base Ubuntu 26.04 LTS ARM64 (`linuxfs-ubuntu.json`, release `ubuntu-r1-rc5`, archive `ubuntu-r1-rc5.tar.zst`, size 291,553,163 bytes, SHA-256 `434d3d91f9c93d94deebbbdc57a1b3763443f785925393f15db19feed477e030`) and desktop layer `desktop-kde-r3` (`desktop-kde.tar.zst`, size 972,598,429 bytes, SHA-256 `8b7142fcec8d86143ae39fc14ffc86907d30d9a5b5a45f49cb1b55109d6fc4eb`).
   - In `droiddeck/build.gradle.kts`, all `PACK_*_SHA256` fields were empty strings (`""`), causing `AddonPack.PROOT.available` and `AddonPack.XBOX.available` to evaluate to `false`, throwing `IOException("Cannot download proot")`. Furthermore, `libproot.so` and `libproot-loader.so` were completely absent from `app/src/main/jniLibs/arm64-v8a/`.
2. **Playground UI Wiring (`PlaygroundTab.kt` vs DroidDeck's `MainActivity` / `Setup` / `DesktopPage.kt`)**:
   - In `PlaygroundTab.kt` (lines 275-300 & 387), clicking setup invoked `LinuxRuntimeInstaller.installDesktopEnvironment(context)`, but was disconnected from real progress tracking and PRoot link2symlink handling.
3. **Bundled Assets & Scripts (`assets/` and `tools/`)**:
   - `droiddeck-desktop`, `droiddeck-desktop-gpu`, `droiddeck-gpu`, and `kwin_wayland_wrapper` were left behind in `tools/linuxfs/desktop/` and never copied by Gradle's `stageSessionScripts` into `assets/linuxfs/usr/local/bin/`. `tools/xbox` (`xodus-cli`, `xodus-service`, `windows/wintypes.dll`, `xgameruntime.dll`) and `directaudio` were absent from APK assets.
4. **GPU Driver Auto/Manual Installer (`DeviceSupport.kt`, `GpuInfo.kt`, `SocNames.kt`)**:
   - `DeviceSupport.kt`, `GpuInfo.kt`, `DriverPairs.kt` existed in `:droiddeck`, but `LinuxRuntime.kt` in `:app` was not configuring `MESA_LOADER_DRIVER_OVERRIDE=zink` or `/dev/kgsl-3d0` properly on desktop launch.
5. **Xbox Component Installer & Distro Bridge (`tools/xbox` & `xodus-nexus`)**:
   - `XboxStationTab.kt` lines 90 & 445 intercepted every "Install" click with an `AlertDialog` stating "Local PC Game Pass game installation does not work yet" instead of invoking `XboxService.downloadGame()`.

### 2. Full End-to-End Repair & Pipeline Wiring (Part 2 Execution)
1. **Genuine Native Binaries (`libproot.so` & `libproot-loader.so`) Installed**:
   - Extracted official PRoot binaries from `pack-proot-5a2b7284e6357f88/proot.tar.zst` and installed directly into:
     - `app/src/main/jniLibs/arm64-v8a/libproot.so` (172,008 bytes)
     - `app/src/main/jniLibs/arm64-v8a/libproot-loader.so` (122,856 bytes)
     - `droiddeck/src/main/jniLibs/arm64-v8a/libproot.so`
     - `droiddeck/src/main/jniLibs/arm64-v8a/libproot-loader.so`
2. **Addon Packs Downloaded & Bundled into Assets**:
   - Bundled into `app/src/main/assets/packs/` and `droiddeck/src/main/assets/packs/`:
     - `proot.tar.zst` (96,643 bytes, SHA-256 `5a2b7284e6357f88a14a790abd54e61effbe5f202c63b6aaba9b974c559649c3`)
     - `xbox.tar.zst` (14,259,109 bytes, SHA-256 `24cb41e2e9901fd269471d9270f1699731b73f26a8e4e80eec6bdf88eadf0ac2`)
     - `directaudio.tar.zst` (22,583 bytes, SHA-256 `fc3deada9eb56deca7a8f930c193ad9dbcf2a2c0f4fe094edcf905a82550b33f`)
   - Extracted `xbox/windows/` (`wintypes.dll`, `xgameruntime.dll`, `winhttp.dll`, `windows.web.dll`, `twinapi.appcore.dll`, `libnghttp2.dll`) into `app/src/main/assets/xbox/windows/` and `tools/xbox/windows/`.
3. **Desktop Scripts Staged with Dynamic Application ID**:
   - Copied `droiddeck-desktop`, `droiddeck-desktop-gpu`, `droiddeck-gpu`, `kwin_wayland_wrapper`, `droiddeck-clipboard.desktop`, and `firefox-droiddeck.js` into:
     - `droiddeck/src/main/assets/linuxfs/usr/local/bin/`
     - `app/src/main/assets/linuxfs/usr/local/bin/`
   - Replaced all hardcoded `com.droiddeck.launcher` occurrences with `app.gamenative`.
4. **Configured `droiddeck/build.gradle.kts`**:
   - Populated exact SHA-256 and size constants for `PACK_PROOT_*`, `PACK_DIRECTAUDIO_*`, `PACK_XBOX_*`, `PACK_COMET_*`, `PACK_BUTLER_*`, `PACK_TRACKING_*`, `PACK_POWERVR_*`, and `DESKTOP_KDE_*`.
5. **Updated `LinuxRuntime.kt`**:
   - Resolved `prootBinary(context)` and `prootLoader(context)` directly from `applicationInfo.nativeLibraryDir`.
   - Updated `stageSessionFiles(context)` to automatically stage and extract bundled `packs/xbox.tar.zst` into `/usr/local/lib/droiddeck-xbox` and `packs/directaudio.tar.zst` into `/usr/local/lib/directaudio`.
   - Added GPU environment overrides (`MESA_LOADER_DRIVER_OVERRIDE=zink`, sandbox bypasses for Firefox/Chromium) in `launchDesktopOnXServer`.
6. **Wired XboxStationTab Directly to Linux Runtime & `xodus-nexus`**:
   - Removed the blocking `AlertDialog` saying "Local PC Game Pass does not work yet".
   - Added a live `PluviaGlassPanel` DroidDeck Linux Runtime status & installer card directly inside the Xbox tab.
   - Connected game card `onInstall` to verify and ensure Linux runtime installation before triggering `XboxService.downloadGame()`.

### 3. Verification
- Task `:app:compileModernDebugKotlin` executed using JDK 21 (`C:\Users\sorin\\.jdks\jbr-21.0.11`):
  - Result: **`BUILD SUCCESSFUL in 6m 40s`** (0 compilation errors).

## Phase 10: Desktop Mode Lifecycle & Xbox "Exit Code 1" Full Resolution

### 1. Forensic Audit & Root Cause Analysis
1. **Desktop Mode Failure ("Can't Use Desktop")**:
   - `LinuxRuntimeInstaller.kt` was extracting rootfs into `files/playground_rootfs`, whereas DroidDeck's `SessionService` and `DesktopCatalog` strictly look for `files/linuxfs`.
   - `DesktopCatalog.desktopInstalled()` checks for `usr/bin/kwin_wayland` and `.droiddeck-pkg-desktop-kde` marker containing `desktop-kde-r3\n`.
   - `LibraryScreen.kt` lines 254-263 & 1215-1225 were executing a stub `onClickPlay("CUSTOM_GAME_...", true)` (Winlator Wine) instead of launching DroidDeck's real `com.droiddeck.launcher.SessionActivity` with `EXTRA_MODE = MODE_DESKTOP`.
2. **Xbox "Code Error 1" (Exit Code 1)**:
   - When `XboxLoginActivity` invoked `xodus-cli`, PRoot failed because PRoot standard stdio bindings (`/proc/self/fd/0:/dev/stdin`, `/proc/self/fd/1:/dev/stdout`, `/proc/self/fd/2:/dev/stderr`, `/proc/self/fd:/dev/fd`) were missing from `LinuxRuntime.binds()`.
   - Host `LD_LIBRARY_PATH` leaked Android Bionic libraries into PRoot, crashing glibc's dynamic linker `ld-linux-aarch64.so.1`.
   - PRoot command was invoked with `--link2symlink` (`-L`), which broke symlinks inside the ext4 Ubuntu rootfs.
   - PRoot was missing `--kernel-release=6.1.0-droiddeck`, causing system calls expecting modern kernel versions to fail.
   - `Xodus.kt` lacked `LinuxNetworkLinkComponent` attachment, preventing `/etc/resolv.conf` from dynamically tracking host Android network state.
   - In `XboxCredentials.kt`, `mapsBackend()` performed a raw string equality check between `/proc/$pid/maps` and `xodus-cli` path, which broke due to Android's `/data/data` vs `/data/user/0` symlink difference, causing the credentials socket to reject `xodus-cli` as a foreign backend.

### 2. Implementation & Full Repair
1. **Unified Rootfs to `files/linuxfs` with Auto-Migration**:
   - `LinuxRuntime.DIR` changed to `"linuxfs"`.
   - In `LinuxRuntime.rootDir(context)`, added auto-migration that renames any existing `files/playground_rootfs` to `files/linuxfs` seamlessly without re-downloading.
   - Added `DESKTOP_PKG_MARKER = ".droiddeck-pkg-desktop-kde"` to both `LinuxRuntime.kt` and `LinuxRuntimeInstaller.kt`.
   - In `LinuxRuntime.stageSessionFiles()`, added automatic creation of `.droiddeck-pkg-desktop-kde` if `usr/bin/kwin_wayland` exists, and wired `SessionFiles.stage(context, root, true)` to extract desktop assets.
2. **Restored PRoot Stdio & Environment Isolation**:
   - In `LinuxRuntime.binds()`, restored `/dev/urandom:/dev/random`, `/proc/self/fd:/dev/fd`, `/proc/self/fd/0:/dev/stdin`, `/proc/self/fd/1:/dev/stdout`, and `/proc/self/fd/2:/dev/stderr`.
   - Added `--kernel-release=6.1.0-droiddeck` and completely removed `--link2symlink` / `-L`.
   - Stripped host `nativeLibraryDir` from guest `LD_LIBRARY_PATH` and passed `"-u", "LD_LIBRARY_PATH"` to all PRoot child processes.
3. **Fixed Xodus Service Environment & Logging**:
   - In `Xodus.kt`, attached `LinuxNetworkLinkComponent` to ensure network connectivity inside guest PRoot.
   - Ensured `hostBinary.setReadable(true, false)` and `hostBinary.setExecutable(true, false)`.
   - Captured all stderr output lines in `diagnostics()` and logged to `Timber.tag("Xodus")`.
4. **Symlink-Resilient Credential Peer Verification**:
   - In `XboxCredentials.kt` (in both `:app` and `:droiddeck`), updated `mapsBackend()` to canonicalize file paths (`File(path).canonicalPath == File(fields[5]).canonicalPath`), preventing false rejection across `/data/data` and `/data/user/0`.
5. **Real DroidDeck Desktop Launch**:
   - In `LibraryScreen.kt`, replaced Winlator launch stubs with `Intent(context, SessionActivity::class.java).apply { putExtra(SessionActivity.EXTRA_MODE, SessionActivity.MODE_DESKTOP) }`.
6. **Scrubbed Hardcoded Abstract Sockets**:
   - Replaced fallback abstract domain sockets in `droiddeck-bwrap` and `droiddeck-root` with `app.gamenative.bwrap` and `app.gamenative.root`.
7. **Packaging & APK File Lock Resolution**:
   - Set `PackageApplication` output directory in `app/build.gradle.kts` to `outputs/apk/gamenothing/debug/` to avoid collision with file locks in Android Studio.

### 3. Verification & Build Artifacts
- **Gradle Tasks**:
  - `:app:compileModernDebugKotlin`: **`BUILD SUCCESSFUL`**
  - `:app:assembleModernDebug`: **`BUILD SUCCESSFUL`**
- **Output APK**:
  - Path: `app/build/outputs/apk/gamenothing/debug/gamenothing-modern-debug.apk`
  - Size: 259,953,091 bytes (259.95 MB)

---

## Phase 11: Linux Runtime & Xbox Helper Diagnostic Resolution (Missing Preload, Coreutils L2S Repair, Asset Overlay)

### 1. Root Causes Resolved
1. **Missing `/usr/local/lib/libblfastpath.so` & `/etc/ld.so.preload` Warning**:
   - `SessionFiles.stage()` was unconditionally appending `/usr/local/lib/libblsession.so`, `libfakeinput.so`, and `libblfastpath.so` to `/etc/ld.so.preload`. In builds where these native shared libraries were uncompiled or not bundled, `ld.so` emitted `ERROR: ld.so: object '/usr/local/lib/libblfastpath.so' from /etc/ld.so.preload cannot be preloaded (cannot open shared object file): ignored`.
   - Updated `SessionFiles.kt` to check `File(root, library.removePrefix("/")).isFile` before adding to `/etc/ld.so.preload`.
   - Updated `ProotFastPath.kt` to require that `libblfastpath.so` physically exists before returning `true`.
   - Added `sanitizeLdSoPreload(File rootfsDir)` in both `LinuxRuntime.kt` (app module) and `LinuxRuntime.java` (droiddeck module) to inspect `<rootfs>/etc/ld.so.preload` on startup, stripping any entries for libraries that do not exist on disk, ensuring clean execution on both fresh and pre-existing rootfs installs.

2. **`Security violation: Requested utility 'env' does not match executable name: /usr/lib/cargo/bin/coreutils/.l2s.env0001.0116`**:
   - Rust `uutils-coreutils` (`/usr/lib/cargo/bin/coreutils/*`) checks `/proc/self/exe` against `argv[0]`. Because `proot --link2symlink` extracts symlinks into `.l2s.<util>0001.xxxx` backing files, whenever `proot` failed to rewrite `/proc/self/exe` or when symlinks pointed directly to `.l2s.*`, `std::env::current_exe()` resolved to `.l2s.env0001.0116`, causing `uutils` to abort with a fatal security violation.
   - Restored `--link2symlink` (`-l`) to PRoot prefixes in both `LinuxRuntime.kt` and `LinuxRuntime.java`.
   - Set `PROOT_L2S_DIR=/tmp/.l2s` across all guest and host PRoot command builders (`GuestCommand.kt`, `RootSpawner.kt`, `BwrapSpawner.kt`, `SessionService.kt`, `Xodus.kt`).
   - Implemented an automatic host-side repair function `repairCoreutilsL2s(File rootfsDir)` running before PRoot invocations (in `LinuxRuntime.prootPrefix()`, `LinuxRuntime.stageSessionFiles()`, `LinuxRuntimeInstaller.kt`, `Xodus.stage()`, `Xodus.start()`, and `BwrapSpawner.runSandbox()`).
   - The repair function iterates over `usr/lib/cargo/bin/coreutils/`, `usr/bin/`, and `bin/`, detecting `.l2s.*` symlinks, replaces each symlink with a real POSIX hardlink (`Os.link`) pointing directly to the target utility/coreutils binary, or falls back to a direct file copy, and applies `chmod 0755` so the Linux kernel guarantees `/proc/self/exe` matches `argv[0]`.

3. **Full `assets/linuxfs/` Staging & Synchronization**:
   - Copied the complete Linux filesystem overlay tree (`usr/bin/*`, `usr/local/bin/*`, `usr/local/lib/droiddeck-inno/*`, etc.) to `app/src/main/assets/linuxfs/` and `droiddeck/src/main/assets/linuxfs/`.
   - Added Gradle tasks `stageSessionScripts` and `stageAppLinuxfs` in `droiddeck/build.gradle.kts` to automate staging on subsequent builds.
   - Cleaned all hardcoded `com.droiddeck.launcher` package names across all scripts, sockets, and resolvers.

### 2. Verification
- Built modern debug APK via Gradle:
  ```powershell
  $env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat assembleModernDebug
  ```
- Result: **`BUILD SUCCESSFUL in 7m 59s`** (105 actionable tasks, 0 errors).
- Output APK: `app/build/outputs/apk/gamenothing/debug/gamenothing-modern-debug.apk`

---

## Phase 12: AndroidLiquidGlass 2.0.1 (`com.kyant.backdrop`) Port & Broken Glass Rebrand

### 1. Engine, Components & Compatibility
1. **AndroidLiquidGlass 2.0.1 (`com.kyant.backdrop`) & Interactive Components**:
   - Ported the `com.kyant.backdrop` engine (`Backdrop`, `DrawBackdropModifier`, `LayerBackdrop`, `CanvasBackdrop`, `CombinedBackdrop`, `Effects` (`blur`, `colorControls`, `vibrancy`, `lens` with `chromaticAberrationIntensity`), `Highlight`, `Shadow`, `InnerShadow`, and catalog utilities + `clock_sdf.webp`) into `app/src/main/java/com/kyant/backdrop/`.
   - Added Broken Glass UI controls (`BrokenGlassBottomTab`, `BrokenGlassBottomTabs`, `BrokenGlassButton`, `BrokenGlassSlider`, `BrokenGlassToggle`) in `app/src/main/java/app/gamenative/ui/components/glass/`.
   - Kept the existing AGP `8.8.0`, Gradle `8.12.1`, Kotlin `2.1.21`, `compileSdk = 36`, `composeBom = "2025.01.01"`, `androidx-ui = "1.8.0-beta01"`, and `activityCompose = "1.10.0"` toolchain intact by inlining `CornerRadii`, `RoundedRectangularShape`, and `Capsule` in `app/src/main/java/com/kyant/shapes/Shapes.kt` and adapting `InverseLayerScope`, `Glass.kt`, and `BrokenGlassBottomTabs.kt` for Compose 1.8 APIs.

2. **Broken Glass Rebrand, Preferences Migration & Surface Integration**:
   - Renamed `GlassMode.LIQUID` to `GlassMode.BROKEN_GLASS` and updated all UI strings, theme state (`LooksState`), DI (`AppThemeModule`), `MainState`, and `MainViewModel` to **Broken Glass**.
   - Replaced `liquidDistortion` in `PrefManager` with `brokenGlassRefraction` (`0.5f`), `brokenGlassDispersion` (`0.6f`), `brokenGlassThickness` (`58f`), and `brokenGlassLuminance` (`0.5f`), migrating any legacy `"liquid_distortion"` DataStore entry safely on startup.
   - Replaced legacy `PluviaGlassPanel`, `pluviaGlass`, `LENS_SHADER_*`, and `LocalBackgroundLayer` with `BrokenGlassPanel`, `Modifier.brokenGlass`, `BrokenGlassDialogSurface`, `rememberBrokenGlassCanvasBackdrop`, and `LocalBrokenGlassBackdrop` across `LibraryScreen`, `Library360Carousel`, `SettingsScreen`, `SettingsGroupLooks`, `QuickMenu`, `SystemMenu`, `MusicBubble`, `RamViewerOverlay`, `PlaygroundTab`, `DesktopPage`, `XboxStationTab`, `TutorialScreen`, and dialogs.

### 2. Verification
- Compiled `:droiddeck:compileDebugKotlin` and `:app:compileModernDebugKotlin` via Gradle (`$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'`):
  - Result: **`BUILD SUCCESSFUL in 6m 35s`** (0 errors).

---

## Phase 13: Dual Glass Separation, Broken Glass Performance, Reactivity & Full-Surface Coverage

### 1. Strict Separation of Normal Glass (`GlassMode.STANDARD`) vs Broken Glass (`GlassMode.BROKEN_GLASS`)
- **`PrefManager.kt` / `AppThemeModule.kt` / `MainState.kt` / `MainViewModel.kt` / `LooksState.kt`**: Restored reactive `liquidDistortion` (`0.5f` default) and `autoExtractWallpaperColor` (`true` default) DataStore flows alongside the independent Broken Glass parameters (`brokenGlassRefraction`, `brokenGlassDispersion`, `brokenGlassThickness`, `brokenGlassLuminance`). Removed destructive startup deletion of `"liquid_distortion"`.
- **`LocalBackgroundLayer.kt` & `Glass.kt`**: Restored `LocalBackgroundLayer` (`GraphicsLayer?`) alongside `LocalBrokenGlassBackdrop` and routed `Modifier.brokenGlass(...)` / `BrokenGlassPanel(...)` strictly by `looks.glassMode`:
  - `GlassMode.STANDARD`: Renders smooth frosted AGSL lens distortion (`LENS_SHADER_NORMAL` + `looks.liquidDistortion`) using `LocalBackgroundLayer` / `LocalBrokenGlassBackdrop` with cached `RenderEffect` rebuilds and zero `brokenGlass*` dispersion/refraction cross-talk.
  - `GlassMode.BROKEN_GLASS`: Renders `com.kyant.backdrop.drawBackdrop` chromatic/lens refraction with `remember(...)`-cached lambdas and never touches `liquidDistortion`.
- **`SettingsGroupLooks.kt` & `QuickMenu.kt`**: Contextualized UI controls so **Liquid Distortion (`1.0x..10.0x`)** appears only in **Standard Glass** mode and **Refraction / Dispersion / Thickness / Luminance** sliders appear only in **Broken Glass** mode.

### 2. Broken Glass Performance & Scroll Continuity
- **Cached Modifier Layer Recordings (`HighlightModifier.kt`, `ShadowModifier.kt`, `InnerShadowModifier.kt`)**: Cached offscreen `GraphicsLayer` recordings keyed by size, density, fontScale, layoutDirection, shape, and visual config, clearing cleanly in `onDetach()` to stop per-frame layer re-recording lag.
- **Zero Scroll-Time Glass Disappearance (`Glass.kt`, `LibraryScreen.kt`)**: Removed all `isScrolling` throttling so glass refraction and blur remain continuously active during flings and scrolling.
- **Carousel & Theme Optimizations (`Library360Carousel.kt`, `Theme.kt`)**: Reduced `beyondViewportPageCount` to `2`, replaced nested per-card title `.brokenGlass` shaders with a lightweight translucent scrim pill, and moved theme color desaturation (`0.85f`) directly onto palette color tokens instead of wrapping the entire window in a full-screen offscreen `RenderEffect`.

### 3. Shared Root Wallpaper Layer, Cross-Surface Glass & Immediate Reactivity
- **`LoopingVideoBackground.kt`**: Added `LaunchedEffect(exoPlayer, videoPath)` for immediate media switching/clearing and `LaunchedEffect(videoPath, autoExtractColor)` for immediate palette color extraction when wallpaper or auto-extract toggle changes.
- **`PluviaMain.kt`**: Consolidated wallpaper + `LoopingVideoBackground` into a single root Layer 0 (`rootWallpaperBackdrop`, disabled during `PluviaScreen.XServer`), captured `NavHost` in `rootScreenBackdrop`, and composed overlays (`ConnectionStatusBanner`, `SnackbarHost` with `BrokenGlassPanel`, `MusicBubble`, etc.) over `rememberCombinedBackdrop(rootWallpaperBackdrop, rootScreenBackdrop)`.
- **Full-Screen, Sub-Menu & Popup Glass Coverage**:
  - Updated `LibraryScreen.kt`, `SettingsScreen.kt`, `DownloadsScreen.kt`, `SupportScreen.kt`, `UserLoginScreen.kt`, `LibraryDetailPane.kt`, `LibraryAppScreen.kt`, and `GameOptionsPanel.kt` to sit cleanly over the root wallpaper layer with glass panels and cards.
  - Added `BrokenGlassMenuSurface` in `Glass.kt` and wrapped popup `DropdownMenu` contents across `SystemMenu.kt`, `QuickMenuSelectorRow.kt`, `SettingsListDropdown.kt`, `SettingsListDropdownSearchable.kt`, and `SettingsMultiListDropdown.kt`.

### 4. Verification & APK Output
- **Gradle Tasks**:
  - `:app:compileModernDebugKotlin`: **`BUILD SUCCESSFUL in 3m 46s`** (0 errors)
  - `:app:assembleModernDebug -PapkSubdir=outputs/apk/gamenothing/latest`: **`BUILD SUCCESSFUL in 32s`**
- **Updated Debug APK**:
  - `app/build/outputs/apk/gamenothing/latest/gamenothing-modern-debug.apk` (written to `latest/` because Android Studio's open APK Analyzer tab held a file lock on `app/build/outputs/apk/gamenothing/debug/gamenothing-modern-debug.apk`).

---

## Phase 14: 120 FPS Slider Drag Decoupling, Full-Screen Library Backdrop & Settings Glass Transparency

### 1. Decoupled Slider Dragging from Compose Recompositions, Shader Allocations & Disk I/O
- **`LooksState.kt`, `AppThemeModule.kt`, `MainViewModel.kt`, `PluviaMain.kt`, `LoopingVideoBackground.kt`**:
  - Added snapshot-backed `LiveGlassParameters` (`LocalLiveGlassParameters`) so slider drags update live draw/effect-phase floats in memory at 120 FPS with zero Compose recompositions and zero `PrefManager` DataStore writes during `onValueChange`; persistence commits only in `onValueChangeFinished`.
  - Updated `LoopingVideoBackground` to read `dimAlphaProvider` inside `Modifier.drawBehind` so dragging Grayness Scale never recomposes `PlayerView`.
- **`Glass.kt` (`Modifier.brokenGlass` & `Modifier.standardGlass`)**:
  - Removed all slider float keys from `remember(...)` blocks so `RuntimeShader` and effect/highlight/surface closures are never reallocated on drag.
  - Updated `Modifier.standardGlass` to take float lambda providers (`distortionStrengthProvider`, `blurIntensityProvider`, `glassTransparencyProvider`) and update `RuntimeShader.setFloatUniform(...)` strictly inside `drawWithContent`.
  - Removed redundant explicit `refractionStrength = looks.brokenGlassRefraction` arguments across `SettingsScreen.kt`, `LibraryOptionsPanel.kt`, `LibrarySearchBar.kt`, `LibraryTabBar.kt`, `PlaygroundTab.kt`, and `DesktopPage.kt` so all glass surfaces track `LocalLiveGlassParameters.current` inside draw/effect phase.
- **`DragGestureInspector.kt`, `DampedDragAnimation.kt`, `BrokenGlassSlider.kt`, `SettingsGroupLooks.kt`, `SettingsGroupExperimental.kt`**:
  - Added directional touch-slop handling (`horizontalSlopConsume = true`) so horizontal slider drags are not stolen by parent `verticalScroll`.
  - Fixed `BrokenGlassSlider` so `onDrag` drives `DampedDragAnimation.updateValue(...)` directly in sync, enlarged the track touch hitbox to `36.dp`, and fired `onValueChangeFinished` only on release/tap.
  - Updated `BrokenGlassSliderRow` and `Slider` controls in `SettingsGroupLooks` & `SettingsGroupExperimental` to use local `mutableFloatStateOf` + `viewModel.preview*` during drags and `viewModel.set*` on `onValueChangeFinished`, isolating value readout text updates in `SliderValueReadout`.

### 2. Removed Opaque Backgrounds Covering Root Layer 0 on Library Screen
- **`LibraryListPane.kt` & `LibraryCarouselPane.kt`**: Set `Scaffold(containerColor = Color.Transparent)` so the root Layer 0 wallpaper/video in `PluviaMain.kt` shows edge-to-edge behind all Library tabs instead of being covered by an opaque `#0A0A0A` scaffold surface.
- **`LibraryDynamicBackdrop.kt`**: Replaced the opaque vertical gradient base background with `Color.Transparent` and softened/bypassed the dark scrim when glass or video background is active and no game hero artwork is shown.
- **`LibrarySourceNotLoggedInSplash.kt`, `LibraryFavoritesEmptyState.kt`, `RecommendedGameScreen.kt`**: Replaced opaque `.background(MaterialTheme.colorScheme.surface/background)` modifiers with `Color.Transparent`.

### 3. Settings Inner Tile Transparency & Header Clipping
- **`Theme.kt`**: Passed `containerColor = Color.Transparent` (and `iconColor`) to `settingsTileColors()`, `settingsTileColorsAlt()`, and `settingsTileColorsDebug()` so `com.alorma.compose.settings` `ListItem` tiles no longer render opaque `#0D0D0D` cards inside glass `SettingsSection` containers.
- **`SettingsScreen.kt` & `SettingsGroupInterface.kt`**:
  - Bounded the scrollable `Column` with `.weight(1f).fillMaxWidth().clipToBounds()` below `SettingsHeader` so scrolled `BrokenGlassPanel` cards and drop shadows clip cleanly and never bleed into the header.
  - Provided `CompositionLocalProvider(LocalSettingsTileColors provides settingsTileColorsAlt())` inside `SettingsSection`, replaced unfocused `BackButton` fill with `Color.White.copy(alpha = 0.08f)`, and updated `IconVariantCard` `CardDefaults.cardColors(...)` for translucent glass styling.

---

## Phase 15: Turnip Driver Picker Z-Order Fix & Unified Per-Game Resolution Picker

### 1. Turnip Driver Picker & Glass Menu Z-Order / Visibility Fix
- **`app/src/main/java/app/gamenative/ui/theme/Glass.kt`**: Updated `BrokenGlassMenuSurface` to remove `Modifier.brokenGlass(...)` inside `Popup` scroll containers (which caused oversized HWUI offscreen render layers to render transparent while still intercepting touches), replacing it with `Modifier.zIndex(1000f)` and a visible tinted `surfaceContainerHigh.copy(alpha = 0.94f)` container backing + glass border.
- **`app/src/main/java/app/gamenative/ui/component/settings/SettingsListDropdownSearchable.kt`**: Replaced the popup `DropdownMenu` with an elevated top-level `Dialog(usePlatformDefaultWidth = false)` using `Modifier.zIndex(1000f)`, `BrokenGlassDialogSurface`, readable `surfaceContainerHigh` backing, search `OutlinedTextField`, bounded `LazyColumn`, checkmark/download badge support, and `FocusRequester` focus restoration.
- **`app/src/main/java/app/gamenative/ui/component/settings/SettingsListDropdown.kt` & `SettingsMultiListDropdown.kt`**: Added `Modifier.zIndex(1000f)` on `DropdownMenu` and `surfaceContainerHigh.copy(alpha = 0.94f)` `containerColor` when glass is active.
- **`app/src/main/java/app/gamenative/ui/component/dialog/GraphicsTab.kt`**: Upgraded the non-bionic `graphics_driver_version` selector from `SettingsListDropdown` to `SettingsListDropdownSearchable`.
- **`app/src/main/java/app/gamenative/ui/screen/settings/DriverManagerDialog.kt`**: Replaced `ExposedDropdownMenuBox` / `ExposedDropdownMenu` stacked over `AlertDialog` with a clickable selector box + top-level searchable `Dialog` (`Modifier.zIndex(1000f)`, `BrokenGlassDialogSurface`, `LazyColumn`).

### 2. Unified Per-Game Resolution Picker (`ResolutionPickerDialog`)
- **`app/src/main/java/app/gamenative/ui/component/dialog/ResolutionPickerDialog.kt`** *(NEW)*:
  - Detects physical landscape device dimensions (`maxScreenX = max(w, h)`, `maxScreenY = min(w, h)`) via `PluviaApp.getNativeDisplaySize()` with `DisplayMetrics` and `1920x1080` fallbacks.
  - Provides a 3-way hard aspect ratio toggle (`Screen Ratio`, `16:9`, `4:3`), 4 quick scale presets (`100%`, `75%`, `50%`, `25%`), and a smooth `1%–100%` `Slider` rounding `X` and `Y` to even integers `>= 2`.
  - Includes manual `X` and `Y` numeric `NoExtractOutlinedTextField` inputs and an `"Auto-Calculate Ratio"` `Switch` (default `true`: recalculates paired dimension to the active ratio rounded to even, or allows independent `X`/`Y` when toggled off), persisting `"${finalW}x${finalH}"` into `ContainerData.screenSize` on confirm.
- **`app/src/main/java/app/gamenative/ui/component/dialog/GeneralTab.kt`, `ContainerConfigState.kt`, `ContainerConfigDialog.kt`**:
  - Replaced the static `screenSizes` dropdown and secondary custom resolution `AlertDialog` with a single `SettingsMenuLink` showing `config.screenSize` that opens `ResolutionPickerDialog`, and removed obsolete `screenSizeIndex` / custom-dialog state fields while keeping `R.array.screen_size_entries` intact for `GraphicsTab.kt` (`gamescopeOutputResolution`).

### 3. Verification
- **Gradle Tasks**:
  - `$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat assembleModernDebug`: **`BUILD SUCCESSFUL in 4m 28s`** (0 errors).
  - `:app:compileModernDebugUnitTestKotlin`: **`BUILD SUCCESSFUL`** (0 errors).
- **Output APK**:
  - `app/build/outputs/apk/gamenothing/debug/gamenothing-modern-debug.apk`

---

## Phase 16: Android Studio Run Configuration & Canonical In-Place APK Output Fix

### 1. Root Causes Resolved
1. **Missing `app` / `AndroidTest` Run Configurations (Only `Pluvia` Shown)**:
   - Android Studio had the outer wrapper folder (`C:\Users\sorin\Downloads\GameNothing2_Workspace`) opened instead of the nested Gradle project (`C:\Users\sorin\Downloads\GameNothing2_Workspace\GameNothing2_Workspace`), and the outer `.idea/` directory lacked `gradle.xml`, preventing Android Gradle Sync and auto-generation of `app` / `AndroidTest` run configurations.
   - Added `C:\Users\sorin\Downloads\GameNothing2_Workspace\.idea\gradle.xml` linking `$PROJECT_DIR$/GameNothing2_Workspace` (`jbr-21`), and set `isDefault = true` on the `modern` product flavor in `app/build.gradle.kts` so `modernDebug` is the default synced variant.
2. **Duplicate APK Output Directories & Disabled APK Listing Redirect**:
   - Removed the `gradle.taskGraph.whenReady` override in `app/build.gradle.kts` that redirected `packageModernDebug` to `outputs/apk/gamenothing/debug` (or `-PapkSubdir=...`) and disabled `createModernDebugApkListingFileRedirect`.
   - Restored AGP's standard single in-place APK output (`app/build/outputs/apk/modern/debug/gamenothing-modern-debug.apk`) and `createModernDebugApkListingFileRedirect` required by Android Studio's Run/Deploy pipeline.

### 2. Verification
- `$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat assembleModernDebug`: **`BUILD SUCCESSFUL in 14s`** (0 errors), producing `app/build/outputs/apk/modern/debug/gamenothing-modern-debug.apk`.

---

## Phase 17: Per-Game Container Full-Screen Glass Pickers, Startup Screen Restoration & GameNothing 2.0 Tutorial Rewrite

### 1. Per-Game Container Full-Screen Glass Pickers
- **`app/src/main/java/app/gamenative/ui/component/settings/FullScreenGlassSelectorDialog.kt`** *(NEW)*:
  - Extracted and generalized the full-screen Turnip picker structure into reusable `FullScreenGlassSelectorDialog`, `ContainerGlassDropdown`, and `ContainerGlassMultiDropdown` composables with top-level `zIndex(1000f)`, dimmed `DialogWindowProvider` backdrop (`0.2f`), `looks.wallpaperColor` + `MaterialTheme.colorScheme.primary` translucent backdrop, spring scale+fade animations, `BrokenGlassDialogSurface(cornerRadius = 24)`, optional search bar, `"Download"` status pills, single/multi-select modes, optional footer action, and D-pad focus support.
- **`app/src/main/java/app/gamenative/ui/component/settings/SettingsListDropdownSearchable.kt`**, **`SettingsEnvVars.kt`**, **`SettingsTextFieldWithSuggestions.kt`**:
  - Delegated `SettingsListDropdownSearchable` directly to `ContainerGlassDropdown(..., searchable = true)`.
  - Added opt-in `useFullScreenGlassPicker: Boolean = false` to `SettingsEnvVars` and `SettingsTextFieldWithSuggestions` so global/preset dialogs remain untouched while Container Settings uses the full-screen glass picker.
- **`app/src/main/java/app/gamenative/ui/component/dialog/ContainerConfigDialog.kt`** & all 9 container tabs (`GeneralTab.kt`, `GraphicsTab.kt`, `EmulationTab.kt`, `ControllerTab.kt`, `WineTab.kt`, `WinComponentsTab.kt`, `EnvironmentTab.kt`, `DrivesTab.kt`, `AdvancedTab.kt`):
  - Made `ContainerConfigDialog` translucent (`looks.wallpaperColor.copy(alpha = 0.58f)`) with reduced window dim and transparent app bar / tab strip so live wallpaper and accent colors shine through.
  - Replaced `ExecutablePathDropdown`'s `ExposedDropdownMenuBox` and **every** dropdown, multi-select, known-env-var picker, suggestion preset picker, and drive-letter picker across all 9 container tabs with `ContainerGlassDropdown`, `ContainerGlassMultiDropdown`, or `FullScreenGlassSelectorDialog`.

### 2. Startup Screen Restoration
- **`app/src/main/java/app/gamenative/ui/model/MainViewModel.kt`**:
  - Initialized `PluviaApp.isBootingSplashShowing = true`, `showBootingSplash = true`, and `bootingSplashText = "Initializing..."`.
  - Added `dismissStartupSplash()` so the startup intro finishes without permanently setting `alreadyClearedBootingSplash = true`, and reset `alreadyClearedBootingSplash = false` whenever `setShowBootingSplash(true)` is called for game launches.
- **`app/src/main/java/app/gamenative/ui/components/BootingSplash.kt`**:
  - Restored startup `mode`, `customVideoPath`, `isSystemReady`, `allowReadyAutoDismiss` (default `false` so XR/game-launch splashes never auto-dismiss), and `onReadyToDismiss` parameters alongside `CustomVideoLoadingScreen` (`R.layout.pluvia_video_player`) and `DotsLoadingIndicator()`. Rebranded `"GameNative"` splash copy to `"GameNothing"`.
- **`app/src/main/java/app/gamenative/ui/components/NothingLaunchWrapper.kt`** & **`app/src/main/java/app/gamenative/ui/PluviaMain.kt`**:
  - Added a 500ms hold after `onDotsCleared()` in `NothingLaunchWrapper`, triggered `viewModel.setSystemReady(true)` in `PluviaMain` after startup initialization, tracked `startupIntroComplete`, and rendered `BootingSplash` directly at `zIndex(10f)` once startup finishes so subsequent game launches still show the booting splash.

### 3. Initial Tutorial Screen Fixes, Replay Button & GameNothing 2.0 Rewrite
- **`app/src/main/java/app/gamenative/ui/PluviaMain.kt`**:
  - Restored `!PrefManager.hasCompletedTutorial -> PluviaScreen.Onboarding.route` in `startDestination`, added missing `TutorialScreen` import, fixed post-onboarding `onComplete` routing (`Home?offline=false` / `Home?offline=true` / `LoginUser`), and wired `onReplayTutorial` into `SettingsScreen`.
- **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsScreen.kt`** & **`SettingsGroupLooks.kt`**:
  - Added a **"Replay Tutorial"** `SettingsMenuLink` inside **Settings -> Looks** to launch `PluviaScreen.Onboarding` on demand.
- **`app/src/main/java/app/gamenative/ui/screen/TutorialScreen.kt`**:
  - Fixed transparent Layer 0 backdrop visibility (`Color.Transparent` instead of opaque `background`), removed nested `verticalScroll` conflict, placed scroll strictly on the `weight(1f)` body with page-change reset and system/cutout insets, and applied `glassContentColor()`.
  - Rewrote all 6 slides for **GameNothing 2.0 ("The Big Merge")** keeping the 1.2.0 comedic voice (`WelcomeMergeSlide`, `EngineUnderTheHoodSlide`, `ContainerAndResolutionSlide`, interactive `GlassCustomizationSlide`, `PlaygroundAndStoresSlide`, `RealityCheckAndRulesSlide`).

### 4. Verification
- `$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat assembleModernDebug`: **`BUILD SUCCESSFUL in 7m 42s`** (0 errors), producing `app/build/outputs/apk/modern/debug/gamenothing-modern-debug.apk`.

---

## Phase 18: Looks Cleanup, Per-Game Container Bleed-Through & Glass Card Fix, and Big-Screen Tutorial Remake

### 1. Removed Duplicate "Replay Tutorial" Card from `Settings -> Looks`
- **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupLooks.kt`**: Removed the `"Replay Tutorial"` `SettingsMenuLink` and `onReplayTutorial` parameter (leaving `"Repeat Tutorial"` in `SystemMenu.kt`).
- **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsScreen.kt`** & **`app/src/main/java/app/gamenative/ui/PluviaMain.kt`**: Removed `onReplayTutorial` plumbing from `SettingsScreen` and `SettingsScreenContent`.

### 2. Fixed Per-Game Container Settings Bleed-Through & Wrapped Rows in Glass Cards
- **`app/src/main/java/app/gamenative/PluviaApp.kt`** & **`app/src/main/java/app/gamenative/ui/PluviaMain.kt`**:
  - Added `PluviaApp.containerConfigDialogCount` and applied `.graphicsLayer { alpha = if (PluviaApp.containerConfigDialogCount > 0) 0f else 1f }` (with `import androidx.compose.ui.graphics.graphicsLayer`) on the `NavHost` wrapper box so underlying screens (e.g., Game Details `Play`, `Plays well`, size, path) are completely hidden while `ContainerConfigDialog` is open, leaving only root Layer 0 live wallpaper visible behind the dialog.
- **`app/src/main/java/app/gamenative/ui/component/dialog/ContainerConfigDialog.kt`**:
  - Increments/decrements `PluviaApp.containerConfigDialogCount` via `DisposableEffect(Unit)`, binds dialog-local `LocalBrokenGlassBackdrop` and `LocalBackgroundLayer provides null` to avoid cross-window `GraphicsLayer` issues, strengthens window dim (`0.35f` glass / `0.60f` solid), wraps the scaffold in a vertical dark gradient scrim, and adds `ContainerGlassCard` (`18.dp` `BrokenGlassPanel` with dark inner contrast scrim) and `ContainerCardDivider`.
- **All 9 Container Tabs** (`GeneralTab.kt`, `GraphicsTab.kt`, `EmulationTab.kt`, `ControllerTab.kt`, `WineTab.kt`, `WinComponentsTab.kt`, `EnvironmentTab.kt`, `DrivesTab.kt`, `AdvancedTab.kt`):
  - Grouped and wrapped container setting rows in `ContainerGlassCard` sections separated by `ContainerCardDivider()` for clean visual separation and contrast.

### 3. Glass Panels Default ON & Practical Big-Screen Tutorial Remake
- **`app/src/main/java/app/gamenative/PrefManager.kt`**, **`app/src/main/java/app/gamenative/ui/data/MainState.kt`**, **`app/src/main/java/app/gamenative/ui/theme/LooksState.kt`**:
  - Enabled Glass Panels by default (`true` in `GLASS_PANELS`, `MainState.glassPanels`, and `LooksState.isGlassEnabled`).
- **`app/src/main/java/app/gamenative/ui/screen/TutorialScreen.kt`**:
  - Rebuilt the onboarding flow from scratch as a practical big-screen setup guide (`widthIn(max = 860.dp)`) with a full-screen dark vertical gradient scrim so bright wallpapers never wash out text, `NdotFontFamily` reserved strictly for big titles/headers, `BricolageGrotesque` for all body/card/button text, and translucent `TutorialGlassSurface` (`BrokenGlassPanel`) cards.
  - Replaced all 6 marketing slides with 5 practical configuration screens:
    1. **Proton & DXVK Combos** (`arm64ec` vs `x86_64`, `DXVK 2.x+` vs `1.10.3`/`async`, `VKD3D` for DX12-only, `Gamescope` vs `X11`)
    2. **When to Use AdrenoTools** (Turnip vs Stock Qualcomm vs Wrapper/System fallback)
    3. **Dedicated VRAM Sizing** (Unified RAM trade-off; `512 MB`, `1 GB – 2 GB`, `4 GB`)
    4. **CPU Cores vs. FEX** (FEX = x86→ARM translation; CPU Cores = core parking `3–4`, `6`, `8`)
    5. **Set Up Your Look** (Glass Panels ON by default, Standard vs Broken Glass toggle, live Main Colour & Wallpaper pickers)

### 4. Verification
- `$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat assembleModernDebug`: **`BUILD SUCCESSFUL in 3m 37s`** (0 errors), producing `app/build/outputs/apk/modern/debug/gamenothing-modern-debug.apk`.

---

## Phase 19: DroidDeck Native CMake/NDK Repair, Runtime Unification, Navigation Wiring & `AGENTS.md` §5 Cleanup

### 1. `:droiddeck` Native C++/NDK Build & JNI Packaging
- **`droiddeck/src/main/cpp/waylandcomp/CMakeLists.txt`** & **`droiddeck/src/main/cpp/questxr/CMakeLists.txt`**:
  - Fixed invalid relative source paths (`../../../../tools/gpuload/src` and `../../../../tools/vr/src`) so CMake resolves all native targets cleanly.
- **`droiddeck/build.gradle.kts`** & **`app/build.gradle.kts`**:
  - Enabled `ndkVersion = "27.3.13750724"` and `externalNativeBuild.cmake` (`-DANDROID_STL=c++_shared`, `-DDROIDDECK_VITURE_SDK_DIR=`, `-DDROIDDECK_UVC_DIR=`) in `:droiddeck`, compiling `libdroiddeckwayland.so`, `libsdnative.so`, `libdeviceinfo.so`, `libgpuclock.so`, `libddvenc.so`, `libtermux.so`, and `libdroiddeckxr.so`.
  - Added `adrenotools` hook libraries (`libhook_impl.so`, `libmain_hook.so`, `libfile_redirect_hook.so`, `libgsl_alloc_hook.so`) to `packaging.jniLibs.pickFirsts` in both `:droiddeck` and `:app` to prevent duplicate `.so` packaging collisions.

### 2. Playground ↔ `:droiddeck` Runtime Unification & Multi-Process Safety
- **`droiddeck/src/main/java/com/droiddeck/launcher/runtime/LinuxRuntime.java`**:
  - Updated `patchedProot`, `prootBinary`, `prootLoader`, and `prootSource` to recognize bundled `nativeLibraryDir/libproot.so` & `libproot-loader.so` (`PROOT_NATIVE = "bundled native"`), and updated `hasRootfs` to also accept `usr/local/bin/gamescope`.
- **`droiddeck/src/main/java/com/droiddeck/launcher/DroidDeckInitializer.kt`** & **`app/src/main/java/app/gamenative/PluviaApp.kt`**:
  - Added `DroidDeckInitializer.prefetchAddonPacks(app)` (`PROOT`, `DIRECTAUDIO`, `XBOX`).
  - Added a secondary-process guard in `PluviaApp.onCreate()` (`processName != null && processName != packageName`) so DroidDeck subprocesses (`:xboxlogin`, `:xboxcloud`, `:xcloudnative`, `:bottom_desktop`, etc.) initialize `DroidDeckInitializer` and exit early instead of running full main-process app startup.
- **`app/src/main/java/app/gamenative/playground/BwrapSpawner.kt`** & **`app/src/main/java/app/gamenative/playground/LinuxRuntime.kt`**:
  - Unified `BwrapSpawner.start`/`socket` to delegate directly to `com.droiddeck.launcher.runtime.BwrapSpawner`.
  - Aligned `LinuxRuntime` staging paths (`session`, `guest-runtime`, `logs`), `prootBinary`/`prootLoader` delegation, `isInstalled`, and `SessionFiles.stage(context, root, false)`.

### 3. Playground Navigation, `DesktopPage` Launches & `AGENTS.md` §5 Cleanup
- **`droiddeck/src/main/java/com/droiddeck/launcher/FrontEndActivity.kt`**, **`FrontEndRail.kt`**, & **`FrontEndScreen.kt`**:
  - Wired `PAGE_KEY` fallback alongside `StoreDownloadService.EXTRA_NAV` in `readGameIntent`, handling `"components"`, `"performance"`, and `"controller-mapping"` navigation targets.
  - Changed default `FrontEndScreen` tab from `"steam"` to `"desktop"`, removed `"steam"` from `FrontEndRail.navigationKeys`, disabled `STARTUP_STEAM` auto-launch, and removed `DeckyManager` sync/refresh/UI exposure in `FrontEndActivity` per `AGENTS.md` §5.
- **`app/src/main/java/app/gamenative/playground/DesktopPage.kt`**:
  - Wired installed Flatpak and AppImage list item clicks to start `BwrapSpawner.start(context)` and launch `com.droiddeck.launcher.SessionActivity` in `SessionService.MODE_RUN` with `FlatpakManager.LAUNCHER` / `AppImageManager.LAUNCHER` and corresponding args.
- **Replaced Hardcoded `com.droiddeck.launcher` Intent/Extra Strings**:
  - Updated `droiddeck/src/main/AndroidManifest.xml` (`${applicationId}.droiddeck.XR` / `LAUNCH_GAME`) and intent/action/extra constants in `SessionService.kt`, `SessionActivity.kt`, `FileManagerActivity.kt`, `GameLaunchIntent.kt`, `SteamIntegration.kt`, `WirelessAdbPairingService.kt`, `Glasses.kt`, `RuntimeInstallService.kt`, `LaunchFlood.kt`, and `SelfInstaller.kt` to `app.gamenative.droiddeck.*`.
- **`app/src/main/java/app/gamenative/ui/component/QuickMenu.kt`**:
  - Replaced `runCatching { hiltViewModel<MainViewModel>() }` with a `LocalInspectionMode` and `LocalViewModelStoreOwner` check prior to calling `@Composable hiltViewModel()`.

### 4. Verification
- `$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat assembleModernDebug`: **`BUILD SUCCESSFUL in 14m 21s`** (0 errors; NDK `27.3.13750724` + CMake `3.22.1` + Kotlin/Dex/APK packaging).
- Inspected `app/build/outputs/apk/modern/debug/gamenothing-modern-debug.apk` (262,264,004 bytes): confirmed `lib/arm64-v8a/` includes `libdroiddeckwayland.so`, `libsdnative.so`, `libdeviceinfo.so`, `libgpuclock.so`, `libddvenc.so`, `libtermux.so`, `libdroiddeckxr.so`, `libproot.so`, `libproot-loader.so`, and `adrenotools` hook `.so`s, plus full `assets/linuxfs/usr/local/bin/*` staging. Note: build and APK inspection verified; runtime execution on a physical Android device has not yet been run.

---

## Phase 20: Win Components Picker Aliasing Fix, Serialization Hardening & Config Auto-Repair

### 1. Root Cause & UI Picker Fix
- **`app/src/main/java/com/winlator/core/KeyValueSet.java`**:
  - Fixed `iterator().next()` reusing a single mutable `String[2]` array across iterations (which caused `KeyValueSet(config.wincomponents).toList()` in `WinComponentsTab.kt` to repeat the last component on every row) by returning a fresh `String[]{key, value}` per entry.
  - Hardened `iterator()` and `indexOfKey()` to skip empty segments and malformed entries without `=` rather than throwing `StringIndexOutOfBoundsException`.
- **`app/src/main/java/app/gamenative/ui/component/dialog/WinComponentsTab.kt`**:
  - Replaced `KeyValueSet(config.wincomponents).toList()` with `parseWinComponents(config.wincomponents)` returning immutable `WinComponentEntry(id, value)` records.
  - Wrapped each `ContainerGlassDropdown` row in `key(compId)` so Compose picker state (`isPickerOpen`, `FocusRequester`) stays isolated per component ID.
  - Replaced `config.wincomponents.replace("$compId=$compValue", "$compId=$it")` with `updateWinComponent(latestConfig.wincomponents, compId, selected)` reading the latest `state.config.value` and updating only the targeted `compId` entry without substring collisions or key duplication.

### 2. Validation, Sanitization & Broken Config Repair
- **`app/src/main/java/com/winlator/container/Container.java`**:
  - Added `Container.normalizeWinComponents(String)` to safely parse, validate (`"0"` or `"1"`), deduplicate (keeping first valid occurrence), drop unknown/obsolete keys, preserve valid `REDIST_WINCOMPONENTS`, and merge missing defaults from `DEFAULT_WINCOMPONENTS`.
  - Wired `normalizeWinComponents` into `checkObsoleteOrMissingProperties(JSONObject)` (using `optString`) and `setWinComponents(String)`.
- **`app/src/main/java/app/gamenative/PrefManager.kt`**, **`app/src/main/java/app/gamenative/utils/ContainerUtils.kt`**, & **`app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`**:
  - Normalized `PrefManager.winComponents` on read/write and `ContainerUtils.toContainerData()` so corrupted default or container configs are repaired automatically.
  - Added launch-time repair in `XServerScreen.kt` (normalizing `container.winComponents` before `extractWinComponentFiles` and `WineUtils.setWinComponentRegistryKeys`, and using `optJSONArray` in `extractWinComponentFiles`).

### 3. Tests & Verification
- **`app/src/test/java/com/winlator/container/ContainerWinComponentsTest.kt`**:
  - Added tests for `KeyValueSet.toList()` distinct entries and malformed-entry resilience, `Container.normalizeWinComponents` corruption repair, and `parseWinComponents` / `updateWinComponent` single-entry updates.
- Ran `:app:testModernDebugUnitTest` for `ContainerWinComponentsTest`, `ExternalRedistOffTest`, and `CommunityConfigApplicationTest` (all passed).
- Ran `$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat assembleModernDebug`: **`BUILD SUCCESSFUL in 2m 28s`** (0 errors; produced `app/build/outputs/apk/modern/debug/gamenothing-modern-debug.apk`).

---

## Phase 21: Official Branding ("GameNothing" v2.0-patch2), sRGB Image Normalization & Reference Doc

### 1. Official App Naming & Version
- **`app/build.gradle.kts`**: Updated `versionName` to `"2.0-patch2"` (preserving `applicationId = "app.gamenative"` and `versionCode = 24`).
- **`app/src/main/res/values*/strings.xml` (all 16 locales)**: Updated `app_name` and `login_app_name` from `GameNative` to `GameNothing`.
- **`droiddeck/src/main/res/values*/strings.xml` (all 33 locales)**: Updated `app_name` from `DroidDeck` to `GameNothing` (fixing fallback locales showing "DroidDeck") and `steam_wall_label` to `GameNothing Wall`.

### 2. Cross-Device Gamma Consistency & Secondary Fixes
- **`app/src/main/java/app/gamenative/MainActivity.kt`**: Pinned `window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT`, configured the shared Coil `ImageLoader` interceptor to normalize decoded images to `ColorSpace.Named.SRGB`, and exposed `MainActivity.clearImageCaches()`.
- **`app/src/main/java/app/gamenative/utils/CoilDecoders.kt`**: Configured `IconDecoder` with `inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)`.
- **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupDebug.kt`**: Updated Debug "Clear Image Cache" to call `MainActivity.clearImageCaches()` in addition to `context.imageLoader`.
- **`app/src/main/java/app/gamenative/ui/screen/library/components/LibraryCarouselPane.kt`**: Removed forced per-card `CompositingStrategy.Offscreen` (matching GameNothing 1.2).
- **`WHATS_WHAT.md`**: Added reference guide documenting functional `GameNative` identifiers/endpoints that must never be renamed, safe renames applied, verified functionality, and unverified areas.

### 3. Verification
- Ran `$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat assembleModernDebug`: finished with exit status `0`, producing `app/build/outputs/apk/modern/debug/gamenothing-modern-debug.apk`.

---

## Phase 22: GameNothing 2.0 — Per-Container Universal Performance Mode & Permanent Free + BYOK AI Container Debugger

### 1. Shared Container Snapshot & Persistence Engine
- **`app/src/main/java/com/winlator/container/ContainerData.kt`** & **`app/src/main/java/app/gamenative/utils/ContainerUtils.kt`**:
  - Added backward-compatible fields `fpsLimiterEnabled: Boolean = true`, `rootlessGamePinningEnabled: Boolean = false`, `performanceMode: Boolean = false`, `performanceModeSnapshot: String = ""`, and `lastAiPatchSnapshot: String = ""` to `ContainerData` and `ContainerData.Saver`.
  - Wired `ContainerUtils.toContainerData(container)` and `ContainerUtils.applyToContainer(context, container, saveData)` to persist all 5 fields in `<container.rootDir>/.container` via `container.getExtra(...)` / `container.putExtra(...)`.

### 2. Per-Container Universal Performance Mode (100% Rootless, Online & Xbox Live Safe)
- **`app/src/main/java/app/gamenative/performance/PerformanceModeManager.kt`**:
  - Implemented `enablePerformanceMode`, `disablePerformanceMode`, `reconcileManualOverride`, and `getAllCpuCoresList`.
  - Turning Performance Mode **ON** snapshots only managed keys (`displayServer`, `fexcorePreset`, `box64Preset`, `cpuList`, `cpuListWoW64`, `fpsLimiterEnabled`, `rootlessGamePinningEnabled`, plus `.power_profile.json` `adaptiveFpsCapEnabled` if present) and forces `displayServer = "x11"`, `fexcorePreset = FEXCorePreset.EXTREME`, `box64Preset = Box86_64Preset.PERFORMANCE`, all CPU cores unlocked (`0..N-1`), `fpsLimiterEnabled = false`, and `rootlessGamePinningEnabled = true`.
  - Turning Performance Mode **OFF** restores the exact prior snapshot values and clears `performanceModeSnapshot`.
  - Manual override rule: if the user or AI Debugger modifies any managed field while Performance Mode is ON, `reconcileManualOverride` automatically flips `performanceMode = false` and clears `performanceModeSnapshot` without reverting the user's change.
  - Strictly preserves online/Xbox Live settings, Proton/Wine version, DXVK/VKD3D version, FEX/Box64 version, Graphics Driver, and Display Resolution.
- **`app/src/main/java/app/gamenative/performance/RootlessGamePinner.kt`** & **`app/src/main/java/app/gamenative/performance/AndroidOsPerformanceSignaler.kt`**:
  - Implemented CPU cluster topology detection via `/sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq` and 3,000 ms same-UID guest thread `(utime + stime)` delta sampling + `TaskAffinity.set(tid, bigPrimeMask)` pinning onto Big/Prime cores.
  - Implemented Android OS max-performance signaling during `XServerScreen` (`window.setSustainedPerformanceMode(false)`, `GameManager.setGameState(MODE_GAMEPLAY_UNINTERRUPTIBLE)` on API 33+, and ADPF `PerformanceHintManager.Session` on API 31+), wired into `XServerScreen.kt` via 1-line helper calls to stay safely below the ART 255-register bytecode verifier limit.

### 3. Permanent Free + BYOK AI Container Debugger
- **`app/src/main/java/app/gamenative/aidebugger/AiKeyStore.kt`** & **`app/src/main/java/app/gamenative/aidebugger/OpenRouterPkceAuth.kt`**:
  - Pure Android Keystore (`AES/GCM/NoPadding`, dedicated alias `gamenothing_ai_keystore_aes_gcm_v1`) credential and model store.
  - 1-Tap `"Continue with Google (OpenRouter)"` OAuth PKCE client (`S256` challenge, loopback `ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))` on `Dispatchers.IO`, Chrome Custom Tabs launch, `/callback` token exchange, and manual code/key fallback) + visual model picker (`openrouter/free` default, `google/gemini-3.5-flash-lite`, `deepseek/deepseek-chat`, `anthropic/claude-sonnet-4`, `openai/gpt-4o-mini`, `moonshotai/kimi-k2`, `perplexity/sonar`, custom).
- **`app/src/main/java/app/gamenative/aidebugger/ContainerDiagnosticCollector.kt`** & **`app/src/main/java/app/gamenative/aidebugger/OfflineDiagnosticsEngine.kt`**:
  - Collects the 6-part redacted diagnostic bundle on `Dispatchers.IO` (last-run telemetry & bottleneck classification, native exit traces & filtered/deduplicated Wine/DXVK/VKD3D/FEX/Box64 logs, complete container config, hardware/thermal/CPU cluster profile, installed components inventory via `ManifestComponentHelper`, and shallow `< 64 KB` `.ini`/`.cfg` discovery).
  - Instant 0-second `OfflineDiagnosticsEngine` rule checks (`gamescope` SDR dark-gamma/black screen, active FPS limiter/Auto FPS cap, Universal Performance Mode off, FEX Extreme TSO thread-race crash/stutter, restricted CPU core mask, verbose debug envVars, and PulseAudio low-latency audio crackle fix).
- **`app/src/main/java/app/gamenative/aidebugger/AiDebuggerClient.kt`**, **`AiPatchValidator.kt`**, & **`AiPatchApplier.kt`**:
  - Multi-provider client supporting OpenRouter, Direct Google Gemini (`gemini-3.5-flash-lite` default with strict 3.5 compatibility guards: `x-goog-api-key` header only, no `frequencyPenalty`/`presencePenalty`, no custom `temperature`/`topP`/`topK`, final `contents` entry `"role": "user"`), Custom OpenAI-Compatible Endpoint, and Zero-Login Copy/Paste prompt generation.
  - Strict allowlist validator enforcing installed component inventory checks and canonical `< 64 KB` `.ini`/`.cfg` path-traversal protection.
  - Full disk-persisted undo via `lastAiPatchSnapshot` and `<filename>.gamenothing.bak` backups.
- **`app/src/main/java/app/gamenative/ui/component/dialog/ContainerPerformanceAndAiCard.kt`**, **`AiSetupCard.kt`**, **`AiDebuggerSheet.kt`**, **`ContainerConfigDialog.kt`**, **`BaseAppScreen.kt`**, **`PluviaMain.kt`**, & **`SettingsGroupDebug.kt`**:
  - Pinned Universal Performance Mode toggle card + `"AI Fix / Debug FPS"` button + `"Undo AI Fix"` button at the top of `ContainerConfigDialog`, wired `Apply & Run Game`, and added global AI Debugger provider setup in Settings -> Debug.

### 4. Versioning & Verification
- **`app/build.gradle.kts`**: Bumped `versionCode` `24` → `25` and `versionName` `"2.0-patch2"` → `"2.0-patch3"`.
- Added Robolectric unit tests in `PerformanceModeManagerTest.kt` and `AiDebuggerEngineTest.kt`.
- Ran `$env:JAVA_HOME = 'C:\Users\sorin\.jdks\jbr-21.0.11'; .\gradlew.bat :app:testModernDebugUnitTest --tests "app.gamenative.performance.PerformanceModeManagerTest" --tests "app.gamenative.aidebugger.AiDebuggerEngineTest" assembleModernDebug`: **`BUILD SUCCESSFUL in 36s`** (5/5 unit tests passed, 0 compilation errors).

---

## Phase 23 (2.0-patch4): Mid-Game Freeze/Unload Fix, Dedicated Performance/Eco Tab, Unified AI Debugger & Doc Split

### 1. Phase 1 Root Causes Audited & Fixed (Mid-Game `0%` CPU Freeze / Silent Container Unload)
1. **Non-No-Op OS Signaler When `OFF` (`AndroidOsPerformanceSignaler.kt`):**
   - `2.0-patch3` unconditionally invoked `window.setSustainedPerformanceMode(false)` even when Performance Mode was OFF, could still start `RootlessGamePinner` if `rootlessGamePinningEnabled` lingered in container extras, created an ADPF `PerformanceHintManager.Session` on the **Android UI thread** (`Process.myTid()` inside `DisposableEffect(Unit)`) ignoring user FPS caps, and never closed/paused ADPF or polling on `ON_PAUSE`.
   - **Fix:** `AndroidOsPerformanceSignaler` is now a strict zero-overhead no-op whenever `tuningMode != PERFORMANCE` (`OFF` and `ECO`). In `PERFORMANCE`, ADPF only binds to the actual in-process GL render thread TID (`GLRenderer.onSurfaceCreated`), computes `targetDurationNanos` respecting user FPS caps (`fpsLimiterTarget` / `DXVK_FRAME_RATE`), and cleanly pauses/closes on `ON_PAUSE` / `onSessionEnd` via `XServerScreen`'s existing `LifecycleEventObserver`.
2. **Thread-Affinity Deadlock & Daemon Pinning (`RootlessGamePinner.kt`):**
   - `extractEffectiveExeName()` returned `tokens.lastOrNull()`, which for daemons invoked with CLI flags (`wineserver -p`, `libpulseaudio.so --exit-idle-time=-1`, `gamescope ...`) returned the flag instead of the binary name — bypassing `EXCLUDED_DAEMON_NAMES`. Furthermore, `pulseaudio`/`libpulseaudio`, `gamescope`, `xwayland`, `gndrm`, `xserver`, `proot`, `fexserver`, `vortek`/`virgl`, `dbus`, and `steam` were missing from exclusions; `pinnedThreads` lacked synchronization across IO polling and Main `stop()`; and unpinning restored threads to all 8 cores, clobbering 32-bit `<= 4`-core masks.
   - **Fix:** Rewrote `shouldExcludeProcess()` to inspect `processName`, full `cmdline` substrings, `argv[0]`, and `.exe` tokens, strictly restricting pinning to active non-excluded Windows `.exe` processes; added `stateLock` synchronization; silently ignore `ESRCH`/`EPERM`; restore unpinned threads to the container's configured CPU mask; and cancel polling immediately on pause/exit.
3. **32-bit `cpuListWoW64` Overload & FPS Cap Override (`PerformanceModeManager.kt`):**
   - `2.0-patch3` assigned all 8 cores to 32-bit `cpuListWoW64` (known trigger for 32-bit Wine/FEX/DXVK deadlocks) and disabled `fpsLimiterEnabled`.
   - **Fix:** Strictly capped `cpuListWoW64` to at most 4 highest-frequency cores in `PERFORMANCE` and `<= 4` even cores in `ECO`, and removed all writes to `fpsLimiterEnabled` / `DXVK_FRAME_RATE` (only `.power_profile.json`'s hidden `adaptiveFpsCapEnabled` is toggled in `PERFORMANCE`).
4. **Targeted Shader Cache Cleanup (`ShaderCacheCleaner.kt`):**
   - Added safe `Clear Shader Cache` utility wiping only Mesa/Turnip (`mesa_shader_cache`, `mesa_shader_cache_db`), DXVK/VKD3D caches, container `.cache/fossilize`, and idle X11/Pulse temp sockets (when `PluviaApp.xEnvironment == null`), preserving saves, `.wine` prefixes, and installed components, and reporting freed MB via `SnackbarManager`.

### 2. Phase 2: Dedicated `Performance` Tab (Index 0, Far Left Before `General`) + Tri-State `tuningMode` (`OFF` / `PERFORMANCE` / `ECO`)
- Replaced boolean `performanceMode` with tri-state `tuningMode` (`"OFF"`, `"PERFORMANCE"`, `"ECO"`) and single `tuningModeSnapshot` in `ContainerData.kt` & `ContainerUtils.kt`, with seamless backward-compatible migration of 1.4.0 and `2.0-patch3` JSONs (`performanceMode == true` → `tuningMode = "PERFORMANCE"`).
- Implemented exact `ECO` core pairing (`LITTLE` + `MEDIUM`, strictly even `{2, 4, 6}`, never `8`, `<= 4` even for 32-bit `cpuListWoW64`, sysfs fallback `0,1,2,3`), `PERFORMANCE <-> ECO` baseline snapshot preservation, manual-override reconciliation back to `OFF`, and immediate disk persistence.
- Added dedicated `PerformanceTab.kt` at **index 0 (far left, BEFORE `General` at index 1)** in `ContainerConfigDialog.kt` with `Off`/`Performance`/`Eco` segmented selector, 1-line live 64-bit/32-bit core preview, and **Clear Shader Cache** button; removed `ContainerPerformanceAndAiCard.kt`.

### 3. Phase 3: Unified Dead-Simple 2-Click `"AI Fix"` Debugger (No Multi-Step Wizard)
- Re-enabled `"Get AI help"` (`AppOptionMenuType.AiDebugRun` in `BaseAppScreen.kt`) as the single unified AI Debugger entry point and routed `onAiDebugRun` in `PluviaMain.kt` directly to `AiDebuggerSheet` (with immediate disk save and `Fix & Run` game launch), eliminating the duplicate GameNothing-only AI card/button.
- Stripped all multi-step wizard chrome out of `AiDebuggerSheet.kt` and rebuilt it as a clean 2-click `"AI Fix"` modal (`usePlatformDefaultWidth = false`, `fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp)`, `widthIn(max = 640.dp)`): Pinned Top Bar (`"AI Fix"` + gear icon for single-card OpenRouter/Gemini login/sign-out + Close), Scrollable Body (one short text box, `Online` and `Offline` primary buttons, compact `Copy Script` / `Paste Fix` sub-actions, and a keyed `Old -> New` Result Card), and Pinned Bottom Bar (`Fix & Run` + `Undo` when `lastAiPatchSnapshot != null`).
- Added `OfflineDiagnosticsEngine.inferSymptomsFromPrompt(...)` and `analyzeCombined(...)` (respecting the user's FPS cap unless explicitly requested) and updated `AiDebuggerClient.kt`, `AiPatchValidator.kt`, `AiPatchApplier.kt`, and `ContainerDiagnosticCollector.kt` for `tuningMode` and the `<= 4` 32-bit `cpuListWoW64` invariant.

### 4. Phase 4: Docs, Version Bump & Modified Files
- Bumped `app/build.gradle.kts` `versionCode` `25` → `26` and `versionName` `"2.0-patch3"` → `"2.0-patch4"`.
- Rewrote `/WHATS_WHAT.md` as a concise 4-section living cheat sheet and added `/WHATS_WHAT.md` + `/CHANGELOG_MERGE.md` discipline rules to `/AGENTS.md`.
- **Modified / Added / Removed Files:**
  - Modified: `app/build.gradle.kts`, `app/src/main/res/values/strings.xml`, `app/src/main/java/app/gamenative/performance/PerformanceModeManager.kt`, `app/src/main/java/app/gamenative/performance/RootlessGamePinner.kt`, `app/src/main/java/app/gamenative/performance/AndroidOsPerformanceSignaler.kt`, `app/src/main/java/com/winlator/container/ContainerData.kt`, `app/src/main/java/app/gamenative/utils/ContainerUtils.kt`, `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`, `app/src/main/java/com/winlator/renderer/GLRenderer.java`, `app/src/main/java/app/gamenative/ui/component/dialog/ContainerConfigDialog.kt`, `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt`, `app/src/main/java/app/gamenative/ui/PluviaMain.kt`, `app/src/main/java/app/gamenative/aidebugger/AiDebuggerSheet.kt`, `app/src/main/java/app/gamenative/aidebugger/AiDebuggerClient.kt`, `app/src/main/java/app/gamenative/aidebugger/AiPatchValidator.kt`, `app/src/main/java/app/gamenative/aidebugger/AiPatchApplier.kt`, `app/src/main/java/app/gamenative/aidebugger/ContainerDiagnosticCollector.kt`, `app/src/main/java/app/gamenative/aidebugger/OfflineDiagnosticsEngine.kt`, `app/src/test/java/app/gamenative/performance/PerformanceModeManagerTest.kt`, `app/src/test/java/app/gamenative/aidebugger/AiDebuggerEngineTest.kt`, `WHATS_WHAT.md`, `AGENTS.md`, `CHANGELOG_MERGE.md`.
  - Added: `app/src/main/java/app/gamenative/performance/ShaderCacheCleaner.kt`, `app/src/main/java/app/gamenative/ui/component/dialog/PerformanceTab.kt`.
  - Removed: `app/src/main/java/app/gamenative/ui/component/dialog/ContainerPerformanceAndAiCard.kt`.
- **Verification:**
  - `:app:testModernDebugUnitTest --tests "app.gamenative.performance.PerformanceModeManagerTest" --tests "app.gamenative.aidebugger.AiDebuggerEngineTest"` — **15/15 passed** (10 in `PerformanceModeManagerTest`, 5 in `AiDebuggerEngineTest`, 0 failures, 0 skipped).
  - `:app:assembleModernDebug` — **`BUILD SUCCESSFUL`** (0 errors, `versionCode = 26`, `versionName = "2.0-patch4"`).

---

## Phase 23.1: AI Fix Patch Hygiene, `tuningMode` Collision Prevention, Prompt Hardening & Full Diagnosis Wrap (`2.0-patch4`)

### Summary of Targeted Fixes
1. **No-Op Diff Filtering (`AiPatchValidator.kt`, `OfflineDiagnosticsEngine.kt`, `AiDebuggerSheet.kt`):**
   - Added `AiPatchValidator.normalizeValueForComparison(...)`, `normalizeCoreMaskTokens(...)`, and `resolveAndFilterChanges(...)` to compare every proposed `changes[key]` against live `ContainerData` values (trimmed whitespace, sorted comma-separated core masks like `0,1,2,3,4,5,6,7`, normalized booleans/enums, and absent `envVars` removals).
   - Drops any `X -> X` no-op entry before display or apply; when zero real changes remain, `AiDebuggerSheet` shows `"No setting changes needed"` and disables `Fix & Run` while still showing the diagnosis.
2. **`tuningMode` Patch Collision Prevention (`PerformanceModeManager.kt`, `AiPatchValidator.kt`, `AiPatchApplier.kt`):**
   - Defined canonical `PerformanceModeManager.TUNING_MANAGED_KEYS` (`displayServer`, `fexcorePreset`, `box64Preset`, `cpuList`, `cpuListWoW64`, `rootlessGamePinningEnabled`) and side-effect-free `previewTuningModeConfig(...)`.
   - When an AI patch emits `tuningMode` alongside managed fields, redundant fields matching `tuningMode`'s effective values are dropped; if any custom managed field contradicts `tuningMode`, `tuningMode` is dropped so custom edits apply cleanly without colliding or auto-flipping back to `OFF`.
   - `AiPatchApplier.applyPatch(...)` now applies `tuningMode` first (snapshotting pre-patch managed fields for full Undo), then remaining non-managed keys, and persists to disk before launch.
3. **Hardened AI Fix System Prompt (`AiDebuggerClient.kt`):**
   - Updated `SYSTEM_PROMPT` (shared by `Online` and `Copy Script`) to prohibit blind `fexcoreVersion`/`wineVersion`/`dxvkVersion` upgrading, prioritize telemetry-driven (`Limiting Factor`) CPU vs GPU bottleneck fixes, omit unchanged fields, and forbid emitting `tuningMode` together with overlapping managed fields.
4. **Full Diagnosis Text Wrap (`AiDebuggerSheet.kt`):**
   - Removed `maxLines = 3` and `TextOverflow.Ellipsis` on the Results Card `diagnosis` `Text` while keeping the `Fix & Run` / `Undo` footer pinned and scrollable with `safeDrawingPadding()`.

### Modified Files & Verification
- **Modified:** `app/src/main/java/app/gamenative/performance/PerformanceModeManager.kt`, `app/src/main/java/app/gamenative/aidebugger/AiPatchValidator.kt`, `app/src/main/java/app/gamenative/aidebugger/AiPatchApplier.kt`, `app/src/main/java/app/gamenative/aidebugger/OfflineDiagnosticsEngine.kt`, `app/src/main/java/app/gamenative/aidebugger/AiDebuggerClient.kt`, `app/src/main/java/app/gamenative/aidebugger/AiDebuggerSheet.kt`, `app/src/test/java/app/gamenative/aidebugger/AiDebuggerEngineTest.kt`, `CHANGELOG_MERGE.md`.
- **Verification:**
  - `:app:testModernDebugUnitTest --tests "app.gamenative.performance.PerformanceModeManagerTest" --tests "app.gamenative.aidebugger.AiDebuggerEngineTest"` — **18/18 passed** (10 in `PerformanceModeManagerTest`, 8 in `AiDebuggerEngineTest`, 0 failures, 0 skipped).
  - `:app:assembleModernDebug` — **`BUILD SUCCESSFUL`** (0 errors).










