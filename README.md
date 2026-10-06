# GameNothing

[image goes here]

Transform your Android device or handheld console into an OEM-grade gaming OS. **GameNothing** is a full-featured Home Launcher replacement and frontend built for high-performance PC game emulation, seamless USB-OTG game storage management, and complete visual customization.

Log in, import your library, and play your games—online or offline—with zero telemetry bloat.

**100% free. Zero data collection, zero limits on what you can add or how much you can store. It's your device — own it.**

**Note:** v1.0 is currently in **BETA**. Some features like control mapping, static wallpapers, and SGSR upscaling aren't in yet—they'll arrive in the future. The big one? The new Linux environment and driver fixes are coming in **v2.0**. For now, treat this beta as a placeholder for the real magic.

---

### 📥 [Click Here to Download Latest GameNothing Release!](https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases)

---

## 🔥 What GameNothing Brings to Your Device

[image goes here]

- **choose your animations**
- **glass panels & AGSL Liquid Glass shader (with realistic blur)**
- **adjustable distortion slider for liquid glass**
- **colour picker (any colour you wish for)**
- **live wallpaper support, no size limit (v1.0 beta)**
- **static wallpaper support (coming in v2.0+)**
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

> **Note:** this started life as v0.6.0 alongside the official GameNative 1.2.1 sync release — turned out to be way too much to still call it a point release. Say hello to **v2.0.0**.

| Version | Key Features | Status |
| :--- | :--- | :--- |
| **BETA v0.1.0** | Very first official launch with Nothing® style font, glass panels, UI animations, 6-color palette picker, screen refresh rate slider, and floating RAM viewer. | `SUPER OUTDATED` |
| **BETA v0.1.1** | Improved colour picker, new custom "Legion" logo, bug fixes, better adjustable sliders on glass panels, refined refresh rate controls, and upgraded floating RAM viewer. | `OUTDATED` |
| **BETA v0.1.2** | **Major Overhaul:** "Legion" logo redesign, haptics integration, controller support, launch animations, 4 new colors, aggressive caching system (cache view), improved tutorial, faster game launch times, glow effects, color saturation tweaks, scrolling optimizations, and initial emulator rebrand to "GameNothing". | `OUTDATED` |
| **v0.2.0** | **Out of Beta:** Full USB drive support, custom game directories (internal/external/SD), base sync with GameNative 1.2.0, interactive beginner setup tutorial, redesigned download progress bar, instant UI response rates, controller loading optimizations, and custom driver support for non-rooted / non-AYN devices. | `OUTDATED` |
| **v0.5.0** | **The Console OS Milestone:** Full app rebrand to **GameNothing**, native Android **Home Launcher** mode (complete OS replacement), initial 360° interactive carousel navigation with floor reflections, **HSV Colour Picker 2.0** popup, initial **AGSL Liquid Glass** shader, persistent **Background Music Bar / MP3 player**, unmuted trailer audio in Hero view, **Material You wallpaper color extraction**, and initial USB loading logic. | `CURRENT RELEASE` |
| **v1.0.0 (Beta)** | **The "This Was Supposed to Be 0.6" Release:** Full **GameNative 1.2.1** base sync, complete **USB Storage Rework** (1MB buffered auto-mover + manual System Files override), **AGSL Liquid Glass 2.0**, reworked **360° Carousel Mode**, no more live wallpaper size limits, custom app-launch loading screens, a real-resolution-aware custom resolution picker, a new Cloud/Remote Play tab, and an early-access **Playground tab**. *Note: Control mapping, static wallpapers, and SGSR upscaling didn't make the cut. The Linux environment + driver fix are the main event for v2.0, making this beta a placeholder for the real magic.* | `CURRENT BETA` |
| **v2.0.0 (Mega Update)** | **The "Everything, Everywhere, All at Once" Release:** The ultimate merge. Includes **every feature from GameNative 1.2.1 + 1.3 pre-release + 1.3 release** (Headless Steam Client, Native Rust download engine, EA/Rockstar support, Microphone input, Native LSFG, and massive Container Size reduction via Copy-on-Write sharing). PLUS the brand new **Linux Environment (Debian)** for native desktop apps, tModLoader, and full browsing. PLUS the **driver fix** for seamless System/Turnip switching. PLUS the return of **Control Mapping**, **Static Wallpapers**, and **SGSR Upscaling**. | `IN DEVELOPMENT` |
| **v2.x and beyond** | More customization, more control, more of your phone actually being yours. | `TBD` |

---

## 🛠️ What's Coming in v2.0.0 (The Mega Update)

**v2.0.0** is the biggest leap forward for GameNothing. It combines the upstream GameNative 1.3 release with your own custom features and the highly anticipated Linux runtime.

### 🐧 The Linux Environment (Debian)
- **A True Dual-Boot Experience:** A dedicated "Playground" tab that seamlessly switches from Android gaming to a full Debian Linux desktop environment.
- **Inspired by DroidDeck:** Built with a heavily customized PRoot + X11 architecture. Run native Linux games (like tModLoader), browse the web with a real desktop Firefox, and use your device like a full PC.
- **Heavily Credited:** This feature was heavily inspired by the incredible work on **DroidDeck** and is built upon the foundation of **GameNative**. 

### 🚀 GameNative 1.3 Sync (All the Good Stuff)
- **Native Rust Download Engine:** One-at-a-time download queue to significantly speed up downloads.
- **Massive Container Size Reduction:** Sharing components across containers (Copy-on-Write) drops container sizes by up to **2GB per game**. Perfect for your external pen drive setup!
- **Headless Steam Client:** Fixes DRM issues and gets online multiplayer working in more games.
- **EA & Rockstar Support:** New launcher stubs and install-script fixes for *NFS: Most Wanted* and Rockstar titles.
- **Native LSFG Pipeline:** Now the default frame generation method, fixing LSFG on most devices.
- **Microphone Input Support:** In-game voice chat is finally possible.

### 🎨 Personalization & Display (The Missing Pieces)
- **Control Mapping:** Full custom control mapping for touch and physical controllers.
- **Static Wallpapers:** The static wallpaper support that missed the 1.0 Beta cut.
- **SGSR Upscaling:** The Snapdragon Game Super Resolution upscaling for crisper visuals on Adreno devices.

### 🛠️ The Driver Fix
- **Seamless Driver Switching:** No more black screens. The app now completely tears down the XServer/Wine environment when you switch between the System Driver and custom Turnip drivers, ensuring DX12 games like *Grounded 2* actually boot.

---

## ⚠️ Bug Reports & Feedback

**IMPORTANT:** If a bug happens, **DO NOT report it to the original GameNative repository**, as GameNothing is a custom-built fork.

* **In-App Bug Reporting:** Open the menu `☰ (Top Left) -> Settings -> Scroll down -> Debug -> Report Bug` to jump directly to my account to message me.
* **Direct Message & Feature Requests:** Message me on TikTok **[@lsxnothing](https://www.tiktok.com/@lsxnothing)** (or *evil rolfy*) for any questions, suggestions, or bug reports!
