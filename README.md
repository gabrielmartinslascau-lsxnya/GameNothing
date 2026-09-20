# GameNothing

[image goes here]

Transform your Android device or handheld console into an OEM-grade gaming OS. **GameNothing** is a full-featured Home Launcher replacement and frontend built for high-performance PC game emulation, seamless USB-OTG game storage management, and complete visual customization.

Log in, import your library, and play your games—online or offline—with zero telemetry bloat.

**100% free. Zero data collection, zero limits on what you can add or how much you can store. It's your device — own it.**

---

### 📥 [Click Here to Download Latest GameNothing Release!](https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases)

---

## 🔥 What GameNothing Brings to Your Device

[image goes here]

- **choose your animations**
- **glass panels & AGSL Liquid Glass shader (with realistic blur)**
- **adjustable distortion slider for liquid glass**
- **colour picker (any colour you wish for)**
- **live wallpaper support, no size limit (v1.0 beta or later)**
- **static wallpaper support (v1.0 beta or later)**
- **"Material You" wallpaper color extraction**

[image goes here]

- **haptics (3 modes)**
- **Nothing styled fonts (2 types)**
- **custom download bars (2 types)**

[image goes here]

- **aggressive caching**
- **ram viewer "bubble"**
- **refresh rate control up to 144hz for fluid gaming**
- **dedicated MP3 player with FLAC support**
- **full USB storage pipeline rework (automated high-speed mover + manual system files bypass)**

[image goes here]

- **steam emulation**
- **GOG emulation**
- **Amazon games emulation**
- **Epic games emulation**
- **custom games (external or internal)**
- **cloud saves**
- **nexus mods**
- **local mods**
- **and a whole lot more!**

---

## 🚀 What to Expect in the Future

> **Note:** this started life as v0.6.0 alongside the official GameNative 1.2.1 sync release — turned out to be way too much to still call it a point release. Say hello to **v1.0.0**.

| Version | Key Features | Status |
| :--- | :--- | :--- |
| **BETA v0.1.0** | Very first official launch with Nothing® style font, glass panels, UI animations, 6-color palette picker, screen refresh rate slider, and floating RAM viewer. | `SUPER OUTDATED` |
| **BETA v0.1.1** | Improved colour picker, new custom "Legion" logo, bug fixes, better adjustable sliders on glass panels, refined refresh rate controls, and upgraded floating RAM viewer. | `OUTDATED` |
| **BETA v0.1.2** | **Major Overhaul:** "Legion" logo redesign, haptics integration, controller support, launch animations, 4 new colors, aggressive caching system (cache view), improved tutorial, faster game launch times, glow effects, color saturation tweaks, scrolling optimizations, and initial emulator rebrand to "GameNothing". | `OUTDATED` |
| **v0.2.0** | **Out of Beta:** Full USB drive support, custom game directories (internal/external/SD), base sync with GameNative 1.2.0, interactive beginner setup tutorial, redesigned download progress bar, instant UI response rates, controller loading optimizations, and custom driver support for non-rooted / non-AYN devices. | `OUTDATED` |
| **v0.5.0** | **The Console OS Milestone:** Full app rebrand to **GameNothing**, native Android **Home Launcher** mode (complete OS replacement), initial 360° interactive carousel navigation with floor reflections, **HSV Colour Picker 2.0** popup, initial **AGSL Liquid Glass** shader, persistent **Background Music Bar / MP3 player**, unmuted trailer audio in Hero view, **Material You wallpaper color extraction**, and initial USB loading logic. | `CURRENT RELEASE` |
| **v1.0.0 (Beta)** | **The "This Was Supposed to Be 0.6" Release:** Full **GameNative 1.2.1** base sync, complete **USB Storage Rework** (1MB buffered auto-mover + manual System Files override), **AGSL Liquid Glass 2.0**, reworked **360° Carousel Mode**, no more wallpaper size limits + static wallpaper support, custom app-launch loading screens (bring your own video instead of the default animation), a real-resolution-aware custom resolution picker, **SGSR upscaling** alongside FSR 1.0, and a new Cloud/Remote Play tab (PS Remote Play, Steam Link, Xbox). | `IT GOT OUT OF HAND, IN A GOOD WAY` |
| **v1.x and beyond** | more customization, more control, more of your phone actually being yours. | `TBD` |

