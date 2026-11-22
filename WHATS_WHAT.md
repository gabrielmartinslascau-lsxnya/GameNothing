# GameNothing — Living Engineering Cheat Sheet

**App:** GameNothing · **Version:** `2.0-patch4` (`versionCode = 26`) · **Base:** GameNative 1.4.0 fork.
*Keep update history strictly in [`CHANGELOG_MERGE.md`](CHANGELOG_MERGE.md). This file is a short invariant reference.*

---

## 1. NEVER EVER CHANGE
- **No Compose Reparenting:** Never reparent `NavHost` or `XServerScreen` (`PluviaMain.kt` / `XServerScreen.kt`).
- **Protected Container Fields (Performance & Eco Mode):** Never overwrite or modify the user's **FPS Cap** (`fpsLimiterEnabled`, `DXVK_FRAME_RATE`), **Online / Xbox Live / Network** settings, **Proton / Wine version**, **DXVK / VKD3D version**, **FEX / Box64 version**, **Graphics Driver**, or **Display Resolution**.
- **32-bit CPU Core Ceiling:** Never assign more than **4 cores** to 32-bit `cpuListWoW64` (causes 32-bit Wine/FEX/DXVK worker deadlocks).
- **Thread-Affinity Exclusion Safety:** Never call `sched_setaffinity` on `wineserver`, `pulseaudio`/`libpulseaudio`, `gamescope`, `XServer`/`Xwayland`/`gndrm`, `proot`, `fexserver`, `vortek`/`virgl`, `dbus`, `steam`, or the Android UI/host process.
- **Preset Separation:** Never merge GameNothing `Performance`/`Eco` tuning into GameNative's built-in pre-made container presets.
- **Functional `app.gamenative` Identifiers:** Never rename `applicationId = "app.gamenative"`, package `app.gamenative`, `/data/data/app.gamenative/files`, prebuilt JNI `.so` names (`libgamenative_drm.so`, `libgnaffinity.so`, etc.), `gamenative://` intent/OAuth schemes, `.gamenative/` container metadata, `GameNative` storage paths, Keystore aliases, or `downloads.gamenative.app` / `api.gamenative.app` endpoints.
- **Read-Only Reference Trees:** Never modify any file under `/_ref_*/`.

---

## 2. CHANGE WITH CARE
- **Container JSON Backward Compatibility:** Maintain clean loading of 1.4.0 and `2.0-patch3` containers (`ContainerData` + `ContainerUtils`: migrate legacy `performanceMode == true` → `tuningMode = "PERFORMANCE"` and `performanceModeSnapshot` → `tuningModeSnapshot`).
- **Zero-Overhead No-Op Hooks When `OFF` / `ECO`:** `AndroidOsPerformanceSignaler` and `RootlessGamePinner` must return immediately without touching `setSustainedPerformanceMode`, `GameManager`, ADPF, or `/proc` polling whenever `tuningMode != PERFORMANCE`.
- **Immediate Disk Persistence Before Launch:** `XServerScreen` reloads container configs from disk on launch — persist `tuningMode` changes and AI patches immediately via `ContainerUtils.applyToContainer(...)`.
- **Snapshot & Manual Override Semantics:** `PERFORMANCE <-> ECO` must preserve the original `OFF` baseline `tuningModeSnapshot`; manually editing any managed field while in `PERFORMANCE`/`ECO` must flip `tuningMode` back to `OFF` and clear `tuningModeSnapshot`.
- **Keyed Compose Loops & `XServerScreen` ART Limit:** Always pass explicit `key(...)` in dynamic Compose loops (`LazyColumn` / `forEach`) and keep additions to `XServerScreen.kt` to 1-line delegated calls (near ART 255-register verifier ceiling).
- **Tab Order:** Keep the dedicated `Performance` tab at **index 0 (far left, BEFORE `General`)** in `ContainerConfigDialog.kt`.

---

