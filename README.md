<div align="center">

# (add image here) GameNothing

### Your phone, but make it a gaming OS.

*Emulators? What are they?*

<br>

<!-- PRIMARY DOWNLOAD BUTTON -->
<a href="https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases/latest">
  <img src="https://img.shields.io/badge/⬇_DOWNLOAD_GAMENOTHING_v2.0-GET_THE_APK_HERE-00e676?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" height="48">
</a>

<br><br>

> **How to install (takes 10 seconds):**  
> **1.** Click the green **Download** button above → **2.** Scroll down to **Assets** → **3.** Tap the **`.apk`** file (not the source code zip).

<br>

<!-- LIVE STATS & SECONDARY LINKS -->
<a href="https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases">
  <img src="https://img.shields.io/github/downloads/gabrielmartinslascau-lsxnya/GameNothing/total?style=for-the-badge&logo=github&label=Total_Downloads&color=2b3137" alt="Total Downloads">
</a>
<a href="https://github.com/user-attachments/files/34876188/GameNothing.2.0.Promo.html">
  <img src="https://img.shields.io/badge/▶_Interactive_Promo-HTML-ff2d87?style=for-the-badge" alt="Interactive Promo">
</a>
<a href="https://www.tiktok.com/@lsxnothing">
  <img src="https://img.shields.io/badge/TikTok-@lsxnothing-000000?style=for-the-badge&logo=tiktok" alt="TikTok">
</a>

<br><br>

<sub>↑ Want to see it in action first? Tap <b>Interactive Promo</b> to download the file, then open it in Chrome. It's about a minute long, with real footage and benchmarks.</sub>

</div>

---

## 👋 What is this?

GameNothing turns your Android phone or handheld into something that feels like a purpose-built gaming console. It's three things in one app:

| | Piece | What it does |
| :-- | :-- | :-- |
| ⚙️ | **GameNative 1.4.0** | The engine. Runs your PC games on Android. |
| 🪟 | **GameNothing UI** | The face. A custom launcher with glass panels, live wallpapers and controller-friendly navigation. |
| 🐧 | **DroidDeck** | The wildcard. A real Debian Linux desktop in the **Playground** tab. |

Log in, import your library, play online or offline. **100% free. Zero data collection. No limits on what you can add.** It's your device, so own it.

> **Status:** **v2.0 ("The Big Merge") is OUT NOW.** All three pieces above are now merged into a single app. See what's inside [below](#-the-big-merge-v20).

---

## ⚡ Does it actually run games?

Fair question. Here's what it does on the developer's own phone (a Snapdragon 7 Gen 4 device):