---

## 🛠️ What's Coming in v1.0.0 (GameNothing Rework & GameNative 1.2.1 Sync)

**v1.0.0** launches alongside **GameNative 1.2.1**, bringing the biggest set of internal GameNothing upgrades yet, on top of core GameNative updates:

### ⚡ GameNothing Engine & Storage Rework
- **Full USB Storage Rework:** Dual-tier storage management featuring an optimized 1MB buffered in-app auto-mover for routine transfers, plus a native **"Manage via System Files"** manual override shortcut to bypass Scoped Storage bottlenecks for massive 40GB+ games (*like Halo MCC, Cyberpunk, etc*).
- **Upgraded AGSL Liquid Glass 2.0:** Higher precision shader rendering with cleaner blur scaling with near 0 lag.
- **Enhanced 360° Carousel Mode:** Significantly smoother carousel physics, fixed touch/gesture overrides, and fixed 360° portrait mode container clicks.
- **Stability & Tutorial Updates:** Updated interactive setup tutorial for HSV Colour Picker 2.0, BGM player bug fixes, trailer memory leak fixes, and default launcher exit safeguards.

### 🎨 Personalization & Display
- **No More Wallpaper Limits:** Live wallpapers are no longer capped at any size — add whatever you want. Static wallpapers are now supported too, not just animated ones.
- **Custom Loading Screens:** Keep the default procedural dot animation, or swap in your own video for the app-launch screen.
- **Real Resolution, No Guessing:** New percentage-based resolution picker calculated from your device's actual screen resolution and aspect ratio — no more hardcoded 16:9 presets pretending every phone is the same shape.
- **SGSR Upscaling:** A second upscaling option alongside FSR 1.0, tuned for Snapdragon/Adreno hardware — FSR stays put for everyone else.

### ☁️ Cloud & Connectivity
- **New Cloud/Remote Play Tab:** One-tap access to PS Remote Play, Steam Link, and the official Xbox app — stream from your own console or PC straight from GameNothing.

### 🎮 GameNative 1.2.1 Base Sync
- **Performance & Storage:** Fixed FPS limiter performance regression (games like *Mewgenics*), made fast external loading a toggle to resolve 1.2.0 external launch bugs, and added a "Disable Epic Overlay" container setting to prevent CEF virtual memory crashes on save load.
- **Controls & Gyro:** Added configurable gyro aiming & tilt steering, simultaneous & sequential multi-action bindings, Joy-Con pairing fixes, and resolved P1/P2 controller assignment regressions.
- **Library & Mods:** Steam achievements viewer, re-integrated Nexus Mods support (no manual mod adding required), customizable library tabs, curated 4:3 filter for Steam library (RP Nova), and Epic Games multi-save fix.
- **Compatibility & Fixes:** Updated GOG installer v1 (fixes registry keys and CD drive prompts for older games), fixed DLC download bugs (e.g. Season Pass downloads), added automated AI debugging for game launches, and initial Quest XR support.

---

## ⚠️ Bug Reports & Feedback

**IMPORTANT:** If a bug happens, **DO NOT report it to the original GameNative repository**, as GameNothing is a custom-built fork.

* **In-App Bug Reporting:** Open the menu `☰ (Top Left) -> Settings -> Scroll down -> Debug -> Report Bug` to jump directly to my account to message me.
* **Direct Message & Feature Requests:** Message me on TikTok **[@lsxnothing](https://www.tiktok.com/@lsxnothing)** (or *evil rolfy*) for any questions, suggestions, or bug reports!
* 