## 3. SAFE TO CHANGE
- UI layout, glass styling, spacing, and copy in `PerformanceTab.kt`, `AiDebuggerSheet.kt`, and Settings screens.
- AI Debugger system prompt wording, offline quick-fix heuristics, and default model IDs (`AiDebuggerClient.kt`, `OfflineDiagnosticsEngine.kt`, `AiKeyStore.kt`).
- Targeted shader/temp-socket cleanup targets in `ShaderCacheCleaner.kt` (provided saves, `.wine`/`drive_c`, installed components, and container JSONs remain untouched).
- Display-only branding (`app_name`, `login_app_name`) and localization strings.

---

## 4. KEY FILES & WHAT THEY DO
| File | Role |
|---|---|
| `app/src/main/java/app/gamenative/performance/PerformanceModeManager.kt` | Tri-state `TuningMode` (`OFF`/`PERFORMANCE`/`ECO`), sysfs cluster/core pairing (`<= 4` 32-bit cap, even Eco pairs `{2,4,6}`), snapshot/restore, manual-override reconciliation. |
| `app/src/main/java/app/gamenative/performance/AndroidOsPerformanceSignaler.kt` | Zero-overhead no-op in `OFF`/`ECO`; active-gameplay `PERFORMANCE` OS boosts (`setSustainedPerformanceMode(false)`, `GameManager`, guarded render-thread ADPF respecting user FPS cap, pause/resume/end hooks). |
| `app/src/main/java/app/gamenative/performance/RootlessGamePinner.kt` | Rootless same-UID `.exe` worker thread pinner for `PERFORMANCE` mode with strict daemon exclusions, thread-safe pin lock, and container-mask restore. |
| `app/src/main/java/app/gamenative/performance/ShaderCacheCleaner.kt` | Targeted Mesa/Turnip, DXVK/VKD3D, Fossilize shader cache + idle temp X11/Pulse socket cleaner. |
| `app/src/main/java/app/gamenative/ui/component/dialog/PerformanceTab.kt` | Dedicated `Performance` tab (index 0, far left before `General`) with `Off`/`Performance`/`Eco` segmented selector, live 64-bit/32-bit core preview, and Clear Shader Cache button. |
| `app/src/main/java/app/gamenative/ui/component/dialog/ContainerConfigDialog.kt` | Container Settings dialog host with 10 tabs (index 0 = `Performance`, index 1 = `General`) and automatic manual-override reconciliation. |
| `app/src/main/java/app/gamenative/aidebugger/AiDebuggerSheet.kt` | Dead-simple 2-click `"AI Fix"` modal (`safeDrawingPadding`/`imePadding`, `640.dp` width cap) with gear login card, short prompt box, `Online`/`Offline`, `Copy Script`/`Paste Fix`, Result Card, and pinned `Fix & Run` + `Undo`. |
| `app/src/main/java/app/gamenative/aidebugger/ContainerDiagnosticCollector.kt` | Collects upstream 1.4.0 telemetry, logs, hardware/thermal state, installed components, and `<64 KB` game `.ini`/`.cfg` files into a redacted bundle. |
| `app/src/main/java/app/gamenative/aidebugger/OfflineDiagnosticsEngine.kt` | Instant 0-second local quick-fix engine (X11, FEX TSO, CPU affinity with `<= 4` WoW64 cap, debug env cleanup, PulseAudio low-latency). |
| `app/src/main/java/app/gamenative/aidebugger/AiDebuggerClient.kt` | Multi-provider client (OpenRouter PKCE, Direct Gemini `gemini-3.5-flash-lite`, Custom OpenAI, Zero-Login Copy/Paste). |
| `app/src/main/java/app/gamenative/aidebugger/AiPatchValidator.kt` | Strict allowlist + installed-component + `<= 4` WoW64 + canonical `.ini`/`.cfg` path-traversal validator. |
| `app/src/main/java/app/gamenative/aidebugger/AiPatchApplier.kt` | Applies validated container + `.ini`/`.cfg` patches with `.gamenothing.bak` backups and full disk-persisted undo. |
| `app/src/main/java/app/gamenative/ui/PluviaMain.kt` | Routes `"Get AI help"` (`onAiDebugRun`) directly into the unified `AiDebuggerSheet` with `Apply & Run` launch support. |
| `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt` | Game session host; delegates `onSessionStart`/`onPause`/`onResume`/`onSessionEnd` to `AndroidOsPerformanceSignaler`. |
