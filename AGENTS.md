# GameNothing 2.0 Master Agent Contract

## 1. Workspace Topology
- `/` and `/app` (GameNative 1.4.0) are the ONLY read/write build targets.
- `/_ref_gamenothing_1_2/` and `/_ref_droiddeck/` are strictly READ-ONLY reference directories. Never modify any file inside `/_ref_*/`.

## 2. Core 1.4.0 Engine Invariants (DO NOT OVERWRITE)
- Preserve GameNative 1.4.0's core execution architecture intact:
    - Shared Wine prefix logic, per-game `A:` drive mappings, executable overlays, warm-prefix boot bypasses, prefix locks, and dual-slot registry snapshots.
    - `gamescope` + `gamenative-drm` (`gndrm-uapi`, `gndrm-daemon`, `gndrm-shim`, `LINEAR` modifiers, and `libadrenotools_gamescope.so`).
    - Native Rust download engine, Headless Steam, `ntsync-android`, and Room `v17+` database migrations.
- Never replace `1.4.0` `ContainerManager`, `XServerScreen`, or Room migrations with legacy `1.2.0` files. Write Kotlin extension mappers if a `1.2.0` UI screen expects an older model property.

## 3. GameNothing Presentation & Zero-Telemetry Rules
- Stub out all PostHog analytics calls as no-ops and disable sponsor cards and AI support chat popups.
- Port GameNothing's AGSL Glass 2.0 shaders (Mornye & Broken Glass, IOR 1.52, Thickness 58, chromatic aberration), 360° carousel, Nothing OS typography, wallpaper color extraction, 144Hz refresh rate controller, floating RAM bubble, and MP3/FLAC player from `/_ref_gamenothing_1_2/`.

## 4. USB Storage Boundary
- Keep the shared Wine prefix (`home/xuser`) and Linux rootfs strictly on internal app storage (`/data/user/0/<applicationId>/`).
- Bind GameNothing's USB storage mover and system-files bypass strictly to game asset folders mapped to `1.4.0`'s `A:` drive overlay (and external executable paths).

## 5. PC Playground (DroidDeck Linux Without Steam Big Picture)
- Extract DroidDeck's rootless `proot` Linux desktop session, KDE Plasma/KWin launcher, `BwrapSpawner`, `droiddeck-bwrap`, `FlathubApi`, `FlatpakManager`, and `AppImageManager` from `/_ref_droiddeck/` into the Playground tab.
- Exclude Steam Big Picture mode, `fakeinput-rings`, Steam QAM chords, and Decky Loader.
- Replace all hardcoded `com.droiddeck.launcher` strings (including the `com.droiddeck.launcher.bwrap` abstract socket and `/data/user/0/` path resolvers) with our target `applicationId`.
- Preserve the sanitized `/proc/cpuinfo` mount (stripping ARMv9 part numbers to avoid LLVM `llvmpipe` SVE2 `SIGILL` crashes), KGSL `libvulkan_freedreno.so` (`MESA_LOADER_DRIVER_OVERRIDE=zink`), Firefox `MOZ_DISABLE_*_SANDBOX`, and Chromium `ZYPAK_ZYGOTE_STRATEGY_SPAWN=0`.

## 6. Xbox Local & Cloud Integration
- Port DroidDeck's `tools/xbox` (`xodus-nexus`, `wintypes.dll`, `GameInput`), keeping the StrongBox Keystore fallback (`5ca1f61`) and conditional JWE ticket parser (`t=` only for compact JWE tickets, `d=` for legacy tickets).
- Port `tools/xcloud-player` (`Greenlight` + `Better xCloud`) into the Cloud/Remote Play tab.

## 7. Verification Discipline
- Read `/WHATS_WHAT.md` before making any edits so hard invariants and protected identifiers are never violated.
- Keep `/WHATS_WHAT.md` concise as a living cheat sheet only — log all phase/patch update history strictly in `/CHANGELOG_MERGE.md`.
- Output a brief file plan before editing, run unit tests + Gradle build at the end of every phase, and log modified files in `/CHANGELOG_MERGE.md`.