| Game | What we saw | What the internet predicted |
| :-- | :-- | :-- |
| **No Man's Sky** | Playable, roughly **55–60 FPS** on the on-screen overlay | "0 to 5 FPS, completely unplayable slideshow" |
| **Astroneer** | **250–325 FPS** on the on-screen overlay | N/A (it's a light game, let it have its moment) |

Real footage is in the [interactive promo](https://github.com/user-attachments/files/34876188/GameNothing.2.0.Promo.html) (download and open in Chrome). Your results will depend on your chip, RAM, thermals and the game. Heavy 3D titles on a 6" screen still obey the laws of physics, no matter how good the vibes are.

---

## 🪟 Looks

https://github.com/user-attachments/assets/b6ca0e0c-3fdb-4201-a521-242b1dac9f5b

- **Physics-accurate glass panels:** Choose between classic **Normal Glass** (single distortion slider) or **Broken Glass** (full 5-slider optical engine with refraction, dispersion, thickness and luminance)
- **Live wallpaper support**, no size limit, taking immediate effect across menus and containers
- **"Material You"** wallpaper colour extraction, plus a custom colour picker for any colour you like
- **Full-screen glass container pickers:** Selecting Turnip drivers, Proton versions, or audio backends opens a live-wallpaper glass selector
- **Choose your own animations**
- **Nothing-style fonts** (Ndot and Bricolage Grotesque)
- **Custom download bars** (2 types)

---

## 🎛️ Feel & Control

https://github.com/user-attachments/assets/ac83a23c-e8c3-437c-a449-64f9fbc4e40c

- **Rebuilt Per-Game Resolution Engine:** Hard-lock your aspect ratio to **Native Screen Ratio**, **16:9**, or **4:3** with **100% / 75% / 50% / 25%** quick presets, a **1–100%** slider, or manual **X/Y pixel inputs** with auto-ratio calculation
- **Built-in Practical Tuning Guide:** Straightforward onboarding that explains real-world Proton + DXVK combos, when to use AdrenoTools (Turnip), how much system RAM to dedicate as VRAM (512 MB to 4 GB), and how CPU Core parking vs. FEX actually affects battery life
- **Haptics** (3 modes)
- **Refresh rate control** up to 144 Hz
- **Floating RAM viewer** bubble
- **Dedicated MP3 player** with FLAC support
- **Aggressive caching** so everything loads fast

---

## 📚 Your Library

- **Steam, GOG, Epic and Amazon** integration
- **Custom games** (external or internal storage)
- **Cloud saves** and **Nexus Mods** support, plus local mods
- **USB storage pipeline rework:** automated high-speed mover, plus a manual bypass for system files
- **Cloud / Remote Play tab**
- And a whole lot more

---

## 🧩 The Big Merge (v2.0 — Out Now)

<img width="1568" height="823" alt="v2.0 overview" src="https://github.com/user-attachments/assets/674e286d-bb24-45cc-a3dd-81dc452bd851" />

v2.0 started life as v0.6.0, then got way too big. It merges **GameNative 1.4.0**, the **GameNothing UI** and **DroidDeck** into a single app, so you don't juggle three installs and three launchers.

**v2.0 Release Status:** `[████████████████████]` **100% — LIVE**

### Broken Glass (new visuals)
- **Two independent glass engines:** classic "Normal" magnifier, or the new "Broken Glass"
- **Realistic distortion:** real-world optics (index of refraction 1.52, thickness 58) bend the background at the edges
- **Chromatic aberration:** colour splitting at the edges for extra realism

### Linux Playground (Debian, via DroidDeck)
- A dedicated **Playground** tab that turns your phone into a full Debian desktop
- Run a real desktop browser, play modded Terraria (tModLoader), and use programs normal Android can't

### GameNative engine upgrade (1.4.0)
- **Faster downloads** with the native Rust download engine
- **Saves storage:** container sharing cuts up to 2 GB per game
- **Headless Steam client:** fixes online multiplayer and DRM issues
- **Gamescope & X11 display servers:** swap between them in two taps if an older game flickers
- **EA and Rockstar support**, for games like *The Sims 4* and *Red Dead Redemption 2*
- **Voice chat:** microphone input is supported
- **Native LSFG:** smoother gameplay with the Lossless Scaling frame generation pipeline
- See GameNative's own release notes for the complete list

### Roadmap

| Version | Highlights | Status |
| :-- | :-- | :-- |
| **BETA v0.1.0 – v0.5.0** | Foundations: Nothing fonts, glass panels, the 360° carousel, the first AGSL shader, MP3 player, wallpaper colour extraction | `OUTDATED` |
| **v1.0.0 (Beta)** | GameNative 1.2.1 base sync, USB storage rework, AGSL Glass 2.0, reworked 360° carousel, custom loading screens, resolution picker, Cloud / Remote Play tab, early-access Playground tab | `LEGACY` |
| **v2.0.0 "The Big Merge"** | GameNative 1.4.0 engine, DroidDeck-powered Linux Playground, physics-accurate "Broken Glass", rebuilt resolution & container UI | `LATEST RELEASE` |

---

## 📈 Project Growth & Downloads

<div align="center">

<a href="https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases">
  <img src="https://img.shields.io/github/downloads/gabrielmartinslascau-lsxnya/GameNothing/total?style=for-the-badge&logo=android&label=All-Time%20APK%20Downloads&color=00e676" alt="All-Time Downloads">
</a>
<a href="https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases/latest">
  <img src="https://img.shields.io/github/downloads/gabrielmartinslascau-lsxnya/GameNothing/latest/total?style=for-the-badge&logo=github&label=v2.0%20Downloads&color=ff2d87" alt="Latest Release Downloads">
</a>
<a href="https://github.com/gabrielmartinslascau-lsxnya/GameNothing/stargazers">
  <img src="https://img.shields.io/github/stars/gabrielmartinslascau-lsxnya/GameNothing?style=for-the-badge&logo=github&label=GitHub%20Stars&color=yellow" alt="GitHub Stars">
</a>

<br><br>

<a href="https://star-history.com/#gabrielmartinslascau-lsxnya/GameNothing&Date">
  <img src="https://api.star-history.com/svg?repos=gabrielmartinslascau-lsxnya/GameNothing&type=Date&theme=dark" alt="GameNothing Growth Chart" width="680">
</a>

</div>

---

## 🚀 Getting started

1. Go to the [**Releases**](https://github.com/gabrielmartinslascau-lsxnya/GameNothing/releases/latest) page, scroll down to **Assets**, and tap the **`.apk`** file to download it.
2. Install it (you'll need to allow installs from unknown sources, as with any APK outside the Play Store).
3. Open the app, check out the quick tuning guide, and log in to your stores.
4. Import your library and press play.

**Best experience:** a recent Snapdragon phone or handheld with plenty of RAM. Other devices may work, but that's the happy path.

---

## ❓ Quick FAQ

**Is it really free?** Yes. No ads, no paywalls, no data collection.

**Why is the app big?** It ships an entire PC compatibility engine and a Linux desktop. We're tiny compared to how much is inside.

**Can I play X?** Maybe! Compatibility depends on the game and your hardware. Treat the promo numbers as best cases, not promises.

**Can I add my own games?** Yes. Add them from external or internal storage, no limits.

---

## 🐞 Bug reports & Credits

> **Important:** if you find a bug, **do not report it to the original GameNative repository.** GameNothing is a custom fork.

- **In the app:** `☰ (top left) → Settings → scroll down → Debug → Report Bug`
- **Questions, ideas, feature requests:** message me on TikTok [**@lsxnothing**](https://www.tiktok.com/@lsxnothing) (or *evil rolfy*)

### Credits
- **GameNative:** the entire core foundation. GameNothing is a heavily customised fork of GameNative by **utkarshdalal** and team, who built the emulator, the container system and the 1.3 / 1.4 updates.
- **The Liquid Glass devs:** huge thanks to **Kyant0** (AndroidLiquidGlass), **sdegenaar** (Liquid Glass Widgets for Flutter) and the wider graphics community for working out the physics behind the glass effect.
- **DroidDeck:** created by **The412Banner**, who proved a full Linux desktop can run on an Android phone. It powers the Playground tab in v2.0.
- **Early supporters:** the handful of people on TikTok who tried the early builds and gave feedback. You kept this project alive.

---

<sub>GameNothing is an independent project and is not affiliated with or endorsed by Valve, Epic Games, GOG, Amazon, Microsoft, EA, Rockstar or any other game store or publisher. Please only play games you own. Performance figures are from the developer's own device and will vary.</sub>
